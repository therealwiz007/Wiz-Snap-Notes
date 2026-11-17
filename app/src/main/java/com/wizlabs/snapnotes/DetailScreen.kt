@file:OptIn(ExperimentalLayoutApi::class)

package com.wizlabs.snapnotes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.IconButton

/** One visit: the header once, then every snap under it in the order they were taken. */
@Composable
fun DetailScreen(visitId: Long, onBack: () -> Unit, onAddSnap: (VisitDraft) -> Unit) {
    val context = LocalContext.current
    val store = remember { NoteStore.get(context) }
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf<List<Note>?>(null) }
    var confirmDelete by remember { mutableStateOf<List<Note>?>(null) }
    LaunchedEffect(visitId, store.version) {
        notes = withContext(Dispatchers.IO) { store.visit(visitId) }
    }
    val list = notes
    val first = list?.firstOrNull()

    Column(Modifier.fillMaxSize().background(WizNavy)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(12.dp))
            Text(
                first?.let {
                    val count = if (list.size > 1) " · ${list.size} snaps" else ""
                    Stamp.timeLabel(it.createdMs) + count
                } ?: "",
                color = WizMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1
            )
            if (list != null && list.isNotEmpty() && DriveBackup.email != null) {
                val saved = list.all { it.driveId != null && it.driveId != "-" }
                Icon(
                    if (saved) Icons.Filled.CloudDone else Icons.Filled.CloudQueue,
                    if (saved) "Backed up to Google Drive" else "Waiting to back up",
                    tint = if (saved) WizGood else WizMuted,
                    modifier = Modifier.padding(end = 10.dp).size(22.dp)
                )
            }
            if (!list.isNullOrEmpty()) GlassIconButton(Icons.Filled.DeleteOutline, "Delete", { confirmDelete = list })
        }
        if (list == null || first == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (list != null) Text("This note was deleted.", color = WizMuted) else CircularProgressIndicator(color = WizAccent)
            }
            return@Column
        }
        val multi = list.size > 1
        val spot = list.firstOrNull { it.hasLocation }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Column(Modifier.fillMaxWidth().enterRise(0).clip(RoundedCornerShape(20.dp)).background(WizPanel).padding(16.dp)) {
                FieldLabel("HEADER")
                Text(first.header, color = WizText, style = MaterialTheme.typography.titleLarge)
                if (spot != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, null, tint = WizAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            Stamp.locationLabel(Spot(spot.lat!!, spot.lng!!, spot.place)),
                            color = WizText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { Mailer.openMap(context, spot.lat, spot.lng) }) {
                            Icon(Icons.Filled.Map, "Open in maps", tint = WizAccent)
                        }
                    }
                }
                if (first.emails.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    FieldLabel("MAIL TO")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        first.emails.forEach {
                            Surface(shape = RoundedCornerShape(50), color = WizAccent.copy(alpha = .14f), border = BorderStroke(1.dp, WizAccent.copy(alpha = .4f))) {
                                Text(it, color = WizText, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                            }
                        }
                    }
                }
            }

            list.forEachIndexed { i, n ->
                Spacer(Modifier.height(18.dp))
                if (multi) {
                    Row(Modifier.fillMaxWidth().padding(start = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("SNAP ${i + 1}/${list.size}", color = WizAccent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("  ·  ${Timeline.time(n.createdMs)}", color = WizMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { confirmDelete = listOf(n) }) {
                            Icon(Icons.Filled.DeleteOutline, "Delete snap ${i + 1}", tint = WizMuted, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                ZoomImage(n)
                if (n.body.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(WizPanel).padding(14.dp)) {
                        FieldLabel("NOTES")
                        Text(n.body, color = WizText, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        Row(
            Modifier.fillMaxWidth().background(WizBar).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { Mailer.share(context, list) },
                border = BorderStroke(1.dp, WizAccent.copy(alpha = .6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WizAccent),
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                modifier = Modifier.weight(1f).height(54.dp)
            ) {
                Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share", style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
            OutlinedButton(
                onClick = {
                    onAddSnap(VisitDraft(visitId, first.header, first.emails, list.any { it.hasLocation }, list.size + 1))
                },
                border = BorderStroke(1.dp, WizAccent.copy(alpha = .6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WizAccent),
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                modifier = Modifier.weight(1f).height(54.dp)
            ) {
                Icon(Icons.Filled.AddAPhoto, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("+ Snap", style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
            Button(
                onClick = { Mailer.mail(context, list) },
                colors = ButtonDefaults.buttonColors(containerColor = WizAccent, contentColor = WizNavy),
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                modifier = Modifier.weight(1.3f).height(54.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (multi) "Mail all" else "Send Mail", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }

    val doomed = confirmDelete
    if (doomed != null && list != null) {
        val all = doomed.size == list.size
        val onDrive = doomed.any { it.driveId != null && it.driveId != "-" }
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = WizPanel,
            title = {
                Text(
                    when {
                        doomed.size == 1 && !all -> "Delete this snap?"
                        doomed.size == 1 -> "Delete this photo note?"
                        else -> "Delete all ${doomed.size} snaps?"
                    },
                    color = WizText
                )
            },
            text = {
                Text(
                    "Removed from Snap Notes and from Pictures/Wiz Snap Notes. This cannot be undone." +
                        if (onDrive) " The copy in your Google Drive stays." else "",
                    color = WizMuted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        doomed.forEach {
                            Thumbs.evict(it.thumb)
                            PhotoPipeline.delete(context, it)
                        }
                        if (all) onBack()
                    }
                }) { Text("Delete", color = WizBad) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = WizAccent) } }
        )
    }
}

/** Pinch to zoom, drag to pan, double-tap to reset. Decoded at screen size, not full size. */
@Composable
private fun ZoomImage(n: Note) {
    val img by rememberImage(n.file, maxSide = 1600)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(img?.let { it.width.toFloat() / it.height } ?: 0.75f)
            .clip(RoundedCornerShape(20.dp))
            .background(WizPanel)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero })
            }
            .pointerInput(Unit) {
                // Two fingers zoom; one finger pans only while zoomed, so the page still scrolls.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + event.calculatePan()
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        img?.let {
            Image(
                it, contentDescription = n.header, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                }
            )
        } ?: CircularProgressIndicator(color = WizAccent, modifier = Modifier.align(Alignment.Center))
    }
}
