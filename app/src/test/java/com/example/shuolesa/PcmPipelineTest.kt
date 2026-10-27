package com.example.shuolesa

import com.example.shuolesa.audio.AudioRecorder
import com.example.shuolesa.audio.PcmPipeline
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v4.1-a P0-1 regression: a slow chunk seal (2 s) must not lose microphone samples. */
class PcmPipelineTest {
    private val chunkSamples = 16000L * 300

    @Test fun slowSealDoesNotDropSamplesOver11Minutes() = runTest {
        val pipeline = PcmPipeline()
        val frame = AudioRecorder.FRAME_SIZE
        val totalFrames = (11 * 60 * AudioRecorder.SAMPLE_RATE) / frame
        var written = 0L
        var inChunk = 0L
        var seals = 0
        var checksumIn = 0L
        var checksumOut = 0L
        val consumer = launch {
            pipeline.run { pcm, count ->
                for (i in 0 until count) checksumOut += pcm[i]
                written += count; inChunk += count
                if (inChunk >= chunkSamples) { delay(2_000); seals++; inChunk = 0 } // slow closeChunk()
                else if (written % (16000L * 30) < count) delay(150)                 // checkpoint write
            }
        }
        val pcm = ShortArray(frame)
        repeat(totalFrames) { n ->
            for (i in 0 until frame) pcm[i] = ((n * 31 + i) % 2000 - 1000).toShort()
            for (i in 0 until frame) checksumIn += pcm[i]
            pipeline.send(pcm, frame)
            delay(60) // AudioRecord delivers 960 samples every 60 ms
        }
        pipeline.close()
        consumer.join()
        val input = totalFrames.toLong() * frame
        assertEquals(input, pipeline.producedSamples.get())
        assertEquals(input, written)
        assertEquals(checksumIn, checksumOut)
        assertEquals(2, seals)
        assertEquals("producer must never wait on the consumer", 0, pipeline.stalls.get())
        assertTrue(pipeline.maxQueuedFrames < pipeline.capacityFrames)
    }

    @Test fun producerArrayReuseDoesNotCorruptQueuedFrames() = runTest {
        val pipeline = PcmPipeline(frameSize = 4, capacitySeconds = 1, sampleRate = 40)
        val seen = mutableListOf<Short>()
        val buffer = ShortArray(4)
        for (v in 1..5) { buffer.fill(v.toShort()); pipeline.send(buffer, 4) }
        pipeline.close()
        pipeline.run { pcm, count -> for (i in 0 until count) seen += pcm[i] }
        assertEquals((1..5).flatMap { v -> List(4) { v.toShort() } }, seen)
    }

    @Test fun oversizedReadIsSplitIntoFrames() = runTest {
        val pipeline = PcmPipeline(frameSize = 960)
        pipeline.send(ShortArray(2500) { 1 }, 2500)
        pipeline.close()
        var total = 0
        pipeline.run { _, count -> assertTrue(count <= 960); total += count }
        assertEquals(2500, total)
    }
}
