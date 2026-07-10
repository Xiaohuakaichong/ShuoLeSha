package com.example.shuolesa.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Opus encoder using Android MediaCodec.
 * Takes PCM 16kHz mono frames and outputs Opus-encoded data.
 */
class OpusEncoder {

    companion object {
        private const val TAG = "OpusEncoder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_OPUS
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_COUNT = 1
        private const val BIT_RATE = 24000 // 24kbps
        private const val CODEC_TIMEOUT_US = 10000L
    }

    private var codec: MediaCodec? = null
    private var outputStream: OutputStream? = null
    private var isStarted = false
    private var totalBytesWritten = 0L

    /**
     * Initialize the Opus encoder.
     * Returns false if Opus encoding is not supported on this device.
     */
    fun init(): Boolean {
        return try {
            val format = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, CHANNEL_COUNT).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AudioRecorder.FRAME_SIZE * 2) // 16-bit samples
            }

            codec = MediaCodec.createEncoderByType(MIME_TYPE)
            codec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init Opus encoder", e)
            false
        }
    }

    /**
     * Start encoding to the given output stream.
     */
    fun start(output: OutputStream) {
        this.outputStream = output
        this.totalBytesWritten = 0
        codec?.start()
        isStarted = true
    }

    /**
     * Feed a PCM frame to the encoder.
     */
    fun encode(pcmData: ShortArray, count: Int) {
        val codec = codec ?: return
        if (!isStarted) return

        // Feed input
        val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
        if (inputIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            // Convert ShortArray to ByteBuffer (little-endian PCM16)
            val byteBuffer = ByteBuffer.allocate(count * 2)
            byteBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            byteBuffer.asShortBuffer().put(pcmData, 0, count)
            inputBuffer.put(byteBuffer.array(), 0, count * 2)
            codec.queueInputBuffer(inputIndex, 0, count * 2, 0, 0)
        }

        // Drain output
        drainOutput()
    }

    /**
     * Drain encoded output from the codec.
     */
    private fun drainOutput() {
        val codec = codec ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
            if (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex) ?: break
                if (bufferInfo.size > 0) {
                    val data = ByteArray(bufferInfo.size)
                    outputBuffer.get(data)
                    // Write raw Opus packets with simple length-prefixed framing
                    // Format: [4-byte LE length][opus data]
                    val lenBytes = ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        .putInt(data.size).array()
                    outputStream?.write(lenBytes)
                    outputStream?.write(data)
                    totalBytesWritten += 4 + data.size
                }
                codec.releaseOutputBuffer(outputIndex, false)
            } else {
                break
            }
        }
    }

    /**
     * Flush the encoder and switch output stream (for chunking).
     * Returns bytes written to the previous stream.
     */
    fun switchOutput(newOutput: OutputStream): Long {
        val bytesWritten = totalBytesWritten
        // Flush current output
        drainOutput()
        outputStream?.flush()
        // Switch
        outputStream = newOutput
        totalBytesWritten = 0
        return bytesWritten
    }

    /**
     * Stop encoding and flush remaining data.
     */
    fun stop(): Long {
        if (!isStarted) return 0
        isStarted = false

        try {
            // Signal end of stream
            val codec = codec ?: return totalBytesWritten
            val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (inputIndex >= 0) {
                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drainOutput()
            outputStream?.flush()
            codec.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping encoder", e)
        }

        return totalBytesWritten
    }

    fun release() {
        stop()
        codec?.release()
        codec = null
        outputStream = null
    }
}
