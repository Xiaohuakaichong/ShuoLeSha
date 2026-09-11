package com.example.shuolesa.audio

import android.content.Context
import android.util.Log
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.repository.AudioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * 管理 5 分钟标准 WAV 音频分片。
 * 接收麦克风 16kHz PCM 数据，写入高保真无损 WAV 文件。
 */
class ChunkManager(
    private val context: Context,
    private val repository: AudioRepository,
    private val scope: CoroutineScope,
    private val recordingMode: String = "lifelog",
    private val lifelogBitrateKbps: Int = 24,
    private val meetingFormat: String = "wav",
) {

    companion object {
        private const val TAG = "ChunkManager"
        const val CHUNK_DURATION_MS = 5 * 60 * 1000L // 5 minutes
        const val FRAME_DURATION_MS = 60L // 60ms per frame
    }

    private val sessionId = UUID.randomUUID().toString().take(8)
    private var chunkIndex = 0
    private var currentWavWriter: WavWriter? = null
    private var currentAacWriter: AacWriter? = null
    private var currentFile: File? = null
    private var chunkStartTime = 0L
    private var frameCount = 0L
    private var totalRecordingStartTime = 0L

    val currentChunkIndex: Int get() = chunkIndex
    val elapsedMs: Long get() {
        return if (totalRecordingStartTime > 0) {
            System.currentTimeMillis() - totalRecordingStartTime
        } else 0
    }

    fun startSession() {
        totalRecordingStartTime = System.currentTimeMillis()
        openNewChunk()
    }

    /**
     * 接收一帧 PCM 数据
     */
    fun onPcmFrame(buffer: ShortArray, readCount: Int) {
        currentWavWriter?.write(buffer, readCount)
        currentAacWriter?.write(buffer, readCount)
        frameCount++
        val elapsedInChunk = frameCount * FRAME_DURATION_MS

        if (elapsedInChunk >= CHUNK_DURATION_MS) {
            rotateChunk()
        }
    }

    private fun rotateChunk() {
        val oldFile = currentFile
        val oldStartTime = chunkStartTime
        val durationMs = frameCount * FRAME_DURATION_MS
        val bytesWritten = currentWavWriter?.finish() ?: currentAacWriter?.finish() ?: 0L

        if (oldFile != null) {
            registerChunk(oldFile, chunkIndex - 1, oldStartTime, durationMs, bytesWritten)
        }

        openNewChunk()
    }

    private fun openNewChunk() {
        val audioDir = File(context.filesDir, "recordings").apply { mkdirs() }
        val timestamp = System.currentTimeMillis()

        val isAac = when {
            recordingMode == "lifelog" -> true
            recordingMode == "meeting" && meetingFormat == "aac_64k" -> true
            else -> false
        }

        val ext = if (isAac) "aac" else "wav"
        val fileName = "${timestamp}_${sessionId}_chunk${String.format("%03d", chunkIndex)}.$ext"
        val file = File(audioDir, fileName)

        currentFile = file
        chunkStartTime = timestamp
        frameCount = 0
        chunkIndex++

        if (isAac) {
            val bps = if (recordingMode == "meeting") 64000 else (lifelogBitrateKbps * 1000).coerceIn(16000, 64000)
            currentAacWriter = AacWriter(file, sampleRate = 16000, bitRate = bps)
            currentWavWriter = null
        } else {
            currentWavWriter = WavWriter(file)
            currentAacWriter = null
        }
    }

    fun finalizeSession() {
        val file = currentFile ?: return
        val durationMs = frameCount * FRAME_DURATION_MS
        val bytesWritten = currentWavWriter?.finish() ?: currentAacWriter?.finish() ?: 0L

        registerChunk(file, chunkIndex - 1, chunkStartTime, durationMs, bytesWritten)

        currentFile = null
        currentWavWriter = null
        currentAacWriter = null
        totalRecordingStartTime = 0
    }

    private fun registerChunk(file: File, index: Int, startTime: Long, durationMs: Long, sizeBytes: Long) {
        val fileSize = if (file.exists()) file.length() else sizeBytes
        val isMeeting = recordingMode == "meeting"
        val fmt = if (isMeeting) meetingFormat else "aac_${lifelogBitrateKbps}k"
        val initialTitle = if (isMeeting) "会议录音 #${index + 1}" else "随身生活记录 #${index + 1}"
        val initialTags = if (isMeeting) "[\"会议\"]" else "[\"LifeLog\"]"

        val record = AudioRecordEntity(
            sessionId = sessionId,
            chunkIndex = index,
            filePath = file.absolutePath,
            durationMs = durationMs,
            fileSizeBytes = fileSize,
            createdAt = startTime,
            recordingMode = recordingMode,
            audioFormat = fmt,
            title = initialTitle,
            tags = initialTags,
        )
        scope.launch {
            try {
                repository.insertRecord(record)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register chunk in DB", e)
            }
        }
    }
}
