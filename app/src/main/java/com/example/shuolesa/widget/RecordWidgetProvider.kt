package com.example.shuolesa.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.example.shuolesa.R
import com.example.shuolesa.audio.CapturePhase
import com.example.shuolesa.service.AudioCaptureService

/**
 * 录音条 (4x1). Idle: one orb that starts a recording. Recording: the orb turns red and stops it, the timer runs by itself
 * (Chronometer, no per-second updates), and pause / mark sit beside it. Updated on every capture phase change.
 */
class RecordWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { manager.updateAppWidget(ids, build(context)) }

    companion object {
        fun refresh(context: Context) {
            val ids = Widgets.ids(context, RecordWidgetProvider::class.java)
            if (ids.isNotEmpty()) AppWidgetManager.getInstance(context).updateAppWidget(ids, build(context))
        }

        private fun build(context: Context): RemoteViews {
            val state = AudioCaptureService.state.value
            val views = RemoteViews(context.packageName, R.layout.widget_record)
            if (!state.active) {
                views.setImageViewResource(R.id.rec_orb_bg, R.drawable.widget_orb_mint)
                views.setViewVisibility(R.id.rec_orb_spirit, View.VISIBLE)
                views.setViewVisibility(R.id.rec_orb_icon, View.GONE)
                views.setTextViewText(R.id.rec_title, "开始录音")
                views.setTextViewText(R.id.rec_sub, "点一下，随身记录")
                views.setViewVisibility(R.id.rec_sub, View.VISIBLE)
                views.setViewVisibility(R.id.rec_timer, View.GONE)
                views.setViewVisibility(R.id.rec_pause, View.GONE)
                views.setViewVisibility(R.id.rec_mark, View.GONE)
                val start = Widgets.startRecording(context, 100)
                views.setOnClickPendingIntent(R.id.rec_orb, start)
                views.setOnClickPendingIntent(R.id.rec_texts, start)
                return views
            }
            val paused = state.phase == CapturePhase.PAUSED
            views.setImageViewResource(R.id.rec_orb_bg, R.drawable.widget_orb_record)
            views.setViewVisibility(R.id.rec_orb_spirit, View.GONE)
            views.setViewVisibility(R.id.rec_orb_icon, View.VISIBLE)
            views.setImageViewResource(R.id.rec_orb_icon, R.drawable.ic_w_stop_on_record)
            views.setTextViewText(R.id.rec_title, "${state.modeLabel}${if (paused) "已暂停" else "录音中"}")
            views.setViewVisibility(R.id.rec_sub, View.GONE)
            views.setViewVisibility(R.id.rec_timer, View.VISIBLE)
            views.setChronometer(R.id.rec_timer, SystemClock.elapsedRealtime() - state.durationMs, null, !paused && state.phase == CapturePhase.RECORDING)
            views.setViewVisibility(R.id.rec_pause, View.VISIBLE)
            views.setViewVisibility(R.id.rec_mark, View.VISIBLE)
            views.setImageViewResource(R.id.rec_pause, if (paused) R.drawable.ic_w_play else R.drawable.ic_w_pause)
            views.setContentDescription(R.id.rec_pause, if (paused) "继续录音" else "暂停录音")
            views.setOnClickPendingIntent(R.id.rec_orb, Widgets.service(context, 101, AudioCaptureService::class.java, AudioCaptureService.ACTION_STOP))
            views.setOnClickPendingIntent(R.id.rec_pause, Widgets.service(context, 102, AudioCaptureService::class.java,
                if (paused) AudioCaptureService.ACTION_RESUME else AudioCaptureService.ACTION_PAUSE))
            views.setOnClickPendingIntent(R.id.rec_mark, Widgets.service(context, 103, AudioCaptureService::class.java, AudioCaptureService.ACTION_MARK))
            views.setOnClickPendingIntent(R.id.rec_texts, Widgets.openApp(context, 104))
            return views
        }
    }
}
