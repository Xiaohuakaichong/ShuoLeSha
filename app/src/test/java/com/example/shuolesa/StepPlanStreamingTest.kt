package com.example.shuolesa

import com.example.shuolesa.network.StepPlanAudioBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.ForwardingSink
import okio.blackholeSink
import okio.buffer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.util.Base64
import kotlin.random.Random

/** v4.1-a P0-3: Step Plan body is streamed (no readBytes / Base64 string / JSON string of the whole file). */
class StepPlanStreamingTest {
    private lateinit var server: MockWebServer
    private lateinit var file: File
    @Before fun start() { server=MockWebServer();server.start(InetAddress.getByName("127.0.0.1"),0)
        file=File.createTempFile("meeting",".wav") }
    @After fun stop() { server.shutdown();file.delete() }

    @Test fun largeFileBodyIsExactAndDecodable() {
        val bytes=Random(7).nextBytes(12*1024*1024+1) // 12 MiB + 1: exercises Base64 padding
        file.writeBytes(bytes)
        server.enqueue(MockResponse().setBody("ok"))
        val body=StepPlanAudioBody(file,"wav","stepaudio-2.5-asr")
        OkHttpClient().newCall(Request.Builder().url(server.url("/v1/audio/asr/sse")).post(body).build()).execute().close()
        val request=server.takeRequest()
        assertEquals(body.contentLength(),request.bodySize)
        assertEquals(body.contentLength().toString(),request.getHeader("Content-Length"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        val json=JSONObject(request.body.readUtf8())
        val audio=json.getJSONObject("audio")
        assertEquals("wav",audio.getJSONObject("input").getJSONObject("format").getString("type"))
        assertEquals("stepaudio-2.5-asr",audio.getJSONObject("input").getJSONObject("transcription").getString("model"))
        assertArrayEquals(bytes,Base64.getDecoder().decode(audio.getString("data")))
    }

    @Test fun pcmFormatCarriesCodecFields() {
        file.writeBytes(ByteArray(10) { it.toByte() })
        val buffer=Buffer();StepPlanAudioBody(file,"pcm","m").writeTo(buffer)
        val format=JSONObject(buffer.readUtf8()).getJSONObject("audio").getJSONObject("input").getJSONObject("format")
        assertEquals("pcm_s16le",format.getString("codec"));assertEquals(16000,format.getInt("rate"))
    }

    /** Heap growth while writing a 16 MiB file must stay far below the old ~50 MB peak (and below file size). */
    @Test fun memoryIsNotProportionalToFileSize() {
        val size=16*1024*1024
        file.outputStream().use { out -> val block=ByteArray(1024*1024) { 0x55 };repeat(16) { out.write(block) } }
        val body=StepPlanAudioBody(file,"wav","m")
        var written=0L
        val runtime=Runtime.getRuntime()
        repeat(3) { System.gc() }
        val before=runtime.totalMemory()-runtime.freeMemory()
        var peak=before
        val counting=object: ForwardingSink(blackholeSink()) {
            override fun write(source: Buffer,byteCount: Long) {
                written+=byteCount
                peak=maxOf(peak,runtime.totalMemory()-runtime.freeMemory())
                super.write(source,byteCount)
            }
        }.buffer()
        body.writeTo(counting);counting.flush()
        assertEquals(body.contentLength(),written)
        // Base64 of the file alone would be ~22 MB; allow GC noise but forbid whole-file buffering.
        assertTrue("heap grew ${(peak-before)/1024/1024} MB",peak-before<size)
    }
}
