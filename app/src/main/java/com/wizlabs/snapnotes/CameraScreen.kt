package com.wizlabs.snapnotes

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val flashModes = intArrayOf(ImageCapture.FLASH_MODE_OFF, ImageCapture.FLASH_MODE_AUTO, ImageCapture.FLASH_MODE_ON)

@Composable
fun CameraScreen(onBack: () -> Unit, onCaptured: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var lens by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flash by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var hasFront by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val provider = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    val iris = remember { Animatable(0f) }

    LaunchedEffect(previewView, lens, granted) {
        val pv = previewView ?: return@LaunchedEffect
        if (!granted) return@LaunchedEffect
        try {
            val p = suspendCancellableCoroutine<ProcessCameraProvider> { c ->
                val f = ProcessCameraProvider.getInstance(context)
                f.addListener({
                    try { c.resume(f.get()) } catch (e: Exception) { c.resumeWithException(e) }
                }, ContextCompat.getMainExecutor(context))
            }
            provider[0] = p
            hasFront = runCatching { p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
            val ratio = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            val preview = Preview.Builder()
                .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build())
                .build().also { it.setSurfaceProvider(pv.surfaceProvider) }
            // Cap stills near 8 MP: sharp enough for meters and labels, light on memory and mail.
            val cap = 8_400_000L
            val ic = ImageCapture.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(ratio)
                        .setResolutionFilter { sizes, _ ->
                            val within = sizes.filter { it.width.toLong() * it.height <= cap }
                            (within.ifEmpty { sizes }).sortedByDescending { it.width.toLong() * it.height }
                        }.build()
                )
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(flashModes[flash])
                .build()
            p.unbindAll()
            camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.Builder().requireLensFacing(lens).build(), preview, ic)
            imageCapture = ic
            error = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Camera unavailable"
        }
    }
    LaunchedEffect(flash, imageCapture) { imageCapture?.flashMode = flashModes[flash] }
    DisposableEffect(Unit) { onDispose { provider[0]?.unbindAll() } }

    fun shoot() {
        val ic = imageCapture ?: return
        if (busy) return
        busy = true
        Haptics.shutter(context)
        scope.launch {
            launch {
                iris.snapTo(0f)
                iris.animateTo(1f, tween(130, easing = FastOutSlowInEasing))
                iris.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
            }
            try {
                onCaptured(PhotoPipeline.capture(context, ic))
            } catch (e: Exception) {
                error = "Could not take the photo. Try again."
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(WizBar)) {
        Column(Modifier.fillMaxSize()) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Spacer(Modifier.weight(1f))
                Text("NEW SNAP", color = WizAccent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                GlassIconButton(
                    when (flash) { 1 -> Icons.Filled.FlashAuto; 2 -> Icons.Filled.FlashOn; else -> Icons.Filled.FlashOff },
                    "Flash", { flash = (flash + 1) % 3 }, active = flash != 0,
                    enabled = lens == CameraSelector.LENS_FACING_BACK
                )
            }

            // Viewfinder (4:3, the same shape as the saved photo)
            Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .aspectRatio(3f / 4f, matchHeightConstraintsFirst = false)
                        .clip(RoundedCornerShape(22.dp))
                        .background(WizNavy)
                ) {
                    if (granted) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    previewView = this
                                }
                            }
                        )
                        HeaderGuide(Modifier.align(Alignment.TopCenter))
                    } else {
                        PermissionAsk(Modifier.align(Alignment.Center)) { ask.launch(Manifest.permission.CAMERA) }
                    }
                    // Iris: one short close-open sweep per shot, drawn only while it runs.
                    Canvas(Modifier.fillMaxSize()) {
                        val v = iris.value
                        if (v <= 0.001f) return@Canvas
                        val big = hypot(size.width, size.height) / 2f
                        val hole = big * (1f - v)
                        drawCircle(Color.Black, radius = (hole + big) / 2f, style = Stroke(width = big - hole + 2f))
                        val c = center
                        for (i in 0 until 6) {
                            val a = Math.toRadians(i * 60.0 + v * 40.0)
                            val p1 = Offset(c.x + cos(a).toFloat() * hole, c.y + sin(a).toFloat() * hole)
                            val p2 = Offset(c.x + cos(a + 1.2).toFloat() * big, c.y + sin(a + 1.2).toFloat() * big)
                            drawLine(WizAccent.copy(alpha = 0.55f * v), p1, p2, strokeWidth = 3f)
                        }
                    }
                    error?.let {
                        Surface(
                            color = WizGlass, shape = RoundedCornerShape(50),
                            modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp)
                        ) { Text(it, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
                    }
                }
            }

            // Controls
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 22.dp, horizontal = 34.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(Modifier.size(52.dp))
                ShutterButton(enabled = imageCapture != null && !busy, busy = busy, onClick = ::shoot)
                if (hasFront) {
                    GlassIconButton(
                        Icons.Filled.FlipCameraAndroid, "Switch camera",
                        {
                            lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
                            else CameraSelector.LENS_FACING_BACK
                        },
                        size = 52.dp
                    )
                } else Spacer(Modifier.size(52.dp))
            }
        }
    }
}

/** Faint outline where the header band will be stamped, so the subject stays clear of it. */
@Composable
private fun HeaderGuide(modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(10.dp)
            .height(46.dp)
            .border(BorderStroke(1.dp, WizAccent.copy(alpha = .45f)), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            "HEADER GOES HERE",
            color = WizAccent.copy(alpha = .75f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 14.dp)
        )
    }
}

@Composable
private fun PermissionAsk(modifier: Modifier, onAsk: () -> Unit) {
    Column(modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Camera access needed", color = WizText, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Snap Notes uses the camera only to take your photo notes.", color = WizMuted)
        Spacer(Modifier.height(18.dp))
        Button(onClick = onAsk, colors = ButtonDefaults.buttonColors(containerColor = WizAccent, contentColor = WizNavy)) {
            Text("Allow camera")
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        interactionSource = interaction,
        border = BorderStroke(4.dp, Color.White),
        modifier = Modifier.pressScale(interaction, 0.88f).size(80.dp)
    ) {
        Box(Modifier.padding(8.dp).clip(CircleShape).background(if (enabled || busy) WizAccent else WizMuted), contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(color = WizNavy, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
        }
    }
}
