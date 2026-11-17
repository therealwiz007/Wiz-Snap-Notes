package com.wizlabs.snapnotes

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Adding another snap to a visit: the header, recipients and location choice carry over. */
data class VisitDraft(val visitId: Long, val header: String, val emails: List<String>, val location: Boolean, val next: Int)

enum class SaveAction { SAVE, MAIL, MORE }

/** Simple screen stack; Home is the root. */
sealed interface Screen {
    val depth: Int
    data object Home : Screen { override val depth = 0 }
    data class Camera(val visit: VisitDraft? = null) : Screen { override val depth = 1 }
    data class Editor(val raw: File, val visit: VisitDraft?) : Screen { override val depth = 2 }
    /** One visit: every snap taken under the same header in one go. */
    data class Detail(val visitId: Long) : Screen { override val depth = 1 }
    data object About : Screen { override val depth = 1 }
    data object Backup : Screen { override val depth = 1 }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        // Leftover raw captures from a closed editor are never needed again.
        cacheDir.listFiles { f -> f.name.startsWith("snap_raw_") }?.forEach { it.delete() }
        DriveBackup.init(this)
        setContent { WizTheme { ProvideWizMotion { App() } } }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) MotionIdle.onTouch()
        return super.dispatchTouchEvent(ev)
    }

    override fun onStart() {
        super.onStart()
        // Picks up anything that could not upload last time.
        DriveBackup.sync(this)
    }
}

@Composable
private fun App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun mailVisit(visitId: Long) = scope.launch {
        val notes = withContext(Dispatchers.IO) { NoteStore.get(context).visit(visitId) }
        if (notes.isNotEmpty()) Mailer.mail(context, notes)
    }
    // Back from a camera opened for another snap returns to that visit, not Home.
    val backTo: Screen = (screen as? Screen.Camera)?.visit?.let { Screen.Detail(it.visitId) } ?: Screen.Home

    WizBackHandler(screen.toString(), enabled = screen != Screen.Home && screen !is Screen.Editor) { screen = backTo }
    val peek = LocalBackPeek.current
    // Once the back transition has played, drop the peek so the next visit starts full size.
    LaunchedEffect(screen) { kotlinx.coroutines.delay(650); if (peek != null && !peek.dragging) peek.reset() }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val dir = if (targetState.depth >= initialState.depth) 1 else -1
            (slideInHorizontally(tween(320, easing = WizMotion.Emphasized)) { dir * it / 5 } + fadeIn(tween(240))) togetherWith
                (slideOutHorizontally(tween(260)) { -dir * it / 8 } + fadeOut(tween(180)))
        },
        label = "screen"
    ) { s ->
      // Camera preview is a SurfaceView, which ignores layer transforms: no peek there.
      Box(if (s is Screen.Camera) Modifier.fillMaxSize() else Modifier.fillMaxSize().backPeek(s.toString())) {
        when (s) {
            Screen.Home -> HomeScreen(
                onSnap = { screen = Screen.Camera() },
                onOpen = { screen = Screen.Detail(it) },
                onAbout = { screen = Screen.About },
                onBackup = { screen = Screen.Backup }
            )
            is Screen.Camera -> CameraScreen(
                onBack = { screen = s.visit?.let { Screen.Detail(it.visitId) } ?: Screen.Home },
                onCaptured = { screen = Screen.Editor(it, s.visit) },
                visitLabel = s.visit?.let { "Snap ${it.next} · ${it.header}" }
            )
            is Screen.Editor -> EditorScreen(
                raw = s.raw,
                visit = s.visit,
                onCancel = { screen = Screen.Camera(s.visit) },
                onSaved = { note, action, loc, seq ->
                    DriveBackup.sync(context)
                    screen = if (action == SaveAction.MORE) {
                        Screen.Camera(VisitDraft(note.visitId, note.header, note.emails, loc, seq + 1))
                    } else Screen.Detail(note.visitId)
                    if (action == SaveAction.MAIL) {
                        if (s.visit == null) Mailer.mail(context, listOf(note)) else mailVisit(note.visitId)
                    }
                }
            )
            is Screen.Detail -> DetailScreen(
                s.visitId,
                onBack = { screen = Screen.Home },
                onAddSnap = { screen = Screen.Camera(it) }
            )
            Screen.About -> AboutScreen(onBack = { screen = Screen.Home })
            Screen.Backup -> BackupScreen(onBack = { screen = Screen.Home })
        }
      }
    }
}

@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    Column(Modifier.fillMaxSize().background(WizNavy)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Wiz Snap Notes", color = WizText, style = MaterialTheme.typography.titleMedium)
                Text("Version $version · Wiz Labs", color = WizMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        // The bundled privacy policy (same file as the website copy). No network is used.
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(0xFF0B0D10.toInt())
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    loadUrl("file:///android_asset/privacy-policy.html")
                }
            },
            modifier = Modifier.fillMaxSize().navigationBarsPadding()
        )
    }
}
