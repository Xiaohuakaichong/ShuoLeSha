package com.example.shuolesa.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

/**
 * 快捷设置磁贴：用户可在下拉通知栏快捷开关中直接点击开启或停止录音。
 */
class RecordTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (AudioCaptureService.isRunning) {
            val intent = Intent(this, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_STOP
            }
            startService(intent)
        } else {
            val intent = Intent(this, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_START
            }
            ContextCompat.startForegroundService(this, intent)
        }
        updateTileState()
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
