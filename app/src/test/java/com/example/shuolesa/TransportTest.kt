package com.example.shuolesa

import com.example.shuolesa.data.prefs.AiSettings
import com.example.shuolesa.network.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.TimeUnit
import java.net.InetAddress

class TransportTest {
    private lateinit var server:MockWebServer
    private lateinit var file:File
    @Before fun start() {server=MockWebServer();server.start(InetAddress.getByName("127.0.0.1"),0);file=File.createTempFile("audio",".wav").apply {writeBytes("RIFF0000WAVE".toByteArray())}}
    @After fun stop() {server.shutdown();file.delete()}
    private fun config()=AiSettings("custom",server.url("/v1").newBuilder().host("127.0.0.1").build().toString().trimEnd('/'),"test-only-secret","asr","llm","")
    @Test fun multipartKeepsFormatAndCredentialsOutOfErrors()=runBlocking {
        server.enqueue(MockResponse().setBody("{\"text\":\"原始转写\"}"))
        assertEquals("原始转写",AiClient().transcribe(config(),file))
        val request=server.takeRequest();assertEquals("/v1/audio/transcriptions",request.path)
        assertTrue(request.body.readUtf8().contains("Content-Type: audio/wav"));assertEquals("Bearer test-only-secret",request.getHeader("Authorization"))
        server.enqueue(MockResponse().setResponseCode(401).setBody("test-only-secret private upstream body"))
        try {AiClient().transcribe(config(),file);fail()} catch(e:AiException) {assertEquals(401,e.code);assertFalse(e.message!!.contains("secret"))}
    }
    @Test fun malformedAsrResponseDoesNotBecomeSuccessfulEmptyText()=runBlocking {
        server.enqueue(MockResponse().setBody("{\"title\":\"wrong\"}"))
        try {AiClient().transcribe(config(),file);fail()} catch(e:AiException) {assertEquals(-1,e.code)}
    }
    @Test fun cancellationInterruptsBodyRead()=runBlocking {
        server.enqueue(MockResponse().setBody("{\"text\":\"test\"}").setBodyDelay(3,TimeUnit.SECONDS))
        val job=launch(Dispatchers.IO) {AiClient().transcribe(config(),file)}
        assertNotNull(server.takeRequest(3,TimeUnit.SECONDS))
        val start=System.nanoTime();job.cancelAndJoin();assertTrue((System.nanoTime()-start)/1_000_000<2000)
    }
}
