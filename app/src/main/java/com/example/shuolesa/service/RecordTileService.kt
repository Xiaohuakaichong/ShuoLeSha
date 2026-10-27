package com.example.shuolesa.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import com.example.shuolesa.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 快捷设置磁贴：用户可在下拉通知栏快捷开关中直接点击开启或停止录音。
 */
class RecordTileService : TileService() {
    companion object { private val tapDebouncer = com.example.shuolesa.ui.logic.TapDebouncer(1000) }

    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        TileConsentCache.clear() // re-read on every listen; until then treat as not consented
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { sc ->
            sc.launch { AppContainer.from(this@RecordTileService).preferences.privacyConsented.collect { TileConsentCache.value = it } }
        }
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (!tapDebouncer.accept()) return // v4.1: ignore repeated taps within 1 s
        when (ShortcutDecision.onTileTap(AudioCaptureService.isRunning, TileConsentCache.value)) {
            ShortcutDecision.TileTap.STOP -> startService(Intent(this, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_STOP
            })
            ShortcutDecision.TileTap.START_RECORDING -> ContextCompat.startForegroundService(this,
                Intent(this, AudioCaptureService::class.java).apply { action = AudioCaptureService.ACTION_START })
            // Cache not loaded yet (cold start), revoked, or not consented: open the non-exported trampoline. It reads
            // the real stored value and starts recording, asks for the mic, or opens the consent page (v4.1-a.3).
            ShortcutDecision.TileTap.OPEN_TRAMPOLINE -> { openConsent(); return }
        }
        updateTileState()
    }

    /**
     * v4.1-a.2: no blocking DataStore read on the main thread. TileConsentCache is an in-memory value kept fresh by
     * collecting privacyConsented while the tile is listening (onStartListening..onStopListening). Until the
     * first value arrives it is null -> treated as "not consented" (safe side: open the app, never record).
     */
    private var scope: CoroutineScope? = null

    override fun onStopListening() {
        scope?.cancel(); scope = null
        super.onStopListening()
    }

    override fun onDestroy() { scope?.cancel(); scope = null; super.onDestroy() }

    /** v4.1-a.2: on the lock screen, ask the user to unlock first, then open the consent page. */
    private fun openConsent() {
        if (isLocked) unlockAndRun { launchConsent() } else launchConsent()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchConsent() {
        val intent = ShortcutTrampolineActivity.intent(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        if (AudioCaptureService.isRunning) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "停止录音"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "快捷录音"
        }
        tile.updateTile()
    }
}
