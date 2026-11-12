package com.wizlabs.snapnotes

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Haptics {
    @Suppress("DEPRECATION")
    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** A short, firm buzz at the moment the photo is taken. */
    fun shutter(context: Context) {
        try {
            val v = vibrator(context.applicationContext) ?: return
            if (!v.hasVibrator()) return
            v.vibrate(VibrationEffect.createOneShot(60L, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            // vibration is a nicety; never let it break capture
        }
    }
}
