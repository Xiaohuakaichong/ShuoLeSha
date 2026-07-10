package com.example.shuolesa.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * PCM audio capture at 16kHz mono using AudioRecord.
 * Feeds raw PCM frames to a callback for encoding.
 */
class AudioRecorder {

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SIZE = 960 // 60ms at 16kHz (Opus optimal frame)
    }

    interface PcmCallback {
        fun onPcmData(buffer: ShortArray, readCount: Int)
    }

    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRecording = false
    @Volatile
    private var isPaused = false

    val recording: Boolean get() = isRecording
    val paused: Boolean get() = isPaused

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun init(): Boolean {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            return false
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            maxOf(bufferSize, FRAME_SIZE * 2 * 4) // at least 4 frames buffer
        )
        return audioRecord?.state == AudioRecord.STATE_INITIALIZED
    }

    /**
     * Start recording in a coroutine. Blocks until stopped.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun startRecording(callback: PcmCallback) = withContext(Dispatchers.IO) {
        val record = audioRecord ?: return@withContext
        val buffer = ShortArray(FRAME_SIZE)

        record.startRecording()
        isRecording = true
        isPaused = false

        try {
            while (isActive && isRecording) {
                if (isPaused) {
                    Thread.sleep(50)
                    continue
                }
                val readCount = record.read(buffer, 0, FRAME_SIZE)
                if (readCount > 0) {
                    callback.onPcmData(buffer, readCount)
                }
            }
        } finally {
            try {
                record.stop()
            } catch (_: IllegalStateException) { }
        }
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    fun stop() {
        isRecording = false
        isPaused = false
    }

    fun release() {
        stop()
        audioRecord?.release()
        audioRecord = null
    }
}
