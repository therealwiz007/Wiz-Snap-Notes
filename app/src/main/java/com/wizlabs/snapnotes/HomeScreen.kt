package com.wizlabs.snapnotes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

@Composable
fun HomeScreen(onSnap: () -> Unit, onOpen: (Long) -> Unit, onAbout: () -> Unit) {
    val context = LocalContext.current
    val store = remember { NoteStore.get(context) }
    var query by rememberSaveable { mutableStateOf("") }
    var range by rememberSaveable { mutableStateOf(DateRange.ALL) }
    var notes by remember { mutableStateOf<List<Note>?>(null) }
    var total by remember { mutableIntStateOf(0) }
    var headers by remember { mutableStateOf(emptyList<String>()) }
    val terms = remember(query) { SearchText.terms(query) }

    LaunchedEffect(query, range, store.version) {
        if (notes != null) delay(120) // debounce typing
        val (list, count, recent) = withContext(Dispatchers.IO) {
            Triple(store.search(query, range), store.count(), store.recentHeaders(8))
        }
        notes = list; total = count; headers = recent
    }

    val grid = rememberLazyGridState()
    Box(Modifier.fillMaxSize().background(WizNavy)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = grid,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "hero") { Hero(total, onAbout) }
            item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
                SearchBlock(query, { query = it }, range, { range = it }, headers)
            }
            val list = notes
            when {
                list == null -> Unit
                list.isEmpty() && total == 0 -> item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    EmptyState("No photo notes yet", "Tap Snap, take a photo, add a header and your points. It lands here, ready to search and mail.", first = true)
                }
                list.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }, key = "none") {
                    EmptyState("Nothing matches", if (query.isNotBlank()) "No header or note contains \"${query.trim()}\"." else "No photo notes in this date range.")
                }
                else -> {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "count") {
                        Text(
                            if (terms.isEmpty() && range == DateRange.ALL) "ALL NOTES" else "${list.size} FOUND",
                            color = WizMuted, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                    itemsIndexed(list, key = { _, it -> it.id }) { i, n -> NoteCard(n, terms, i) { onOpen(n.id) } }
                }
            }
        }

        SnapButton(
            pulse = total == 0,
            onClick = onSnap,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 22.dp)
        )
    }
}

@Composable
private fun Hero(total: Int, onAbout: () -> Unit) {
    val motion = rememberMotionAllowed()
    val clock = rememberThrottledClock(running = motion, fps = 24)
    val density = LocalDensity.current
    val artPx = with(density) { 132.dp.roundToPx() }
    val art = remember(artPx) { HeroArt.get(artPx) }
    // Count up to the number of notes once.
    val counter = remember { Animatable(0f) }
    LaunchedEffect(total) { counter.animateTo(total.toFloat(), tween(if (motion) 700 else 0, easing = WizMotion.Emphasized)) }

    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp)
            .height(176.dp)
            .clip(RoundedCornerShape(26.dp))
            .drawBehind {
                drawRect(WizPanel)
                // Soft yellow glow behind the art
                drawCircle(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(WizAccent.copy(alpha = .22f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.5f),
                        radius = size.height * 0.85f
                    ),
                    radius = size.height * 0.85f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.5f)
                )
            }
    ) {
        Column(Modifier.align(Alignment.CenterStart).padding(start = 20.dp, end = 150.dp)) {
            Text("WIZ LABS", color = WizAccent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Snap Notes", color = WizText, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "%,d".format(counter.value.toInt()),
                    color = WizAccent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))
                Text(if (total == 1) "photo note" else "photo notes", color = WizMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
        Image(
            art, contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
                .size(132.dp)
                .graphicsLayer {
                    val t = clock.value
                    translationY = sin(t * 1.6f) * 5.dp.toPx()
                    rotationZ = sin(t * 0.9f) * 2.2f
                }
        )
        IconButton(onClick = onAbout, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
            Icon(Icons.Filled.Info, "About", tint = WizMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SearchBlock(
    query: String, onQuery: (String) -> Unit,
    range: DateRange, onRange: (DateRange) -> Unit,
    headers: List<String>
) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text("Search headers and notes", color = WizMuted) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = WizAccent) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Clear", tint = WizMuted) } }
            } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(50),
            colors = wizFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DateRange.entries.forEach { r -> PillChip(r.label, r == range) { onRange(r) } }
            if (headers.isNotEmpty()) {
                Box(Modifier.padding(horizontal = 2.dp).size(1.dp, 34.dp).background(WizLine))
                headers.forEach { h -> PillChip(h, query.equals(h, ignoreCase = true)) { onQuery(if (query == h) "" else h) } }
            }
        }
    }
}

private val cardDate = SimpleDateFormat("d MMM · h:mm a", Locale.ENGLISH)

@Composable
private fun NoteCard(n: Note, terms: List<String>, index: Int, onClick: () -> Unit) {
    val img by rememberImage(n.thumb)
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = WizPanel,
        border = BorderStroke(1.dp, WizLine),
        interactionSource = interaction,
        modifier = Modifier.enterRise(index.coerceAtMost(7)).pressScale(interaction, 0.96f)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(0.82f).background(WizPanelHi)) {
                img?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                if (n.emails.isNotEmpty()) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp).clip(CircleShape).background(WizGlass),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.MailOutline, "Has recipients", tint = WizAccent, modifier = Modifier.size(15.dp)) }
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    highlight(n.header, terms), color = WizText, style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (n.body.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        highlight(SearchText.snippet(n.body, terms, 70), terms), color = WizMuted,
                        style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(cardDate.format(Date(n.createdMs)), color = WizAccent.copy(alpha = .8f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Marks the searched words in yellow. */
fun highlight(text: String, terms: List<String>): AnnotatedString {
    val ranges = SearchText.matches(text, terms)
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        ranges.forEach {
            addStyle(SpanStyle(color = WizNavy, background = WizAccent, fontWeight = FontWeight.Bold), it.first, it.last + 1)
        }
    }
}

@Composable
private fun EmptyState(title: String, text: String, first: Boolean = false) {
    Column(
        Modifier.fillMaxWidth().enterRise(0).padding(top = 40.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(WizAccent.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(if (first) Icons.Filled.PhotoCamera else Icons.Filled.Search, null, tint = WizAccent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, color = WizText, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(text, color = WizMuted, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun SnapButton(pulse: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val motion = rememberMotionAllowed()
    val clock = rememberThrottledClock(running = pulse && motion, fps = 24)
    val interaction = remember { MutableInteractionSource() }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (pulse) {
            Box(
                Modifier.size(width = 168.dp, height = 62.dp).graphicsLayer {
                    val p = (clock.value / 1.8f) % 1f
                    scaleX = 1f + p * 0.22f; scaleY = 1f + p * 0.5f
                    alpha = (1f - p) * 0.5f
                }.clip(RoundedCornerShape(50)).background(WizAccent)
            )
        }
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(50),
            color = WizAccent,
            contentColor = WizNavy,
            shadowElevation = 10.dp,
            interactionSource = interaction,
            modifier = Modifier.pressScale(interaction, 0.92f)
        ) {
            Row(Modifier.padding(horizontal = 30.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PhotoCamera, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("Snap", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
