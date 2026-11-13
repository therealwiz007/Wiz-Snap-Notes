package com.wizlabs.snapnotes

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { NoteStore.get(context) }
    val scope = rememberCoroutineScope()
    var pending by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var connecting by remember { mutableStateOf(false) }
    var confirmOff by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(store.version, DriveBackup.status) {
        val (p, t) = withContext(Dispatchers.IO) { store.pendingCount() to store.count() }
        pending = p; total = t
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        connecting = false
        DriveBackup.finishConnect(context, res.data)
    }
    fun connect() {
        error = null
        connecting = true
        scope.launch {
            try {
                val r = DriveBackup.startConnect(context)
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) {
                    consent.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                } else {
                    connecting = false
                    DriveBackup.onAuthorized(context, r)
                }
            } catch (e: com.google.android.gms.common.api.ApiException) {
                connecting = false
                error = if (e.statusCode == com.google.android.gms.common.api.CommonStatusCodes.DEVELOPER_ERROR)
                    "Google sign-in is not set up for this app build yet." else "Could not reach Google (${e.statusCode}). Check the internet and try again."
            } catch (e: Exception) {
                connecting = false
                error = "Could not reach Google. Check the internet and try again."
            }
        }
    }

    val email = DriveBackup.email
    val status = DriveBackup.status

    Column(Modifier.fillMaxSize().background(WizNavy)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(12.dp))
            Text("Google Drive backup", color = WizText, style = MaterialTheme.typography.titleMedium)
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()
        ) {
            StatusCard(email, status, pending, total)

            if (email == null) {
                SectionTitle("How it works", Icons.Filled.Lock)
                SettingsCard {
                    InfoRow(Icons.Filled.CloudUpload, "Every stamped photo goes to a \"Wiz Snap Notes\" folder in your own Google Drive.")
                    CardDivider()
                    InfoRow(Icons.Filled.Lock, "The app can only see the files it puts there. The rest of your Drive stays private.")
                    CardDivider()
                    InfoRow(Icons.Filled.CloudSync, "Header, notes and mail IDs go with each photo, so Drive search finds them too.")
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { connect() },
                    enabled = !connecting,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WizAccent, contentColor = WizNavy),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    Icon(Icons.Filled.Cloud, null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (connecting) "Opening Google…" else "Sign in with Google", fontWeight = FontWeight.Bold)
                }
            } else {
                SectionTitle("Backup", Icons.Filled.CloudSync)
                SettingsCard {
                    SwitchRow(
                        "Back up new photos", DriveBackup.auto, { DriveBackup.setAuto(context, it) },
                        hint = "Uploads right after each save", icon = Icons.Filled.CloudUpload
                    )
                    CardDivider()
                    SwitchRow(
                        "Wi-Fi only", DriveBackup.wifiOnly, { DriveBackup.setWifiOnly(context, it) },
                        hint = "Waits for Wi-Fi to save mobile data", icon = Icons.Filled.Wifi
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (status is DriveBackup.Status.NeedsSignIn) connect() else DriveBackup.sync(context, force = true)
                        },
                        enabled = status !is DriveBackup.Status.Uploading && (pending > 0 || status is DriveBackup.Status.NeedsSignIn),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WizAccent, contentColor = WizNavy,
                            disabledContainerColor = WizPanelHi, disabledContentColor = WizMuted
                        ),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text(if (status is DriveBackup.Status.NeedsSignIn) "Sign in again" else "Back up now", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            val url = DriveBackup.folderUrl(context) ?: "https://drive.google.com/drive/my-drive"
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, WizAccent.copy(alpha = .6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WizAccent),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open in Drive", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { confirmOff = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Disconnect Google account", color = WizBad)
                }
            }

            (error ?: (status as? DriveBackup.Status.Failed)?.message?.takeIf { email == null })?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = WizBad, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            containerColor = WizPanel,
            title = { Text("Disconnect Google Drive?", color = WizText) },
            text = { Text("New photos stop backing up. Photos already in your Drive stay there.", color = WizMuted) },
            confirmButton = {
                TextButton(onClick = { confirmOff = false; scope.launch { DriveBackup.disconnect(context) } }) {
                    Text("Disconnect", color = WizBad)
                }
            },
            dismissButton = { TextButton(onClick = { confirmOff = false }) { Text("Cancel", color = WizAccent) } }
        )
    }
}

@Composable
private fun StatusCard(email: String?, status: DriveBackup.Status, pending: Int, total: Int) {
    val (icon, title, line) = when {
        email == null -> Triple(Icons.Filled.CloudOff, "Not backed up", "Photos are only on this phone")
        status is DriveBackup.Status.Uploading -> Triple(Icons.Filled.CloudSync, "Backing up", "${status.done} of ${status.total} photos")
        status is DriveBackup.Status.NeedsSignIn -> Triple(Icons.Filled.CloudOff, "Sign in needed", "Google wants you to approve again")
        status is DriveBackup.Status.Failed -> Triple(Icons.Filled.CloudOff, "Backup paused", status.message)
        pending > 0 && status is DriveBackup.Status.Waiting ->
            Triple(Icons.Filled.CloudSync, "$pending waiting", if (DriveBackup.wifiOnly) "Waiting for Wi-Fi" else "Waiting for internet")
        pending > 0 -> Triple(Icons.Filled.CloudSync, "$pending waiting", "Tap Back up now")
        else -> Triple(Icons.Filled.CloudDone, "All backed up", if (total == 1) "1 photo in Drive" else "$total photos in Drive")
    }
    val tint = when {
        email == null -> WizMuted
        status is DriveBackup.Status.Failed || status is DriveBackup.Status.NeedsSignIn -> WizBad
        pending == 0 -> WizGood
        else -> WizAccent
    }
    val progress = (status as? DriveBackup.Status.Uploading)?.let { it.done / it.total.coerceAtLeast(1).toFloat() }
    val shown by animateFloatAsState(progress ?: 0f, tween(400, easing = WizMotion.Emphasized), label = "progress")

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .enterRise(0)
            .clip(RoundedCornerShape(24.dp))
            .background(WizPanel)
            .background(Brush.radialGradient(listOf(tint.copy(alpha = .16f), Color.Transparent), radius = 520f))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = .16f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = WizText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(line, color = WizMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (email != null) {
            Spacer(Modifier.height(14.dp))
            Text(email, color = WizAccent, style = MaterialTheme.typography.labelLarge)
        }
        if (progress != null) {
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { shown },
                color = WizAccent, trackColor = WizPanelHi,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon)
        Spacer(Modifier.width(14.dp))
        Text(text, color = WizText, style = MaterialTheme.typography.bodyMedium)
    }
}
