package com.example.shuolesa.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.shuolesa.AppContainer
import com.example.shuolesa.R
import com.example.shuolesa.data.db.TaskEntity
import com.example.shuolesa.data.model.TaskState
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 待办清单 (4x3). Lists the confirmed 待办 (ACTIVE), newest first; tapping a row checks it off (-> DONE) and it leaves the
 * list. 候选 are not listed — confirming them stays in the app, where the quoted source is one tap away; the footer
 * says how many are waiting.
 */
class TodoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { render(context, ids) }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) { super.onReceive(context, intent); return }
        val id = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val pending = goAsync()
        val app = AppContainer.from(context)
        app.scope.launch {
            try {
                app.awaitReady()
                if (app.repository.dao.transitionTask(id, TaskState.ACTIVE, TaskState.DONE) == 1) Widgets.refreshData(context)
            } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.example.shuolesa.widget.TOGGLE_TASK"
        const val EXTRA_TASK_ID = "task_id"

        fun refresh(context: Context) {
            val ids = Widgets.ids(context, TodoWidgetProvider::class.java)
            if (ids.isNotEmpty()) render(context, ids)
        }

        private fun render(context: Context, ids: IntArray) {
            val app = AppContainer.from(context)
            app.scope.launch {
                app.awaitReady()
                val active = app.repository.dao.taskCount(TaskState.ACTIVE)
                val candidates = app.repository.dao.taskCount(TaskState.CANDIDATE)
                val manager = AppWidgetManager.getInstance(context)
                ids.forEach { manager.updateAppWidget(it, views(context, it, active, candidates)) }
                manager.notifyAppWidgetViewDataChanged(ids, R.id.todo_list)
            }
        }

        private fun views(context: Context, id: Int, active: Int, candidates: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_todo)
            views.setTextViewText(R.id.todo_count, active.toString())
            val adapter = Intent(context, TodoWidgetService::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            adapter.data = Uri.parse(adapter.toUri(Intent.URI_INTENT_SCHEME))
            views.setRemoteAdapter(R.id.todo_list, adapter)
            views.setEmptyView(R.id.todo_list, R.id.todo_empty)
            views.setTextViewText(R.id.todo_empty, if (candidates > 0) "没有待办，有 $candidates 项等你确认" else "待办都处理好了")
            // Fill-in intents carry the task id; the template has to be mutable for that.
            views.setPendingIntentTemplate(R.id.todo_list, PendingIntent.getBroadcast(context, 300,
                Intent(context, TodoWidgetProvider::class.java).setAction(ACTION_TOGGLE), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            views.setTextViewText(R.id.todo_footer, if (candidates > 0) "$candidates 项待确认 ›" else "查看全部 ›")
            views.setOnClickPendingIntent(R.id.todo_title, Widgets.openApp(context, 301, Widgets.openTasks))
            views.setOnClickPendingIntent(R.id.todo_footer, Widgets.openApp(context, 302, Widgets.openTasks))
            views.setOnClickPendingIntent(R.id.todo_record, Widgets.startRecording(context, 303))
            return views
        }
    }
}

class TodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val context: Context) : RemoteViewsFactory {
        private var items: List<TaskEntity> = emptyList()
        override fun onCreate() {}
        // Runs on a binder thread; blocking here is what the framework expects.
        override fun onDataSetChanged() {
            items = runBlocking {
                withTimeoutOrNull(5_000) {
                    val app = AppContainer.from(context)
                    app.awaitReady()
                    app.repository.dao.tasksByState(TaskState.ACTIVE, 30)
                }
            } ?: emptyList()
        }
        override fun onDestroy() { items = emptyList() }
        override fun getCount() = items.size
        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_todo_item)
            val task = items.getOrNull(position) ?: return views
            views.setTextViewText(R.id.todo_text, task.text)
            val hint = task.whenHint?.takeIf { it.isNotBlank() }
            views.setViewVisibility(R.id.todo_hint, if (hint != null) android.view.View.VISIBLE else android.view.View.GONE)
            if (hint != null) views.setTextViewText(R.id.todo_hint, "时间提示：$hint")
            views.setOnClickFillInIntent(R.id.todo_row, Intent().putExtra(TodoWidgetProvider.EXTRA_TASK_ID, task.id))
            return views
        }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = items.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
        override fun hasStableIds() = true
    }
}
