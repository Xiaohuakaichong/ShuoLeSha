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
    private var lifelogDurationMs = 2000L
    private var meetingDurationMs = 4000L

    // Key state tracking
    private var volumeUpPressed = false
    private var volumeDownPressed = false
    private var bothPressedActive = false

    private var reachedLifelog = false
    private var reachedMeeting = false

    private var lifelogRunnable: Runnable? = null
    private var meetingRunnable: Runnable? = null
    private var stopRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceActive = true
        hapticFeedback = HapticFeedback(this)
        prefs = AppPreferences(this)
        Log.d(TAG, "BlindTriggerService connected")

        scope.launch {
            prefs?.lifelogTriggerDuration?.collect { seconds ->
                lifelogDurationMs = seconds * 1000L
                Log.d(TAG, "LifeLog trigger duration: $lifelogDurationMs ms")
            }
        }
        scope.launch {
            prefs?.meetingTriggerDuration?.collect { seconds ->
                meetingDurationMs = seconds * 1000L
                Log.d(TAG, "Meeting trigger duration: $meetingDurationMs ms")
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

                // Both pressed — start tactile ladder
                if (volumeUpPressed && volumeDownPressed) {
                    bothPressedActive = true
                    val isRecording = AudioCaptureService.isRunning

                    if (isRecording) {
                        if (stopRunnable == null) {
                            val r = Runnable {
                                onStopTriggered()
                                stopRunnable = null
                            }
                            stopRunnable = r
                            handler.postDelayed(r, 1200L)
                        }
                    } else {
                        if (lifelogRunnable == null && meetingRunnable == null) {
                            reachedLifelog = false
                            reachedMeeting = false

                            // Stage 1: LifeLog
                            val r1 = Runnable {
                                reachedLifelog = true
                                scope.launch {
                                    if (prefs?.hapticEnabled?.first() ?: true) {
                                        hapticFeedback?.singlePulse()
                                    }
                                }
                                Log.d(TAG, "Ladder Stage 1 reached: LifeLog (release now to start LifeLog, or keep holding for Meeting)")
                            }
                            lifelogRunnable = r1
                            handler.postDelayed(r1, lifelogDurationMs)

                            // Stage 2: Meeting
                            val r2 = Runnable {
                                reachedMeeting = true
                                scope.launch {
                                    if (prefs?.hapticEnabled?.first() ?: true) {
                                        hapticFeedback?.doublePulse()
                                    }
                                }
                                Log.d(TAG, "Ladder Stage 2 reached: Launching Meeting recording")
                                onStartTriggered("meeting")
                            }
                            meetingRunnable = r2
                            handler.postDelayed(r2, meetingDurationMs)
                        }
                    }
                    return true
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

                if (!volumeUpPressed || !volumeDownPressed) {
                    stopRunnable?.let { handler.removeCallbacks(it) }
                    stopRunnable = null

                    lifelogRunnable?.let { handler.removeCallbacks(it) }
                    lifelogRunnable = null

                    meetingRunnable?.let { handler.removeCallbacks(it) }
                    meetingRunnable = null

                    val isRecording = AudioCaptureService.isRunning
                    if (!isRecording) {
                        if (reachedMeeting) {
                            Log.d(TAG, "Meeting mode already triggered at stage 2")
                        } else if (reachedLifelog) {
                            Log.d(TAG, "Released after Stage 1: Launching LifeLog recording")
                            onStartTriggered("lifelog")
                        }
                    }

                    reachedLifelog = false
                    reachedMeeting = false
                }

                return wasBothPressed
            }
        }

        return false
    }

    private fun onStartTriggered(mode: String) {
        scope.launch {
            Log.d(TAG, "Trigger: START recording in mode: $mode")
            val intent = Intent(this@BlindTriggerService, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_START
                putExtra(AudioCaptureService.EXTRA_RECORDING_MODE, mode)
            }
            startForegroundService(intent)
        }
    }

    private fun onStopTriggered() {
        scope.launch {
            val hapticEnabled = prefs?.hapticEnabled?.first() ?: true
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

    override fun onDestroy() {
        isServiceActive = false
        stopRunnable?.let { handler.removeCallbacks(it) }
        lifelogRunnable?.let { handler.removeCallbacks(it) }
        meetingRunnable?.let { handler.removeCallbacks(it) }
        scope.cancel()
        super.onDestroy()
    }
}
