package com.example.shuolesa.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.shuolesa.MainActivity
import com.example.shuolesa.service.EventNotifications
import com.example.shuolesa.service.ShortcutTrampolineActivity

/**
 * v5.0 home screen widgets. 录音条 follows the capture service; 待办清单 and 今日回顾 follow the database
 * (AppContainer watches it and calls [refreshData]). None of them records without the user: a tap on a record button goes
 * through ShortcutTrampolineActivity, which checks consent and the microphone first, exactly like the quick-settings tile.
 */
object Widgets {
    private val providers = listOf(RecordWidgetProvider::class.java, QuickWidgetProvider::class.java, TodoWidgetProvider::class.java, TodayWidgetProvider::class.java)
    private const val IMMUTABLE_UPDATE = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun ids(context: Context, provider: Class<*>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider))

    fun refreshAll(context: Context) { refreshRecording(context); refreshData(context) }
    fun refreshRecording(context: Context) = RecordWidgetProvider.refresh(context)
    /** 待办清单 + 今日回顾, after tasks or sessions changed. */
    fun refreshData(context: Context) { TodoWidgetProvider.refresh(context); TodayWidgetProvider.refresh(context) }
    fun hasAny(context: Context) = providers.any { ids(context, it).isNotEmpty() }

    /** Start a recording through the trampoline (consent + microphone checks), optionally in [mode]. */
    fun startRecording(context: Context, code: Int, mode: String? = null): PendingIntent =
        PendingIntent.getActivity(context, code, ShortcutTrampolineActivity.intent(context, mode), IMMUTABLE_UPDATE)

    /** Open the app on [action] (OPEN_TODAY / OPEN_TASKS / OPEN_SEARCH), or just the home screen when null. */
    fun openApp(context: Context, code: Int, action: String? = null): PendingIntent =
        PendingIntent.getActivity(context, code, Intent(context, MainActivity::class.java).setAction(action ?: Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), IMMUTABLE_UPDATE)

    fun service(context: Context, code: Int, service: Class<*>, action: String): PendingIntent =
        PendingIntent.getService(context, code, Intent(context, service).setAction(action), IMMUTABLE_UPDATE)

    val openToday get() = MainActivity.ACTION_OPEN_TODAY
    val openTasks get() = EventNotifications.ACTION_OPEN_TASKS
    val openSearch get() = MainActivity.ACTION_OPEN_SEARCH
}
