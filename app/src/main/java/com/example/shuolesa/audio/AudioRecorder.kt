package com.example.shuolesa.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.IOException

/** One owner consumes PCM. stop() unblocks the native read; release follows join(). */
class AudioRecorder {
    companion object {
        const val SAMPLE_RATE = 16000
        const val FRAME_SIZE = 960
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
    private var record: AudioRecord? = null
    @Volatile private var stopping = false
    private var started = false

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun init(): Boolean {
        val size = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (size <= 0) return false
        record = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG,
            AUDIO_FORMAT, maxOf(size, FRAME_SIZE * 8))
        return record?.state == AudioRecord.STATE_INITIALIZED
    }
    @Synchronized private fun startNative(): Boolean {
        if (stopping) return false
        record?.startRecording() ?: return false
        started = true
        return true
    }
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun capture(onFrame: suspend (ShortArray, Int) -> Unit) = withContext(Dispatchers.IO) {
        val native = record ?: throw IOException("麦克风尚未初始化")
        if (!startNative()) return@withContext
        val pcm = ShortArray(FRAME_SIZE)
        try {
            while (currentCoroutineContext().isActive && !stopping) {
                val count = native.read(pcm, 0, pcm.size, AudioRecord.READ_NON_BLOCKING)
                if (count > 0) onFrame(pcm, count)
                else if (!stopping && count < 0) throw IOException("麦克风采集中断 ($count)")
                else if(count==0) delay(10)
            }
        } finally { stop() }
    }
    @Synchronized fun stop() {
        stopping = true
        if (started) runCatching { record?.stop() }
        started = false
    }
    @Synchronized fun release() {
        stop()
        record?.release()
        record = null
    }
}
