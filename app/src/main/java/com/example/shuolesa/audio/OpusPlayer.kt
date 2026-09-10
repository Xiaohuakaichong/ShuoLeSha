package com.example.shuolesa.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Audio player supporting standard WAV playback via MediaPlayer,
 * with fallback to length-prefixed Opus decoding via MediaCodec.
 */
class OpusPlayer(private val context: Context) {
    companion object {
        private const val TAG = "OpusPlayer"
        private const val SAMPLE_RATE = 16000
    }

    data class OpusPacketIndex(
        val fileOffset: Long,
        val packetSize: Int,
        val timeMs: Long
    )

    private var isWavFormat = false
    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && !isPaused && isWavFormat && mediaPlayer != null) {
                try {
                    val pos = mediaPlayer?.currentPosition?.toLong() ?: 0L
                    currentPositionMs = pos
                    onProgressUpdate?.invoke(pos)
                    mainHandler.postDelayed(this, 50)
                } catch (_: Exception) {}
            }
        }
    }

    private var audioTrack: AudioTrack? = null
    private var decoder: MediaCodec? = null
    private var playThread: Thread? = null

    @Volatile
    private var isPlaying = false
    @Volatile
    private var isPaused = false
    @Volatile
    private var currentPositionMs = 0L
    @Volatile
    private var seekTargetMs = -1L

    private val indexList = mutableListOf<OpusPacketIndex>()
    private var fileBytes = ByteArray(0)
    private var durationMs = 0L

    var onProgressUpdate: ((Long) -> Unit)? = null
    var onCompletion: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun prepare(filePath: String): Boolean {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                onError?.invoke("文件不存在: ${file.name}")
                return false
            }

            val isWav = filePath.endsWith(".wav", ignoreCase = true) || run {
                if (file.length() >= 4) {
                    val header = ByteArray(4)
                    file.inputStream().use { it.read(header) }
                    header.contentEquals("RIFF".toByteArray())
                } else false
            }

            if (isWav) {
                isWavFormat = true
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(filePath)
                    prepare()
                    setOnCompletionListener {
                        this@OpusPlayer.isPlaying = false
                        mainHandler.removeCallbacks(progressRunnable)
                        currentPositionMs = duration.toLong()
                        onProgressUpdate?.invoke(currentPositionMs)
                        onCompletion?.invoke()
                    }
                    setOnErrorListener { _, what, extra ->
                        this@OpusPlayer.isPlaying = false
                        mainHandler.removeCallbacks(progressRunnable)
                        onError?.invoke("播放失败 ($what, $extra)")
                        true
                    }
                }
                durationMs = mediaPlayer?.duration?.toLong() ?: 0L
                currentPositionMs = 0L
                return true
            }

            isWavFormat = false
            fileBytes = file.readBytes()
            indexList.clear()

            val buffer = ByteBuffer.wrap(fileBytes).order(ByteOrder.LITTLE_ENDIAN)
            var currentTimeMs = 0L
            while (buffer.remaining() >= 4) {
                val size = buffer.getInt()
                if (size <= 0 || buffer.remaining() < size) {
                    break
                }
                val offset = buffer.position().toLong()
                indexList.add(OpusPacketIndex(offset, size, currentTimeMs))
                buffer.position(buffer.position() + size)
                currentTimeMs += 20 // 20ms per Opus packet
            }

            durationMs = currentTimeMs
            currentPositionMs = 0L
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare player", e)
            onError?.invoke("加载失败: ${e.message}")
            return false
        }
    }

    fun getDurationMs(): Long = durationMs

    fun getCurrentPositionMs(): Long = currentPositionMs

    fun isPlaying(): Boolean = isPlaying && !isPaused

    fun start() {
        if (isWavFormat) {
            val mp = mediaPlayer ?: return
            if (isPlaying) {
                if (isPaused) {
                    isPaused = false
                    try {
                        mp.start()
                        mainHandler.post(progressRunnable)
                    } catch (_: Exception) {}
                }
                return
            }
            isPlaying = true
            isPaused = false
            try {
                mp.start()
                mainHandler.post(progressRunnable)
            } catch (e: Exception) {
                Log.e(TAG, "MediaPlayer start failed", e)
                onError?.invoke("播放失败: ${e.message}")
            }
            return
        }

        if (isPlaying) {
            if (isPaused) {
                isPaused = false
                try {
                    audioTrack?.play()
                } catch (_: Exception) {}
            }
            return
        }

        isPlaying = true
        isPaused = false

        playThread = Thread {
            runPlayerLoop()
        }.apply { start() }
    }

    fun pause() {
        if (isWavFormat) {
            if (isPlaying && !isPaused) {
                isPaused = true
                try {
                    mediaPlayer?.pause()
                    mainHandler.removeCallbacks(progressRunnable)
                } catch (_: Exception) {}
            }
            return
        }

        if (isPlaying && !isPaused) {
            isPaused = true
            try {
                audioTrack?.pause()
            } catch (_: Exception) {}
        }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0L, durationMs)
        if (isWavFormat) {
            try {
                mediaPlayer?.seekTo(target.toInt())
                currentPositionMs = target
                onProgressUpdate?.invoke(currentPositionMs)
            } catch (_: Exception) {}
            return
        }

        seekTargetMs = target
        if (isPaused) {
            currentPositionMs = seekTargetMs
            onProgressUpdate?.invoke(currentPositionMs)
        }
    }

    fun stop() {
        isPlaying = false
        isPaused = false
        if (isWavFormat) {
            mainHandler.removeCallbacks(progressRunnable)
            try {
                mediaPlayer?.stop()
                mediaPlayer?.prepare()
            } catch (_: Exception) {}
            return
        }
        playThread?.interrupt()
        try {
            playThread?.join(500)
        } catch (_: Exception) {}
        playThread = null
        releaseResources()
    }

    fun release() {
        stop()
        if (isWavFormat) {
            try {
                mediaPlayer?.release()
            } catch (_: Exception) {}
            mediaPlayer = null
        }
    }

    private fun releaseResources() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null

        try {
            decoder?.stop()
            decoder?.release()
        } catch (_: Exception) {}
        decoder = null
    }

    private fun runPlayerLoop() {
        try {
            val format = MediaFormat.createAudioFormat("audio/opus", SAMPLE_RATE, 1)
            val csd0 = byteArrayOf(
                0x4F, 0x70, 0x75, 0x73, 0x48, 0x65, 0x61, 0x64, // "OpusHead"
                1, // version
                1, // channel count
                0x38, 0x01, // pre-skip (312)
                0x80.toByte(), 0x3E, 0x00, 0x00, // 16000 Hz
                0x00, 0x00, // gain
                0x00 // channel map
            )
            val csd1 = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(6500000L).array()
            val csd2 = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(80000000L).array()
            format.setByteBuffer("csd-0", ByteBuffer.wrap(csd0))
            format.setByteBuffer("csd-1", ByteBuffer.wrap(csd1))
            format.setByteBuffer("csd-2", ByteBuffer.wrap(csd2))

            decoder = MediaCodec.createDecoderByType("audio/opus").apply {
                configure(format, null, null, 0)
                start()
            }

            var packetPointer = 0
            val bufferInfo = MediaCodec.BufferInfo()
            var eosQueued = false
            var track: AudioTrack? = null

            while (isPlaying && !Thread.currentThread().isInterrupted) {
                if (isPaused) {
                    Thread.sleep(50)
                    continue
                }

                // Handle seek request
                val target = seekTargetMs
                if (target >= 0) {
                    seekTargetMs = -1L
                    packetPointer = indexList.indexOfFirst { it.timeMs >= target }
                    if (packetPointer == -1) {
                        packetPointer = indexList.size
                    }
                    decoder?.flush()
                    track?.flush()
                    currentPositionMs = target
                    onProgressUpdate?.invoke(currentPositionMs)
                    eosQueued = false
                }

                var hasWork = false

                // Feed decoder input
                val dec = decoder ?: break
                if (packetPointer < indexList.size && !eosQueued) {
                    val inputIndex = dec.dequeueInputBuffer(5000L)
                    if (inputIndex >= 0) {
                        val inputBuffer = dec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            val packet = indexList[packetPointer]
                            inputBuffer.put(fileBytes, packet.fileOffset.toInt(), packet.packetSize)
                            dec.queueInputBuffer(inputIndex, 0, packet.packetSize, packet.timeMs * 1000L, 0)
                            packetPointer++
                            hasWork = true
                        }
                    }
                } else if (!eosQueued) {
                    val inputIndex = dec.dequeueInputBuffer(5000L)
                    if (inputIndex >= 0) {
                        dec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        eosQueued = true
                        hasWork = true
                    }
                }

                // Drain decoder output
                val outputIndex = dec.dequeueOutputBuffer(bufferInfo, 5000L)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = dec.outputFormat
                    val sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    val channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    val channelMask = if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO

                    Log.d(TAG, "Decoder output format changed: rate=$sampleRate, channels=$channelCount")

                    try {
                        track?.stop()
                        track?.release()
                    } catch (_: Exception) {}

                    val minBufSize = AudioTrack.getMinBufferSize(
                        sampleRate,
                        channelMask,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    track = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setSampleRate(sampleRate)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(channelMask)
                                .build()
                        )
                        .setBufferSizeInBytes(maxOf(minBufSize, 8192))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                    track.play()
                    audioTrack = track
                    hasWork = true
                } else if (outputIndex >= 0) {
                    val outputBuffer = dec.getOutputBuffer(outputIndex)
                    if (bufferInfo.size > 0 && outputBuffer != null && !isPaused) {
                        val pcmData = ByteArray(bufferInfo.size)
                        outputBuffer.get(pcmData)
                        track?.write(pcmData, 0, pcmData.size)
                        currentPositionMs = bufferInfo.presentationTimeUs / 1000L
                        onProgressUpdate?.invoke(currentPositionMs)
                    }
                    dec.releaseOutputBuffer(outputIndex, false)
                    hasWork = true

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isPlaying = false
                        onCompletion?.invoke()
                        break
                    }
                }

                if (!hasWork) {
                    Thread.sleep(10)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in player loop", e)
            onError?.invoke("播放异常: ${e.message}")
        } finally {
            releaseResources()
        }
    }
}
