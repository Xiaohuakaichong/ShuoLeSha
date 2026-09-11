package com.example.shuolesa.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_records")
data class AudioRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val chunkIndex: Int,
    val filePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val createdAt: Long,
    val status: String = STATUS_PENDING,
    val uploadedAt: Long? = null,
    val retryCount: Int = 0,
    val title: String? = null,
    val summary: String? = null,
    val actionItems: String? = null,
    val tags: String? = null,
    val transcription: String? = null,
    val agentResult: String? = null,
    val recordingMode: String? = "lifelog",
    val audioFormat: String? = null,
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_UPLOADING = "UPLOADING"
        const val STATUS_UPLOADED = "UPLOADED"
        const val STATUS_FAILED = "FAILED"
    }

    fun isMeeting(): Boolean {
        if (recordingMode == "meeting") return true
        if (recordingMode == "lifelog") return false
        if (filePath.endsWith(".wav", ignoreCase = true)) return true
        if (tags?.contains("会议") == true) return true
        if (title?.contains("会议") == true) return true
        return false
    }

    fun isDailyLifeLogSummary(): Boolean {
        return tags?.contains("每日复盘") == true || sessionId.startsWith("lifelog_")
    }

    fun getDisplayModeLabel(): String {
        return when {
            isDailyLifeLogSummary() -> "复盘"
            isMeeting() -> "会议"
            else -> "随身"
        }
    }

    fun getDisplayModeTag(): String = getDisplayModeLabel()

    fun getDisplayFormatInfo(): String {
        val ext = if (filePath.isNotBlank()) java.io.File(filePath).extension.lowercase() else ""
        return when {
            audioFormat == "wav" || ext == "wav" -> "无损 WAV · 16kHz"
            audioFormat == "aac_64k" -> "高清 AAC · 64kbps"
            audioFormat == "aac_32k" -> "清晰 AAC · 32kbps"
            audioFormat == "aac_24k" -> "标准 AAC · 24kbps"
            audioFormat == "aac_16k" -> "极省 AAC · 16kbps"
            ext == "aac" -> if (isMeeting()) "高清 AAC · 64kbps" else "压缩 AAC · 省流"
            ext.isNotBlank() -> ext.uppercase()
            else -> if (isMeeting()) "高保真音频" else "省流音频"
        }
    }

    fun getEstimatedBitrateDesc(): String {
        return when {
            audioFormat == "wav" || filePath.endsWith(".wav", ignoreCase = true) -> "高保真未压缩 (~115MB/h)"
            audioFormat == "aac_64k" -> "高清压缩 (~28MB/h)"
            audioFormat == "aac_32k" -> "清晰压缩 (~14MB/h)"
            audioFormat == "aac_24k" -> "省流轻量 (~10MB/h)"
            audioFormat == "aac_16k" -> "极小体积 (~7MB/h)"
            else -> if (isMeeting()) "会议高保真" else "随身轻量"
        }
    }
}
