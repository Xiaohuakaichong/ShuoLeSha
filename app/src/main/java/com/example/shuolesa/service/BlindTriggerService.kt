package com.example.shuolesa.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.util.HapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * AccessibilityService that intercepts volume key events to implement
 * the blind trigger: simultaneous long press of Vol+ and Vol- for 3 seconds.
 */
class BlindTriggerService : AccessibilityService() {

    companion object {
        private const val TAG = "BlindTriggerService"
        private const val TRIGGER_DURATION_MS = 3000L

        @Volatile
        var isServiceActive = false
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var hapticFeedback: HapticFeedback? = null
    private var prefs: AppPreferences? = null
    private var triggerDurationMs = 3000L

    // Key state tracking
    private var volumeUpPressed = false
    private var volumeDownPressed = false
    private var bothPressedActive = false
    private var triggerRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceActive = true
        hapticFeedback = HapticFeedback(this)
        prefs = AppPreferences(this)
        Log.d(TAG, "BlindTriggerService connected")

        scope.launch {
            prefs?.triggerDuration?.collect { seconds ->
                triggerDurationMs = seconds * 1000L
                Log.d(TAG, "Trigger duration updated to: $triggerDurationMs ms")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need accessibility events, only key events
    }

    override fun onInterrupt() {
        Log.d(TAG, "BlindTriggerService interrupted")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        // Only handle volume keys
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        when (action) {
            KeyEvent.ACTION_DOWN -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> volumeUpPressed = true
                    KeyEvent.KEYCODE_VOLUME_DOWN -> volumeDownPressed = true
                }

                // Both pressed — start countdown
                if (volumeUpPressed && volumeDownPressed) {
                    bothPressedActive = true
                    if (triggerRunnable == null) {
                        val runnable = Runnable {
                            onTriggerActivated()
                            triggerRunnable = null
                        }
                        triggerRunnable = runnable
                        handler.postDelayed(runnable, triggerDurationMs)
                    }
                    return true // Consume when both are pressed
                }

                return false // Pass through single press so volume controls work
            }

            KeyEvent.ACTION_UP -> {
                val wasBothPressed = bothPressedActive

                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> volumeUpPressed = false
                    KeyEvent.KEYCODE_VOLUME_DOWN -> volumeDownPressed = false
                }

                if (!volumeUpPressed && !volumeDownPressed) {
                    bothPressedActive = false
                }

                // If either key released before trigger duration, cancel countdown
                if (!volumeUpPressed || !volumeDownPressed) {
                    triggerRunnable?.let { handler.removeCallbacks(it) }
                    triggerRunnable = null
                }

                return wasBothPressed // Only consume if we were in simultaneous press mode
            }
        }

        return false
    }

    private fun onTriggerActivated() {
        scope.launch {
            val hapticEnabled = prefs?.hapticEnabled?.first() ?: true
            val isRecording = AudioCaptureService.isRunning

            if (!isRecording) {
                // Start recording
                Log.d(TAG, "Trigger: START recording")
                val intent = Intent(this@BlindTriggerService, AudioCaptureService::class.java).apply {
                    action = AudioCaptureService.ACTION_START
                }
                startForegroundService(intent)

                if (hapticEnabled) {
                    hapticFeedback?.singlePulse()
                }
            } else {
                // Stop recording
                Log.d(TAG, "Trigger: STOP recording")
                val intent = Intent(this@BlindTriggerService, AudioCaptureService::class.java).apply {
                    action = AudioCaptureService.ACTION_STOP
                }
                startService(intent)

                if (hapticEnabled) {
                    hapticFeedback?.doublePulse()
                }
            }
        }
    }

    override fun onDestroy() {
        isServiceActive = false
        triggerRunnable?.let { handler.removeCallbacks(it) }
        scope.cancel()
        super.onDestroy()
    }
}
