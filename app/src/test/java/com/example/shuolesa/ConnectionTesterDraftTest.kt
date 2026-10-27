package com.example.shuolesa

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
import java.nio.file.Files

/** qa v4.1-final P1: 测试连接 on an unsaved draft address must send nothing unless that address was consented. */
class ConnectionTesterDraftTest {
    private lateinit var server: MockWebServer
    private lateinit var dir: File
    private val ai = AiClient(outboundAllowed = { true }) // saved-address gate passes: only the draft check stands
    @Before fun start() { server = MockWebServer(); server.start(InetAddress.getByName("127.0.0.1"), 0)
        dir = Files.createTempDirectory("ct").toFile() }
    @After fun stop() { server.shutdown(); dir.deleteRecursively() }
    private fun draft(base: String = server.url("/v1").toString().trimEnd('/')) = AiSettings("custom", base, "k", "asr", "llm", "")

    @Test fun draftDifferentFromConsentedIsBlockedAndSendsZeroRequests() = runBlocking {
        val e = try { ConnectionTester.run(ai, draft(), true, "https://api.stepfun.com/step_plan/v1", dir); fail("expected block"); null }
            catch (x: AiException) { x }!!
        assertEquals(AiException.DRAFT_CHANGED, e.code)
        assertEquals(ProcessingReason.AWAITING_CONSENT, e.reason)
        assertEquals(ConnectionTester.DRAFT_CHANGED_MESSAGE, e.message)
        assertEquals(0, server.requestCount)
        assertFalse(File(dir, "connection-test.wav").exists())
    }
    @Test fun noConsentAtAllIsConsentErrorAndSendsZeroRequests() = runBlocking {
        val e = try { ConnectionTester.run(ai, draft(), false, null, dir); null } catch (x: AiException) { x }!!
        assertEquals(AiException.CONSENT, e.code)
        assertEquals(0, server.requestCount)
    }
    @Test fun matchingAddressGoesThrough() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"text":"ok"}"""))
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"连接成功"}}]}"""))
        // consented form differs only cosmetically: case, default port, trailing slash
        val consented = "HTTP://${server.hostName.uppercase()}:${server.port}/v1/"
        val r = runCatching { ConnectionTester.run(ai, draft(), true, consented, dir) }
        assertTrue("request must reach the consented server: ${r.exceptionOrNull()}", server.requestCount >= 1)
    }
    @Test fun normalisationMatchesConsentCheck() {
        assertTrue(ConnectionTester.matchesConsent("https://API.StepFun.com:443/step_plan/v1/", "https://api.stepfun.com/step_plan/v1"))
        assertTrue(ConnectionTester.matchesConsent("http://localhost:80/v1", "HTTP://LOCALHOST/v1/"))
        assertTrue(ConnectionTester.matchesConsent(" https://a.example.com ", "https://a.example.com/"))
        assertFalse(ConnectionTester.matchesConsent("https://a.example.com/v1", "https://a.example.com:8443/v1"))
        assertFalse(ConnectionTester.matchesConsent("https://a.example.com/v1", "http://a.example.com/v1"))
        assertFalse(ConnectionTester.matchesConsent("https://a.example.com/v1", "https://evil.example.com/v1"))
        assertFalse(ConnectionTester.matchesConsent("https://a.example.com/v1", "https://a.example.com/v2"))
        assertFalse(ConnectionTester.matchesConsent(null, "https://a.example.com/v1"))
    }
}
