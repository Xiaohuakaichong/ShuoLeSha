package com.example.shuolesa.service

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.example.shuolesa.AppContainer
import com.example.shuolesa.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * v4.1-a.3 (qa-v41a a.2 P1): the ONLY entry that may auto-start recording. exported=false, translucent,
 * no UI. Reached by explicit in-app intents from RecordTileService, BlindTriggerService and
 * AudioCaptureService (consent missing). It reads the real stored consent, then either starts recording
 * itself or hands MainActivity a one-time token for the screen to show.
 */
class ShortcutTrampolineActivity : Activity() {
    private val scope: CoroutineScope = MainScope()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = AppContainer.from(this)
        scope.launch {
            val consent = app.preferences.privacyConsented.first()
            TileConsentCache.value = consent
            val mic = ContextCompat.checkSelfPermission(this@ShortcutTrampolineActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            when (ShortcutDecision.onTrampoline(consent, mic, MainActivity.visible)) {
                ShortcutDecision.Trampoline.START_SILENTLY -> startRecording()
                ShortcutDecision.Trampoline.START_AND_NOTIFY_APP -> { startRecording(); openMain(ShortcutDecision.MainIntent.SHOW_STARTED) }
                ShortcutDecision.Trampoline.ASK_MICROPHONE -> openMain(ShortcutDecision.MainIntent.ASK_MICROPHONE)
                ShortcutDecision.Trampoline.SHOW_CONSENT -> openMain(ShortcutDecision.MainIntent.SHOW_CONSENT)
            }
            finish()
            overridePendingTransition(0, 0)
        }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun startRecording() {
        if (AudioCaptureService.isRunning) return
        val intent = Intent(this, AudioCaptureService::class.java).setAction(AudioCaptureService.ACTION_START)
        getIntent().getStringExtra(AudioCaptureService.EXTRA_RECORDING_MODE)?.let { intent.putExtra(AudioCaptureService.EXTRA_RECORDING_MODE, it) }
        ContextCompat.startForegroundService(this, intent)
    }
    private fun openMain(request: ShortcutDecision.MainIntent) {
        startActivity(MainActivity.internalIntent(this, request, ShortcutTokens.shared.issue()))
    }

    companion object {
        fun intent(context: Context, mode: String? = null) = Intent(context, ShortcutTrampolineActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            .apply { mode?.let { putExtra(AudioCaptureService.EXTRA_RECORDING_MODE, it) } }
    }
}
