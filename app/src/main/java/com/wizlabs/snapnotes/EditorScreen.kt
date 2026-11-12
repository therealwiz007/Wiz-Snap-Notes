@file:OptIn(ExperimentalLayoutApi::class)

package com.wizlabs.snapnotes

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun EditorScreen(raw: File, onCancel: () -> Unit, onSaved: (Note, Boolean) -> Unit) {
    val context = LocalContext.current
    val store = remember { NoteStore.get(context) }
    val scope = rememberCoroutineScope()
    val timeMs = remember { System.currentTimeMillis() }

    var header by remember { mutableStateOf("") }
    var body by remember { mutableStateOf(TextFieldValue("")) }
    val emails = remember { mutableStateListOf<String>() }
    var emailText by remember { mutableStateOf("") }
    var headerError by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var recentHeaders by remember { mutableStateOf(emptyList<String>()) }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(Unit) { recentHeaders = withContext(Dispatchers.IO) { store.recentHeaders() } }
    LaunchedEffect(emailText, emails.size) {
        val prefix = emailText.trim()
        suggestions = withContext(Dispatchers.IO) { store.recipients(prefix) }.filter { it !in emails }.take(4)
    }

    // ---- Live stamped preview: a small copy of the photo, re-stamped as you type -------------
    var base by remember { mutableStateOf<Bitmap?>(null) }
    val buffers = remember { arrayOfNulls<Bitmap>(2) }
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    var flip by remember { mutableStateOf(0) }
    LaunchedEffect(raw) {
        base = withContext(Dispatchers.IO) { runCatching { PhotoPipeline.loadUpright(raw, PhotoPipeline.PREVIEW_SIDE) }.getOrNull() }
    }
    LaunchedEffect(base, header, body.text) {
        val b = base ?: return@LaunchedEffect
        delay(90) // debounce while typing
        val next = flip xor 1
        val out = withContext(Dispatchers.Default) {
            val buf = buffers[next]?.takeIf { it.width == b.width && it.height == b.height }
                ?: Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888).also { buffers[next] = it }
            val c = Canvas(buf)
            c.drawBitmap(b, 0f, 0f, null)
            Stamp.draw(c, b.width, b.height, header.ifBlank { "Add a header" }, body.text, timeMs)
            buf
        }
        flip = next
        shown = out.asImageBitmap()
    }
    DisposableEffect(Unit) {
        onDispose {
            base?.recycle()
            buffers.forEach { it?.recycle() }
        }
    }

    // One "print" sweep when the preview first appears.
    val sweep = remember { Animatable(0f) }
    val systemMotion = rememberSystemMotion()
    LaunchedEffect(shown != null) {
        if (shown != null && sweep.value == 0f) {
            if (systemMotion) sweep.animateTo(1f, tween(720, easing = WizMotion.Emphasized)) else sweep.snapTo(1f)
        }
    }

    fun commitEmails(text: String): Boolean {
        val parts = SearchText.splitEmails(text)
        val bad = parts.filterNot { SearchText.isEmail(it) }
        parts.filter { SearchText.isEmail(it) }.map { it.lowercase() }.forEach { if (it !in emails) emails += it }
        emailError = if (bad.isNotEmpty()) "Not an email: ${bad.first()}" else null
        emailText = bad.joinToString(" ")
        return bad.isEmpty()
    }

    fun save(mail: Boolean) {
        if (saving) return
        if (header.isBlank()) { headerError = true; return }
        if (emailText.isNotBlank() && !commitEmails(emailText)) return
        saving = true
        scope.launch {
            try {
                val note = PhotoPipeline.save(context, raw, header, body.text, emails.toList(), timeMs)
                saved = true
                delay(650)
                onSaved(note, mail)
            } catch (e: Exception) {
                saving = false
                emailError = "Could not save: ${e.localizedMessage ?: "storage error"}"
            }
        }
    }

    BackHandler(enabled = !saving) { confirmDiscard = true }

    Box(Modifier.fillMaxSize().background(WizNavy)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(Icons.Filled.Close, "Discard", { confirmDiscard = true })
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Write the note", color = WizText, style = MaterialTheme.typography.titleMedium)
                    Text(Stamp.timeLabel(timeMs), color = WizMuted, style = MaterialTheme.typography.bodySmall)
                }
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
            ) {
                // Preview card
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .aspectRatio(base?.let { it.width.toFloat() / it.height } ?: 0.75f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(WizPanel)
                ) {
                    shown?.let {
                        Image(
                            it, contentDescription = "Stamped photo preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                alpha = (sweep.value * 1.6f).coerceAtMost(1f)
                                val s = 1.03f - 0.03f * sweep.value
                                scaleX = s; scaleY = s
                            }
                        )
                    } ?: CircularProgressIndicator(color = WizAccent, modifier = Modifier.align(Alignment.Center))
                    // Yellow scan line that "prints" the stamp, top to bottom, once.
                    if (sweep.value in 0.01f..0.99f) {
                        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                            val p = sweep.value
                            val band = 34.dp.toPx()
                            val y = p * (size.height + band) - band
                            drawRect(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, WizAccent.copy(alpha = .6f * (1f - p * 0.6f)), Color.Transparent),
                                    startY = y, endY = y + band
                                ),
                                topLeft = androidx.compose.ui.geometry.Offset(0f, y),
                                size = androidx.compose.ui.geometry.Size(size.width, band)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                FieldLabel("HEADER", required = true)
                OutlinedTextField(
                    value = header,
                    onValueChange = { header = it.take(90).replace("\n", " "); headerError = false },
                    placeholder = { Text("e.g. HPCL Sector 12 · Tank 2 dip", color = WizMuted) },
                    singleLine = true,
                    isError = headerError,
                    supportingText = if (headerError) {{ Text("Header is required. It goes on top of the photo.") }} else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    colors = wizFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (recentHeaders.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.History, null, tint = WizMuted, modifier = Modifier.size(18.dp))
                        recentHeaders.forEach { h -> PillChip(h, h == header) { header = h; headerError = false } }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FieldLabel("NOTES", hint = "printed at the bottom", modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val t = body.text
                        val add = if (t.isEmpty() || t.endsWith("\n")) "• " else "\n• "
                        val nt = t + add
                        body = TextFieldValue(nt, TextRange(nt.length))
                    }) {
                        Icon(Icons.Filled.FormatListBulleted, null, tint = WizAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add point", color = WizAccent)
                    }
                }
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    placeholder = { Text("Points to remember: nozzle 3 leaking, stock 4,200 L, call dealer…", color = WizMuted) },
                    minLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = wizFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))
                FieldLabel("MAIL TO", hint = "optional · dealer IDs")
                if (emails.isNotEmpty()) {
                    FlowRow(
                        Modifier.padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) { emails.forEach { e -> EmailChip(e) { emails.remove(e) } } }
                }
                OutlinedTextField(
                    value = emailText,
                    onValueChange = { v ->
                        emailError = null
                        if (v.any { it == ',' || it == ' ' || it == ';' || it == '\n' }) commitEmails(v) else emailText = v
                    },
                    placeholder = { Text("dealer@example.com", color = WizMuted) },
                    leadingIcon = { Icon(Icons.Filled.AlternateEmail, null, tint = WizMuted) },
                    singleLine = true,
                    isError = emailError != null,
                    supportingText = emailError?.let { {Text(it)} },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (emailText.isNotBlank()) commitEmails(emailText) }),
                    colors = wizFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (suggestions.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestions.forEach { s -> PillChip("+ $s", false) { emails += s; emailText = ""; emailError = null } }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // Sticky actions
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(WizBar)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { save(false) },
                    enabled = !saving,
                    border = BorderStroke(1.dp, WizAccent.copy(alpha = .6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WizAccent),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(54.dp)
                ) { Text("Save", style = MaterialTheme.typography.titleSmall) }
                Button(
                    onClick = { save(true) },
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = WizAccent, contentColor = WizNavy),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1.4f).height(54.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save & Mail", style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        // Saving / saved overlay
        AnimatedVisibility(saving, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
            Box(Modifier.fillMaxSize().background(Color(0xCC07080A)), contentAlignment = Alignment.Center) {
                if (!saved) {
                    CircularProgressIndicator(color = WizAccent)
                } else {
                    SavedBurst()
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = WizPanel,
            title = { Text("Discard this photo?", color = WizText) },
            text = { Text("The photo and note will not be saved.", color = WizMuted) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; raw.delete(); onCancel() }) { Text("Discard", color = WizBad) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing", color = WizAccent) } }
        )
    }
}

@Composable
private fun SavedBurst() {
    val s = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) { s.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow)) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(92.dp).graphicsLayer { scaleX = s.value; scaleY = s.value }.clip(CircleShape).background(WizAccent),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Check, "Saved", tint = WizNavy, modifier = Modifier.size(52.dp)) }
        Spacer(Modifier.height(16.dp))
        Text("Saved to gallery", color = WizText, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier, required: Boolean = false, hint: String? = null) {
    Row(modifier.padding(start = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = WizAccent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        if (required) Text("  *", color = WizAccent, style = MaterialTheme.typography.labelMedium)
        if (hint != null) Text("  ·  $hint", color = WizMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmailChip(email: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = WizAccent.copy(alpha = .14f),
        border = BorderStroke(1.dp, WizAccent.copy(alpha = .4f))
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(email, color = WizText, style = MaterialTheme.typography.labelLarge)
            Surface(onClick = onRemove, shape = CircleShape, color = Color.Transparent, modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "Remove $email", tint = WizMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun wizFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = WizAccent,
    unfocusedBorderColor = WizLine,
    cursorColor = WizAccent,
    focusedTextColor = WizText,
    unfocusedTextColor = WizText,
    focusedContainerColor = WizPanel,
    unfocusedContainerColor = WizPanel,
    errorContainerColor = WizPanel
)
