package com.wizlabs.snapnotes

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Optional backup of stamped photos to the user's own Google Drive, in a "Wiz Snap Notes" folder.
 *
 * Uses the `drive.file` scope only: the app can see and change just the files it creates, nothing
 * else in the user's Drive. Uploads go straight from the phone to Google; Wiz Labs has no server.
 * The header, notes and recipients go into each file's Drive description so Drive search finds them.
 */
object DriveBackup {
    private const val DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    private val scopes = listOf(Scope(DRIVE_FILE), Scope("email"))
    private const val FOLDER_NAME = "Wiz Snap Notes"
    private const val PREFS = "drive_backup"

    sealed interface Status {
        data object Idle : Status
        data class Uploading(val done: Int, val total: Int) : Status
        data object Waiting : Status          // offline, or Wi-Fi only and on mobile data
        data object NeedsSignIn : Status      // Google wants the user to approve again
        data class Failed(val message: String) : Status
    }

    /** Signed-in Google account, or null when Drive backup is off. */
    var email by mutableStateOf<String?>(null); private set
    var auto by mutableStateOf(true); private set
    var wifiOnly by mutableStateOf(false); private set
    var status by mutableStateOf<Status>(Status.Idle); private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var loaded = false
    private var watching = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        email = p.getString("email", null)
        auto = p.getBoolean("auto", true)
        wifiOnly = p.getBoolean("wifi_only", false)
        if (email != null) watchNetwork(context.applicationContext)
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- Sign-in ---------------------------------------------------------------------------

    private fun request(account: String?): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(scopes)
        .apply { if (account != null) setAccount(Account(account, "com.google")) }
        .build()

    /** First step of Connect. If the result has a resolution, the screen must launch it. */
    suspend fun startConnect(context: Context): AuthorizationResult =
        Identity.getAuthorizationClient(context).authorize(request(null)).await()

    /** The consent screen came back. */
    fun finishConnect(context: Context, data: Intent?): Boolean = try {
        val r = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        onAuthorized(context, r)
        true
    } catch (e: ApiException) {
        if (e.statusCode != CommonStatusCodes.CANCELED) status = Status.Failed(explain(e))
        false
    }

    fun onAuthorized(context: Context, r: AuthorizationResult) {
        val account = r.toGoogleSignInAccount()?.email ?: email ?: "Google account"
        if (email != null && !email.equals(account, ignoreCase = true)) {
            // Different account: its Drive has none of the photos yet.
            NoteStore.get(context).clearDriveIds()
            prefs(context).edit().remove("folder").apply()
        }
        email = account
        prefs(context).edit().putString("email", account).apply()
        status = Status.Idle
        watchNetwork(context.applicationContext)
        sync(context, force = true)
    }

    fun setAuto(context: Context, on: Boolean) {
        auto = on
        prefs(context).edit().putBoolean("auto", on).apply()
        if (on) sync(context)
    }

    fun setWifiOnly(context: Context, on: Boolean) {
        wifiOnly = on
        prefs(context).edit().putBoolean("wifi_only", on).apply()
        sync(context)
    }

    /** Turns backup off on this phone. Photos already in Drive stay there. */
    suspend fun disconnect(context: Context) {
        val account = email ?: return
        runCatching {
            Identity.getAuthorizationClient(context).revokeAccess(
                RevokeAccessRequest.builder().setAccount(Account(account, "com.google")).setScopes(scopes).build()
            ).await()
        }
        prefs(context).edit().remove("email").remove("folder").apply()
        NoteStore.get(context).clearDriveIds()
        email = null
        status = Status.Idle
    }

    fun folderUrl(context: Context): String? =
        prefs(context).getString("folder", null)?.let { "https://drive.google.com/drive/folders/$it" }

    // ---- Upload queue ----------------------------------------------------------------------

    /** Uploads every note not yet in Drive. Safe to call often; only one run happens at a time. */
    fun sync(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        if (email == null || (!auto && !force)) return
        scope.launch { lock.withLock { runQueue(app) } }
    }

    private suspend fun runQueue(context: Context) {
        val store = NoteStore.get(context)
        val total = store.pendingCount()
        if (total == 0) { if (status !is Status.NeedsSignIn) status = Status.Idle; return }
        if (!networkOk(context)) { status = Status.Waiting; return }

        var token = token(context) ?: return
        var done = 0
        status = Status.Uploading(0, total)
        try {
            var folder = ensureFolder(context, token)
            while (true) {
                val batch = store.pendingBackup(20)
                if (batch.isEmpty()) break
                for (n in batch) {
                    if (!networkOk(context)) { status = Status.Waiting; return }
                    val file = File(n.file)
                    if (!file.exists()) { store.setDriveId(n.id, "-"); continue }
                    val id = try {
                        upload(token, folder, n, file)
                    } catch (e: HttpError) {
                        when (e.code) {
                            401 -> { // token expired: clear it and get a fresh one once
                                clearToken(context, token)
                                token = token(context) ?: return
                                upload(token, folder, n, file)
                            }
                            404 -> { // folder was deleted in Drive: make a new one
                                prefs(context).edit().remove("folder").apply()
                                folder = ensureFolder(context, token)
                                upload(token, folder, n, file)
                            }
                            else -> throw e
                        }
                    }
                    store.setDriveId(n.id, id)
                    done++
                    status = Status.Uploading(done, maxOf(total, done))
                }
            }
            status = Status.Idle
        } catch (e: IOException) {
            status = if (networkOk(context)) Status.Failed(e.message ?: "Upload failed. Will retry.") else Status.Waiting
        } catch (e: HttpError) {
            status = Status.Failed(e.friendly())
        }
    }

    private suspend fun token(context: Context): String? = try {
        val r = Identity.getAuthorizationClient(context).authorize(request(email)).await()
        if (r.hasResolution()) { status = Status.NeedsSignIn; null } else r.accessToken
    } catch (e: ApiException) {
        status = Status.Failed(explain(e)); null
    } catch (e: Exception) {
        status = Status.Failed(e.message ?: "Google sign-in failed"); null
    }

    private suspend fun clearToken(context: Context, token: String) {
        runCatching {
            Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
        }
    }

    private fun ensureFolder(context: Context, token: String): String {
        val p = prefs(context)
        p.getString("folder", null)?.let { id ->
            val (code, body) = http("GET", "https://www.googleapis.com/drive/v3/files/$id?fields=id,trashed", token)
            if (code == 200 && !JSONObject(body).optBoolean("trashed")) return id
            if (code != 404 && code != 200) throw HttpError(code, body)
            // Folder gone or in the bin: its photos went with it, so send them all again.
            NoteStore.get(context).clearDriveIds()
        }
        // Reuse a folder this app made before (e.g. after reinstall), else create one.
        val q = java.net.URLEncoder.encode(
            "name = '$FOLDER_NAME' and mimeType = 'application/vnd.google-apps.folder' and trashed = false", "UTF-8"
        )
        val (lc, lb) = http("GET", "https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id)&spaces=drive", token)
        if (lc != 200) throw HttpError(lc, lb)
        val found = JSONObject(lb).optJSONArray("files")?.optJSONObject(0)?.optString("id")
        val id = found?.takeIf { it.isNotEmpty() } ?: run {
            val meta = JSONObject().put("name", FOLDER_NAME).put("mimeType", "application/vnd.google-apps.folder")
            val (cc, cb) = http("POST", "https://www.googleapis.com/drive/v3/files?fields=id", token, meta.toString())
            if (cc != 200) throw HttpError(cc, cb)
            JSONObject(cb).getString("id")
        }
        p.edit().putString("folder", id).apply()
        return id
    }

    private fun upload(token: String, folder: String, n: Note, file: File): String {
        val meta = JSONObject()
            .put("name", file.name)
            .put("mimeType", "image/jpeg")
            .put("parents", JSONArray().put(folder))
            .put("description", describe(n))
            .put("appProperties", JSONObject().put("snapNoteId", n.id.toString()))
        val boundary = "wizsnap" + System.nanoTime()
        val head = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
            "--$boundary\r\nContent-Type: image/jpeg\r\n\r\n").toByteArray()
        val tail = "\r\n--$boundary--\r\n".toByteArray()

        val c = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 20_000
            c.readTimeout = 60_000
            c.doOutput = true
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            // Stream the photo instead of loading it into memory.
            c.setFixedLengthStreamingMode(head.size + file.length() + tail.size)
            c.outputStream.use { out ->
                out.write(head)
                file.inputStream().use { it.copyTo(out, 64 * 1024) }
                out.write(tail)
            }
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw HttpError(code, body)
            return JSONObject(body).getString("id")
        } finally {
            c.disconnect()
        }
    }

    private fun describe(n: Note) = buildString {
        append(n.header).append('\n').append(Stamp.timeLabel(n.createdMs))
        if (n.body.isNotBlank()) append("\n\n").append(n.body.trim())
        if (n.emails.isNotEmpty()) append("\n\nMail to: ").append(n.emails.joinToString(", "))
    }

    private fun http(method: String, url: String, token: String, json: String? = null): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 20_000
            c.readTimeout = 30_000
            c.setRequestProperty("Authorization", "Bearer $token")
            if (json != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                c.outputStream.use { it.write(json.toByteArray()) }
            }
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code == 401) throw HttpError(401, body)
            return code to body
        } finally {
            c.disconnect()
        }
    }

    private class HttpError(val code: Int, val body: String) : Exception("HTTP $code") {
        fun friendly(): String = when (code) {
            403 -> if (body.contains("storageQuota", true)) "Google Drive is full" else "Google Drive refused the upload"
            in 500..599 -> "Google Drive is busy. Will retry."
            else -> "Upload failed (HTTP $code)"
        }
    }

    private fun explain(e: ApiException): String = when (e.statusCode) {
        CommonStatusCodes.DEVELOPER_ERROR -> "Google sign-in is not set up for this app build"
        CommonStatusCodes.NETWORK_ERROR -> "No internet connection"
        else -> "Google sign-in failed (${e.statusCode})"
    }

    // ---- Network ---------------------------------------------------------------------------

    private fun networkOk(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return !wifiOnly || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** Picks up the backlog when the phone comes back online, without any background service. */
    private fun watchNetwork(app: Context) {
        if (watching) return
        watching = true
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (email != null && status is Status.Waiting) sync(app)
            }
        })
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
