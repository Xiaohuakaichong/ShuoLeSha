package com.example.shuolesa.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.shuolesa.MainActivity
import com.example.shuolesa.R

/**
 * Manages foreground service notifications.
 */
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "shuolesa_recording"
        const val NOTIFICATION_ID = 1001
        const val ACTION_FORCE_STOP = "com.example.shuolesa.ACTION_FORCE_STOP"
    }

    init {
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "录音服务",
            NotificationManager.IMPORTANCE_LOW // Silent
        ).apply {
            description = "说了啥后台录音服务通知"
            setSound(null, null)
            enableLights(false)
            enableVibration(false)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun buildRecordingNotification(startedAt: Long, modeTitle: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, AudioCaptureService::class.java).apply {
                action = ACTION_FORCE_STOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val label = modeTitle.ifBlank { "录音" }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("说了啥 · $label")
            .setContentText("正在听")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("正在听。点按回到应用，或在这里停止。"),
            )
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setWhen(startedAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setShortCriticalText(label)
            .setRequestPromotedOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_stop, "停止", stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun updateRecording(startedAt: Long, modeTitle: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildRecordingNotification(startedAt, modeTitle))
    }
}
