package com.example.shuolesa.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 将 16kHz PCM 数据实时编码为带 ADTS 头的标准 AAC 音频文件 (.aac)。
 * 纯硬件级编码，体积极小（16kbps~32kbps 相比无损 WAV 缩减 85%~94%），
 * 兼容 StepFun、SiliconFlow、Whisper 及全平台播放器。
 */
class AacWriter(
    val file: File,
    private val sampleRate: Int = 16000,
    private val bitRate: Int = 24000,
) {

    companion object {
        private const val TAG = "AacWriter"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC
        private const val TIMEOUT_US = 8000L
        private const val ADTS_HEADER_SIZE = 7
    }

    private var codec: MediaCodec? = null
    private var outputStream: FileOutputStream? = null
    private var isInitialized = false
    private var presentationTimeUs = 0L

    init {
        try {
            outputStream = FileOutputStream(file)
            val format = MediaFormat.createAudioFormat(MIME_TYPE, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)
            }
            val mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mediaCodec.start()
            codec = mediaCodec
            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AAC encoder", e)
            isInitialized = false
        }
    }

    /**
     * 写入 PCM 采样数据
     */
    fun write(pcmData: ShortArray, count: Int) {
        val mediaCodec = codec ?: return
        if (!isInitialized) return

        try {
            val byteCount = count * 2
            val byteBuffer = ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN)
            byteBuffer.asShortBuffer().put(pcmData, 0, count)
            val bytes = byteBuffer.array()

            var bytesOffset = 0
            while (bytesOffset < bytes.size) {
                val inputIndex = mediaCodec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuf = mediaCodec.getInputBuffer(inputIndex) ?: break
                    inputBuf.clear()
                    val toCopy = minOf(bytes.size - bytesOffset, inputBuf.remaining())
                    inputBuf.put(bytes, bytesOffset, toCopy)
                    bytesOffset += toCopy

                    val durationUs = (toCopy / 2 * 1_000_000L) / sampleRate
                    mediaCodec.queueInputBuffer(inputIndex, 0, toCopy, presentationTimeUs, 0)
                    presentationTimeUs += durationUs
                }
                drainOutput(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding PCM frame to AAC", e)
        }
    }

    private fun drainOutput(isEndOfStream: Boolean) {
        val mediaCodec = codec ?: return
        val outStream = outputStream ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!isEndOfStream) break
            } else if (outputIndex >= 0) {
                val outputBuf = mediaCodec.getOutputBuffer(outputIndex)
                if (outputBuf != null && bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    val packetLen = bufferInfo.size + ADTS_HEADER_SIZE
                    val packet = ByteArray(packetLen)
                    addAdtsHeader(packet, packetLen, sampleRate, 1)

                    outputBuf.position(bufferInfo.offset)
                    outputBuf.limit(bufferInfo.offset + bufferInfo.size)
                    outputBuf.get(packet, ADTS_HEADER_SIZE, bufferInfo.size)

                    outStream.write(packet)
                }
                mediaCodec.releaseOutputBuffer(outputIndex, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    /**
     * 为 AAC 裸流数据包构建 7 字节 ADTS 头
     */
    private fun addAdtsHeader(packet: ByteArray, packetLen: Int, sampleRate: Int, channels: Int) {
        val profile = 2 // AAC-LC (2)
        val freqIdx = when (sampleRate) {
            96000 -> 0
            88200 -> 1
            64000 -> 2
            48000 -> 3
            44100 -> 4
            32000 -> 5
            24000 -> 6
            22050 -> 7
            16000 -> 8
            12000 -> 9
            11025 -> 10
            8000 -> 11
            else -> 8
        }
        val chanCfg = channels // 1

        packet[0] = 0xFF.toByte()
        packet[1] = 0xF9.toByte() // 0xFFF + MPEG-4 (0) + Layer 00 + No CRC (1)
        packet[2] = (((profile - 1) shl 6) + (freqIdx shl 2) + (chanCfg shr 2)).toByte()
        packet[3] = (((chanCfg and 3) shl 6) + (packetLen shr 11)).toByte()
        packet[4] = ((packetLen and 0x7FF) shr 3).toByte()
        packet[5] = (((packetLen and 7) shl 5) + 0x1F).toByte()
        packet[6] = 0xFC.toByte()
    }

    /**
     * 结束编码并关闭文件
     */
    fun finish(): Long {
        try {
            codec?.let { mediaCodec ->
                val inputIndex = mediaCodec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    mediaCodec.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                drainOutput(true)
                mediaCodec.stop()
                mediaCodec.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finishing AAC encoder", e)
        } finally {
            codec = null
            try {
                outputStream?.flush()
                outputStream?.close()
            } catch (_: Exception) {}
            outputStream = null
        }
        return file.length()
    }
}
