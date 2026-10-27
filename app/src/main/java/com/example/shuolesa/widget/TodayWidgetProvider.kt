package com.example.shuolesa.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.example.shuolesa.AppContainer
import com.example.shuolesa.R
import com.example.shuolesa.ui.clock
import com.example.shuolesa.ui.duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 今日回顾 (4x2): how many records and how long today, then the day's 复盘 if there is one, otherwise the latest record.
 * Everything is read from the local database; tapping it opens the 今日 tab.
 */
class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { render(context, ids) }

    companion object {
        fun refresh(context: Context) {
            val ids = Widgets.ids(context, TodayWidgetProvider::class.java)
            if (ids.isNotEmpty()) render(context, ids)
        }

        private fun render(context: Context, ids: IntArray) {
            val app = AppContainer.from(context)
            app.scope.launch {
                app.awaitReady()
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
                val sessions = app.repository.dao.observeSessions().first().filter { it.startedAt >= start && it.startedAt < start + 86_400_000 }
                val digest = app.repository.dao.observeDigests().first().find { it.date == today.toString() }
                val latest = sessions.firstOrNull() // ordered newest first
                val views = RemoteViews(context.packageName, R.layout.widget_today)
                views.setImageViewResource(R.id.today_spirit, when { digest != null -> R.drawable.ic_spirit_happy; sessions.isEmpty() -> R.drawable.ic_spirit_sleep; else -> R.drawable.ic_spirit_idle })
                views.setTextViewText(R.id.today_date, today.format(DateTimeFormatter.ofPattern("M月d日")))
                views.setTextViewText(R.id.today_stat, if (sessions.isEmpty()) "今天还没有记录" else "${sessions.size} 条 · ${sessions.sumOf { it.durationMs } / 60_000} 分钟")
                val title: String?
                val body: String?
                when {
                    digest != null -> { title = "今日复盘"; body = digest.content }
                    latest != null -> { title = latest.title; body = "${clock(latest.startedAt)} · ${if (latest.mode == "meeting") "会议" else "随身"} · ${duration(latest.durationMs)}" }
                    else -> { title = null; body = "点右下角的录音键，说过的话会留在这里。" }
                }
                views.setViewVisibility(R.id.today_title, if (title != null) View.VISIBLE else View.GONE)
                if (title != null) views.setTextViewText(R.id.today_title, title)
                views.setTextViewText(R.id.today_body, body)
                views.setOnClickPendingIntent(android.R.id.background, Widgets.openApp(context, 400, Widgets.openToday))
                AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
            }
        }
    }
}
