package com.example.shuolesa.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.example.shuolesa.R

/** 快捷入口 (2x2): 随身 · 会议 start a recording in that mode; 搜索 · 待办 open the app there. Static, nothing to refresh. */
class QuickWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_quick)
        views.setOnClickPendingIntent(R.id.quick_life, Widgets.startRecording(context, 200, "lifelog"))
        views.setOnClickPendingIntent(R.id.quick_meeting, Widgets.startRecording(context, 201, "meeting"))
        views.setOnClickPendingIntent(R.id.quick_search, Widgets.openApp(context, 202, Widgets.openSearch))
        views.setOnClickPendingIntent(R.id.quick_tasks, Widgets.openApp(context, 203, Widgets.openTasks))
        manager.updateAppWidget(ids, views)
    }
}
