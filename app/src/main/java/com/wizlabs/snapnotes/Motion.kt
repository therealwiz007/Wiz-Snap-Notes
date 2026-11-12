package com.wizlabs.snapnotes

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/**
 * Motion toolkit. The rule everywhere: decorative motion runs at ≤30 fps, is read only in the
 * draw phase (no recomposition per frame), and stops when the screen is hidden or unfocused,
 * under Battery Saver, or when the system animation scale is off.
 */
object WizMotion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** System-level permission for decorative motion. */
    fun systemAllows(context: Context): Boolean =
        ValueAnimator.areAnimatorsEnabled() &&
            context.getSystemService(PowerManager::class.java)?.isPowerSaveMode != true
}

/** System setting only (animations on, Battery Saver off), re-checked when Battery Saver flips. */
@Composable
fun rememberSystemMotion(): Boolean {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(WizMotion.systemAllows(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                allowed = WizMotion.systemAllows(context)
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return allowed
}

/** True while continuous decorative motion may run on this screen. */
@Composable
fun rememberMotionAllowed(): Boolean {
    val owner = LocalLifecycleOwner.current
    val system = rememberSystemMotion()
    val focused = LocalWindowInfo.current.isWindowFocused
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return system && resumed && focused
}

/**
 * Seconds of "animation time", advanced at most [fps] times per second while [running].
 * Read `.value` only inside draw / graphicsLayer lambdas so only the draw phase is invalidated.
 * When stopped it keeps its last value, so the art freezes as a still frame.
 */
@Composable
fun rememberThrottledClock(running: Boolean, fps: Int = 30): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running, fps) {
        if (!running) return@LaunchedEffect
        val frameMs = (1000L / fps).coerceAtLeast(16L)
        var last = System.nanoTime()
        while (true) {
            delay(frameMs)
            val now = System.nanoTime()
            // Clamp the step so a long pause doesn't make the art jump.
            t.floatValue += ((now - last) / 1e9f).coerceAtMost(0.1f)
            last = now
        }
    }
    return t
}

/** Press-in on touch with a springy release, done in graphicsLayer (no relayout). */
fun Modifier.pressScale(interaction: InteractionSource, pressedScale: Float = 0.9f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        if (pressed) tween(90) else spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

/** One-time staggered fade-and-rise when a section first appears. */
fun Modifier.enterRise(index: Int, rise: Float = 22f, gapMs: Long = 55L): Modifier = composed {
    val system = rememberSystemMotion()
    val p = remember { Animatable(if (system) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (p.value < 1f) {
            delay(40L + index * gapMs)
            p.animateTo(1f, tween(460, easing = WizMotion.Emphasized))
        }
    }
    graphicsLayer {
        val v = p.value
        alpha = v
        translationY = (1f - v) * rise.dp.toPx()
    }
}
