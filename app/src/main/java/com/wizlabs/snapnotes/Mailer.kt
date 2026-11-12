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

    fun body(note: Note): String = buildString {
        append(note.header).append('\n')
        append(Stamp.timeLabel(note.createdMs)).append("\n\n")
        if (note.body.isNotBlank()) append(note.body.trim()).append("\n\n")
        append("Sent with Wiz Snap Notes")
    }

    fun mail(context: Context, note: Note) {
        val uri = photoUri(context, note)
        val base = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_EMAIL, note.emails.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, note.header)
            putExtra(Intent.EXTRA_TEXT, body(note))
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(note.header, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
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

    private fun gmailCompose(context: Context, base: Intent): Intent? {
        val probe = Intent(base).setPackage(GMAIL)
        val targets = context.packageManager.queryIntentActivities(probe, 0)
        if (targets.isEmpty()) return null
        val pick = targets.firstOrNull { it.activityInfo.name.contains("Compose", ignoreCase = true) }
            ?: targets.first()
        return probe.setClassName(pick.activityInfo.packageName, pick.activityInfo.name)
    }

    /** WhatsApp, Drive and the rest: a normal share sheet. */
    fun share(context: Context, note: Note) {
        val uri = photoUri(context, note)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_SUBJECT, note.header)
            putExtra(Intent.EXTRA_TEXT, body(note))
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(note.header, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(send, "Share photo note")) }
    }
}
