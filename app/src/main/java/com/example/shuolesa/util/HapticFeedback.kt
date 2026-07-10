package com.example.shuolesa.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticFeedback(private val context: Context) {

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /** Single short pulse: recording started */
    fun singlePulse() {
        vibrator.vibrate(
            VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    /** Double short pulse: recording stopped */
    fun doublePulse() {
        vibrator.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 50, 50, 50), // delay, vib, pause, vib
                intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE),
                -1 // no repeat
            )
        )
    }

    /** Triple long pulse: storage alarm */
    fun alarmPulse() {
        vibrator.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 300, 200, 300, 200, 300),
                intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE),
                -1
            )
        )
    }
}
