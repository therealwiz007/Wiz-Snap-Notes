package com.wizlabs.snapnotes

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

/** Simple screen stack; Home is the root. */
sealed interface Screen {
    val depth: Int
    data object Home : Screen { override val depth = 0 }
    data object Camera : Screen { override val depth = 1 }
    data class Editor(val raw: File) : Screen { override val depth = 2 }
    data class Detail(val id: Long) : Screen { override val depth = 1 }
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
        setContent { WizTheme { App() } }
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

    BackHandler(enabled = screen != Screen.Home && screen !is Screen.Editor) { screen = Screen.Home }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val dir = if (targetState.depth >= initialState.depth) 1 else -1
            (slideInHorizontally(tween(320, easing = WizMotion.Emphasized)) { dir * it / 5 } + fadeIn(tween(240))) togetherWith
                (slideOutHorizontally(tween(260)) { -dir * it / 8 } + fadeOut(tween(180)))
        },
        label = "screen"
    ) { s ->
        when (s) {
            Screen.Home -> HomeScreen(
                onSnap = { screen = Screen.Camera },
                onOpen = { screen = Screen.Detail(it) },
                onAbout = { screen = Screen.About },
                onBackup = { screen = Screen.Backup }
            )
            Screen.Camera -> CameraScreen(onBack = { screen = Screen.Home }, onCaptured = { screen = Screen.Editor(it) })
            is Screen.Editor -> EditorScreen(
                raw = s.raw,
                onCancel = { screen = Screen.Camera },
                onSaved = { note, mail ->
                    screen = Screen.Detail(note.id)
                    DriveBackup.sync(context)
                    if (mail) Mailer.mail(context, note)
                }
            )
            is Screen.Detail -> DetailScreen(s.id, onBack = { screen = Screen.Home })
            Screen.About -> AboutScreen(onBack = { screen = Screen.Home })
            Screen.Backup -> BackupScreen(onBack = { screen = Screen.Home })
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
