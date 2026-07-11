package com.example.shuolesa.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {

    private val listDateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    private val detailDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun formatListDate(epochMs: Long): String = listDateFormat.format(Date(epochMs))

    fun formatDetailDate(epochMs: Long): String = detailDateFormat.format(Date(epochMs))

    /** Elapsed / seek time: mm:ss or h:mm:ss */
    fun formatElapsed(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    /** Short duration label for list rows */
    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> String.format(Locale.getDefault(), "%dh %02dm", hours, minutes)
            minutes > 0 -> String.format(Locale.getDefault(), "%dm %02ds", minutes, seconds)
            else -> String.format(Locale.getDefault(), "%ds", seconds)
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.0fKB", bytes / 1024.0)
            else -> String.format(Locale.getDefault(), "%.1fMB", bytes / (1024.0 * 1024.0))
        }
    }
}
