package com.example.shuolesa.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * v5.0: self-check for the recording 实时活动 (OPPO 流体云 / Android 16 Live Updates).
 * The recording notification asks to be promoted ([NotificationHelper.buildRecordingNotification]), but the system only
 * does it when (a) the notification has the promotable shape and (b) the app's 「实时活动」 switch is on. Either can be
 * the reason the capsule is missing, so 设置 › 录音与快捷启动 shows which one, opens the right page, and can post a sample.
 */
object LiveActivity {
    data class State(val title: String, val hint: String, val canOpenSettings: Boolean)
    private const val SAMPLE_ID = 1002

    fun state(context: Context): State {
        if (Build.VERSION.SDK_INT < 36) return State("这个系统版本不支持", "实时活动需要 Android 16 / ColorOS 16 及以上，录音通知仍会正常显示。", false)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled())
            return State("通知已关闭", "先允许说了啥发通知，录音状态才会出现在顶部。", true)
        if (!manager.canPostPromotedNotifications())
            return State("系统里的「实时活动」开关是关的", "打开后，录音计时会收进流体云。路径：设置 › 通知与状态栏 › 说了啥 › 实时活动。", true)
        val promotable = NotificationHelper(context).buildRecordingNotification(System.currentTimeMillis(), "随身").hasPromotableCharacteristics()
        if (!promotable) return State("录音通知不满足实时活动条件", "请把这条反馈给开发者，并附上手机型号和系统版本。", false)
        return State("已允许", "录音时，计时会出现在流体云。没出现的话点「试一下」，10 秒内看屏幕顶部。", true)
    }

    /** The per-app 实时活动 page on Android 16, or the app's notification page when that is missing. */
    fun openSettings(context: Context) {
        val promotion = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        listOf(promotion, fallback).firstOrNull { intent ->
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
    }

    /** A recording-shaped notification for a few seconds, without touching the microphone. */
    fun postSample(context: Context) {
        val notification = NotificationHelper(context).buildRecordingNotification(System.currentTimeMillis(), "试一下")
        runCatching { context.getSystemService(NotificationManager::class.java).notify(SAMPLE_ID, notification) }
    }

    fun clearSample(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java).cancel(SAMPLE_ID) }
    }
}
