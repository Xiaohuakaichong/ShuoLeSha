package com.example.shuolesa

import com.example.shuolesa.data.model.SourceText
import com.example.shuolesa.data.prefs.AiSettings
import com.example.shuolesa.network.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetAddress

/**
 * v4.1-a.1 (qa-v41a P1-2): "确认数据外发前零请求".
 * ProcessingCoordinator, AskMemory and SettingsViewModel.test (测试连接) all call [OutboundGate]; AiClient.send()
 * enforces it again, so every path is checked here against a MockWebServer that must see 0 requests.
 */
class OutboundGateTest {
    private lateinit var server: MockWebServer
    private lateinit var file: File
    private var consented=false
    private val client get()=AiClient(outboundAllowed={ consented })
    @Before fun start() { server=MockWebServer();server.start(InetAddress.getByName("127.0.0.1"),0)
        file=File.createTempFile("audio",".wav").apply { writeBytes(ByteArray(3200)) } }
    @After fun stop() { server.shutdown();file.delete() }
    private fun config()=AiSettings("custom",server.url("/v1").toString().trimEnd('/'),"k","asr","llm","")
    private fun blocked(block: suspend ()->Unit): AiException = runBlocking {
        try { block();fail("expected AWAITING_CONSENT");throw IllegalStateException() } catch(e: AiException) { e }
    }
    private fun assertBlocked(e: AiException) {
        assertEquals(AiException.CONSENT,e.code);assertEquals(ProcessingReason.AWAITING_CONSENT,e.reason)
    }

    /** Coordinator: enqueueDurableJobs / isEligible / runOne all ask OutboundGate.allows(kind, consented). */
    @Test fun processingQueueOnlyLetsLocalImportThroughBeforeConsent() {
        for(kind in listOf("ASR","MAP","DERIVE","DIGEST")) {
            assertFalse(kind,OutboundGate.allows(kind,false));assertTrue(kind,OutboundGate.allows(kind,true))
        }
        assertTrue(OutboundGate.allows("IMPORT",false))
    }
    /** Coordinator jobs that do reach AiClient (consent revoked mid-run) send nothing and go back to PENDING, no attempt. */
    @Test fun queueJobsSendNothingAndStayPending() {
        val sources=SourceText(1L,"今天开会讨论预算",0L,"asr")
        listOf<suspend ()->Unit>(
            { client.transcribe(config(),file) },
            { client.mapTopics(config(),sources,"lifelog") },
            { client.chat(config(),"整理/复盘","原话") }, // finalizeNotes + digest go through chat()
        ).forEach { call -> val e=blocked(call);assertBlocked(e)
            val outcome=classifyFailure(e,true)
            assertEquals("PENDING",outcome.state);assertFalse(outcome.countsAsAttempt) }
        assertEquals(0,server.requestCount)
    }
    /** AskMemory: OutboundGate.require before anything, and queryTerms/answer are blocked in AiClient too. */
    @Test fun memoryQuestionSendsNothingBeforeConsent() {
        assertBlocked(blocked { OutboundGate.require(false) })
        runBlocking { client.queryTerms(config(),"预算是多少") } // swallows failures by design, must still send nothing
        blocked { client.answer(config(),"预算是多少",listOf(SourceText(1L,"预算是十万",0L))) }.let(::assertBlocked)
        assertEquals(0,server.requestCount)
    }
    /** 测试连接: SettingsViewModel.test calls OutboundGate.require; transcribe + chat are blocked in AiClient too. */
    @Test fun testConnectionSendsNothingBeforeConsent() {
        assertBlocked(blocked { OutboundGate.require(false) })
        assertBlocked(blocked { client.transcribe(config(),file) })
        assertBlocked(blocked { client.chat(config(),"请仅回答连接成功","这是一次连接测试",false) })
        assertEquals(0,server.requestCount)
    }
    /** After consent the same calls do reach the server. */
    @Test fun requestsFlowAfterConsent() = runBlocking {
        consented=true
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"连接成功"}}]}"""))
        assertEquals("连接成功",client.chat(config(),"s","u",false))
        assertEquals(1,server.requestCount)
        OutboundGate.require(true)
    }
}
