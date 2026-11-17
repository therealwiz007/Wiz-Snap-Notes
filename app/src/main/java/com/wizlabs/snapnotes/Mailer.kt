package com.wizlabs.snapnotes

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands the photo note to the user's mail app with everything filled in (to, subject, body,
 * photo). The user taps Send there, so the app needs no account, password or INTERNET permission.
 */
object Mailer {
    private const val GMAIL = "com.google.android.gm"

    fun photoUri(context: Context, note: Note): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".files", File(note.file))

    /** One email body for a visit: header, time, place + map link, then every snap's notes. */
    fun body(notes: List<Note>): String = buildString {
        val first = notes.first()
        append(first.header).append('\n')
        append(Stamp.timeLabel(first.createdMs))
        if (notes.size > 1) append(" · ").append(notes.size).append(" photos")
        append('\n')
        notes.firstOrNull { it.hasLocation }?.let { n ->
            append(Stamp.locationLabel(Spot(n.lat!!, n.lng!!, n.place))).append('\n')
            append(Spot.mapsLink(n.lat, n.lng)).append('\n')
        }
        append('\n')
        if (notes.size == 1) {
            if (first.body.isNotBlank()) append(first.body.trim()).append("\n\n")
        } else {
            notes.forEachIndexed { i, n ->
                append("Photo ").append(i + 1).append(" · ").append(Timeline.time(n.createdMs)).append('\n')
                if (n.body.isNotBlank()) append(n.body.trim()).append('\n')
                append('\n')
            }
        }
        append("Sent with Wiz Snap Notes")
    }

    private fun sendIntent(context: Context, notes: List<Note>): Intent {
        val uris = ArrayList(notes.map { photoUri(context, it) })
        val first = notes.first()
        return Intent(if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_SUBJECT, first.header)
            putExtra(Intent.EXTRA_TEXT, body(notes))
            if (uris.size > 1) putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            else putExtra(Intent.EXTRA_STREAM, uris.first())
            clipData = ClipData.newRawUri(first.header, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Every snap of the visit in one email. */
    fun mail(context: Context, notes: List<Note>) {
        if (notes.isEmpty()) return
        val base = sendIntent(context, notes).apply { putExtra(Intent.EXTRA_EMAIL, notes.first().emails.toTypedArray()) }
        // Straight into Gmail's compose screen when it is installed; otherwise let the user pick a
        // mail app. Gmail also offers a "Chat" share target, so the package alone would show a
        // picker; naming the compose activity skips it.
        val gmail = gmailCompose(context, base)
        try {
            if (gmail != null) {
                context.startActivity(gmail)
            } else {
                context.startActivity(Intent.createChooser(base, "Send mail with").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        } catch (e: Exception) {
            Toast.makeText(context, "No mail app found", Toast.LENGTH_SHORT).show()
        }
    }

    fun openMap(context: Context, lat: Double, lng: Double) {
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse(String.format(java.util.Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f", lat, lng, lat, lng)))
        runCatching { context.startActivity(geo) }.onFailure {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Spot.mapsLink(lat, lng)))) }
        }
    }

    private fun gmailCompose(context: Context, base: Intent): Intent? {
        val probe = Intent(base).setPackage(GMAIL)
        val targets = context.packageManager.queryIntentActivities(probe, 0)
        if (targets.isEmpty()) return null
        val pick = targets.firstOrNull { it.activityInfo.name.contains("Compose", ignoreCase = true) }
            ?: targets.first()
        return probe.setClassName(pick.activityInfo.packageName, pick.activityInfo.name)
    }

    /** WhatsApp, Drive and the rest: a normal share sheet. */
    fun share(context: Context, notes: List<Note>) {
        if (notes.isEmpty()) return
        runCatching { context.startActivity(Intent.createChooser(sendIntent(context, notes), "Share photo note")) }
    }
}
