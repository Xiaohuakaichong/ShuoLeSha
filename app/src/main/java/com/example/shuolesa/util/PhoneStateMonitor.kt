package com.example.shuolesa.util

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi

/**
 * v4.1-a: call detection without READ_PHONE_STATE.
 * A record-only app never holds audio focus, so focus callbacks are useless here. Instead we watch
 * the system audio mode: MODE_IN_CALL (cellular) or MODE_IN_COMMUNICATION (VoIP: WeChat etc.).
 * API 31+: AudioManager.addOnModeChangedListener. API 29–30: poll AudioManager.getMode() every second.
 * Needs no runtime permission.
 */
class PhoneStateMonitor(context: Context) {

    interface PhoneStateCallback {
        fun onCallStarted()
        fun onCallEnded()
    }

    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var callback: PhoneStateCallback? = null
    private var listener: Any? = null
    private var polling: Runnable? = null
    private var wasInCall = false

    fun start(callback: PhoneStateCallback) {
        stop()
        this.callback = callback
        wasInCall = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) registerListener()
        else {
            val tick = object : Runnable {
                override fun run() {
                    handleMode(audio?.mode ?: AudioManager.MODE_NORMAL)
                    main.postDelayed(this, POLL_MS)
                }
            }
            polling = tick
            main.post(tick)
        }
        // A call already in progress when recording starts counts as a call.
        main.post { audio?.let { handleMode(it.mode) } }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerListener() {
        val l = AudioManager.OnModeChangedListener { mode -> main.post { handleMode(mode) } }
        audio?.addOnModeChangedListener(java.util.concurrent.Executor { main.post(it) }, l)
        listener = l
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun unregisterListener() {
        (listener as? AudioManager.OnModeChangedListener)?.let { runCatching { audio?.removeOnModeChangedListener(it) } }
    }

    private fun handleMode(mode: Int) {
        val inCall = isCallMode(mode)
        if (inCall && !wasInCall) { wasInCall = true; callback?.onCallStarted() }
        else if (!inCall && wasInCall) { wasInCall = false; callback?.onCallEnded() }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) unregisterListener()
        polling?.let { main.removeCallbacks(it) }
        polling = null
        listener = null
        callback = null
    }

    companion object {
        const val POLL_MS = 1000L
        fun isCallMode(mode: Int) = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE
    }
}
