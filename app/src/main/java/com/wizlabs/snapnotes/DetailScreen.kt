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

@Composable
fun DetailScreen(id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { NoteStore.get(context) }
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<Note?>(null) }
    var missing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        note = withContext(Dispatchers.IO) { store.get(id) }
        missing = note == null
    }
    val n = note

    Column(Modifier.fillMaxSize().background(WizNavy)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(12.dp))
            Text(
                n?.let { Stamp.timeLabel(it.createdMs) } ?: "", color = WizMuted,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)
            )
            if (n != null) GlassIconButton(Icons.Filled.DeleteOutline, "Delete", { confirmDelete = true })
        }
        if (n == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (missing) Text("This note was deleted.", color = WizMuted) else CircularProgressIndicator(color = WizAccent)
            }
            return@Column
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            ZoomImage(n)
            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth().enterRise(0).clip(RoundedCornerShape(20.dp)).background(WizPanel).padding(16.dp)) {
                FieldLabel("HEADER")
                Text(n.header, color = WizText, style = MaterialTheme.typography.titleLarge)
                if (n.body.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    FieldLabel("NOTES")
                    Text(n.body, color = WizText, style = MaterialTheme.typography.bodyLarge)
                }
                if (n.emails.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    FieldLabel("MAIL TO")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        n.emails.forEach {
                            Surface(shape = RoundedCornerShape(50), color = WizAccent.copy(alpha = .14f), border = BorderStroke(1.dp, WizAccent.copy(alpha = .4f))) {
                                Text(it, color = WizText, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        Row(
            Modifier.fillMaxWidth().background(WizBar).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { Mailer.share(context, n) },
                border = BorderStroke(1.dp, WizAccent.copy(alpha = .6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WizAccent),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(54.dp)
            ) {
                Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share", style = MaterialTheme.typography.titleSmall)
            }
            Button(
                onClick = { Mailer.mail(context, n) },
                colors = ButtonDefaults.buttonColors(containerColor = WizAccent, contentColor = WizNavy),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1.4f).height(54.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Send Mail", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (confirmDelete && n != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = WizPanel,
            title = { Text("Delete this photo note?", color = WizText) },
            text = { Text("It is removed from Snap Notes and from Pictures/Wiz Snap Notes. This cannot be undone.", color = WizMuted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        Thumbs.evict(n.thumb)
                        PhotoPipeline.delete(context, n)
                        onBack()
                    }
                }) { Text("Delete", color = WizBad) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = WizAccent) } }
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
