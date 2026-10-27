package com.example.shuolesa.service

import com.example.shuolesa.ui.logic.NoticeKind
import com.example.shuolesa.ui.logic.NoticePolicy
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.shuolesa.AppContainer
import com.example.shuolesa.MainActivity
import com.example.shuolesa.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * v4.1-b: event notifications (not the recording foreground notification).
 *  - 整理完成 (+ 待办候选数) -> tap opens 待办 (navigation-only action on MainActivity, never records)
 *  - ⑦ 盲触发失败兜底: double short vibration + "录音没能在后台开始"; "开始录音" goes through the non-exported
 *    trampoline (a.3 security model). Notifications off -> vibration only + home hint.
 */
object EventNotifications {
    private const val CHANNEL = "shuolesa_events"
    private const val ID_ORGANISED = 2001
    private const val ID_BLIND = 2002
    const val ACTION_OPEN_TASKS = "com.example.shuolesa.action.OPEN_TASKS"

    private fun channel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "整理与提醒", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun organised(context: Context, title: String, candidates: Int) {
        if (MainActivity.visible) return // in-app: the 待办 badge is enough
        runCatching {
            channel(context)
            val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_TASKS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val text = if (candidates > 0) "有 $candidates 项待办候选，确认后才会加入待办" else "要点已整理好，可以搜索和回听"
            NotificationManagerCompat.from(context).notify(ID_ORGANISED, NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify_ceer).setContentTitle("「$title」整理完成").setContentText(text)
                .setContentIntent(open).setAutoCancel(true).build())
        }
    }

    /** ⑦. Returns true when the notification could be shown. */
    fun blindTriggerFailed(context: Context) {
        vibrateDoubleShort(context)
        val app = AppContainer.from(context)
        app.scope.launch {
            // v4.1: each informational notice has its own persisted 「以后不提示」.
            val markHomeCard: suspend () -> Unit = {
                if (NoticePolicy.visible(NoticeKind.HOME_BLIND_FALLBACK, true, app.preferences.homeBlindCardOff.first()))
                    app.preferences.setBlindFailedPending(true)
            }
            val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            if (!enabled) { markHomeCard(); return@launch }
            if (!NoticePolicy.visible(NoticeKind.BLIND_SYSTEM_LIMIT, true, app.preferences.blindHintOff.first())) return@launch
            runCatching {
                channel(context)
                val start = PendingIntent.getActivity(context, 2, ShortcutTrampolineActivity.intent(context, "lifelog"),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val never = PendingIntent.getBroadcast(context, 3, Intent(context, BlindHintReceiver::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                NotificationManagerCompat.from(context).notify(ID_BLIND, NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notify_ceer).setContentTitle("录音没能在后台开始")
                    .setContentText("系统限制了后台启动。点这里马上开始随身录音。")
                    .setContentIntent(start).setAutoCancel(true)
                    .addAction(0, "开始录音", start).addAction(0, "以后不提示", never).build())
            }.onFailure { markHomeCard() }
        }
    }
    fun dismissBlind(context: Context) { runCatching { NotificationManagerCompat.from(context).cancel(ID_BLIND) } }

    /** ⑦: two short pulses, distinct from the single long pulse on success. */
    fun vibrateDoubleShort(context: Context) = runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java).defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 120, 80), -1))
    }
}

/** ⑦ "以后不提示". Not exported; only reachable through our own PendingIntent. */
class BlindHintReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = AppContainer.from(context)
        app.scope.launch { try { app.preferences.setBlindHintOff(true); EventNotifications.dismissBlind(context) } finally { pending.finish() } }
    }
}
