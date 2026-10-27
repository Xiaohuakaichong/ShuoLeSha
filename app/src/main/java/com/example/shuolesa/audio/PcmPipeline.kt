package com.example.shuolesa.audio

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * v4.1-a P0-1: decouples the microphone read loop from encoding, chunk sealing and Room writes.
 * The producer (AudioRecord loop) only copies PCM into a pooled array and hands it to a bounded
 * channel (~10 s at 16 kHz). A single consumer coroutine runs [run] and does all slow work.
 * Pure Kotlin so it can be unit tested on the JVM.
 */
class PcmPipeline(frameSize: Int = AudioRecorder.FRAME_SIZE, capacitySeconds: Int = 10,
                  sampleRate: Int = AudioRecorder.SAMPLE_RATE) {
    private class Frame(val data: ShortArray) { var count = 0 }

    val capacityFrames = (capacitySeconds * sampleRate + frameSize - 1) / frameSize
    private val frameSize = frameSize
    private val channel = Channel<Frame>(capacityFrames)
    private val pool = ArrayDeque<Frame>()
    private val queued = AtomicInteger(0)

    /** Samples accepted from the producer. */
    val producedSamples = AtomicLong(0)
    /** Samples handed to the consumer sink. */
    val consumedSamples = AtomicLong(0)
    /** Times the producer found the queue full and had to wait (should stay 0 in practice). */
    val stalls = AtomicInteger(0)
    /** Highest observed queue depth in frames, for diagnostics. */
    @Volatile var maxQueuedFrames = 0; private set

    private fun obtain(): Frame = synchronized(pool) { pool.pollFirst() } ?: Frame(ShortArray(frameSize))
    private fun recycle(frame: Frame) { synchronized(pool) { if (pool.size < capacityFrames + 4) pool.addLast(frame) } }

    /** Called from the read loop. Copies [count] samples; never touches disk or the database. */
    suspend fun send(pcm: ShortArray, count: Int) {
        var offset = 0
        while (offset < count) {
            val frame = obtain()
            val n = minOf(frameSize, count - offset)
            System.arraycopy(pcm, offset, frame.data, 0, n)
            frame.count = n
            val depth = queued.incrementAndGet()
            if (depth > maxQueuedFrames) maxQueuedFrames = depth
            val offered = channel.trySend(frame)
            if (offered.isClosed) { queued.decrementAndGet(); throw ClosedSendChannelException("PCM 管线已关闭") }
            if (offered.isFailure) { stalls.incrementAndGet(); channel.send(frame) }
            producedSamples.addAndGet(n.toLong())
            offset += n
        }
    }

    /** Producer finished: the consumer drains what is queued and then returns. */
    fun close(cause: Throwable? = null) { channel.close(cause) }

    /** Consumer loop. Returns after [close] once every queued frame went through [sink]. */
    suspend fun run(sink: suspend (ShortArray, Int) -> Unit) {
        for (frame in channel) {
            queued.decrementAndGet()
            try {
                sink(frame.data, frame.count)
                consumedSamples.addAndGet(frame.count.toLong())
            } finally { recycle(frame) }
        }
    }
}
