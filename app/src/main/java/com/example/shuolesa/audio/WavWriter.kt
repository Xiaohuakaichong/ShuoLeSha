package com.example.shuolesa.audio

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 写入标准 16kHz 16-bit 单声道 WAV 音频文件。
 * 所有主流 ASR 引擎（阶跃星辰、硅基流动、Whisper）与系统播放器 100% 原生支持。
 */
class WavWriter(val file: File) {

    companion object {
        private const val TAG = "WavWriter"
        const val SAMPLE_RATE = 16000
        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
        const val HEADER_SIZE = 44
    }

    private val outputStream: FileOutputStream = FileOutputStream(file)
    private var payloadBytes: Long = 0

    init {
        // 预留 44 字节 WAV 文件头空间
        outputStream.write(ByteArray(HEADER_SIZE))
    }

    fun write(pcmData: ShortArray, count: Int) {
        val byteCount = count * 2
        val byteBuffer = ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.asShortBuffer().put(pcmData, 0, count)
        outputStream.write(byteBuffer.array(), 0, byteCount)
        payloadBytes += byteCount
    }

    fun finish(): Long {
        try {
            outputStream.flush()
            outputStream.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing stream", e)
        }

        // 将最终计算出的正确数据长度写入文件头
        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)
                raf.write(buildWavHeader(payloadBytes, SAMPLE_RATE, CHANNELS, BITS_PER_SAMPLE))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing WAV header", e)
        }

        return file.length()
    }

    private fun buildWavHeader(
        payloadSize: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
    ): ByteArray {
        val totalDataLen = payloadSize + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        val header = ByteArray(HEADER_SIZE)
        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII))
        bb.putInt(totalDataLen.toInt())
        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII))
        bb.putInt(16) // Subchunk1Size for PCM
        bb.putShort(1.toShort()) // AudioFormat 1 = PCM
        bb.putShort(channels.toShort())
        bb.putInt(sampleRate)
        bb.putInt(byteRate)
        bb.putShort(blockAlign.toShort())
        bb.putShort(bitsPerSample.toShort())
        bb.put("data".toByteArray(Charsets.US_ASCII))
        bb.putInt(payloadSize.toInt())
        return header
    }
}
