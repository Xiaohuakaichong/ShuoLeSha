package com.example.shuolesa.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * v4.1-a.3: when AudioCaptureService refuses to start for missing consent, open the consent page through the
 * same trampoline path as the tile. Starting an activity from a background service can be blocked by Android,
 * so we also post a notification whose tap opens the same trampoline: never a silent failure.
 */
object ConsentLauncher {
    private const val CHANNEL = "shuolesa_consent"
    private const val ID = 1002
    fun open(context: Context) {
        val intent = ShortcutTrampolineActivity.intent(context)
        runCatching { context.startActivity(intent) }
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "隐私同意", NotificationManager.IMPORTANCE_DEFAULT))
            val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(com.example.shuolesa.R.drawable.ic_mic) // app's monochrome mic
                .setContentTitle("使用快捷录音前，需要先同意隐私政策")
                .setContentText("点此打开说了啥完成同意，录音不会自动开始")
                .setContentIntent(pending).setAutoCancel(true).build()
            NotificationManagerCompat.from(context).notify(ID, notification)
        }
    }
    fun dismiss(context: Context) { runCatching { NotificationManagerCompat.from(context).cancel(ID) } }
}
