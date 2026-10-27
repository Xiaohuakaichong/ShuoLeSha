package com.example.shuolesa

import com.example.shuolesa.data.prefs.AiSettings
import com.example.shuolesa.data.prefs.ServiceRegion
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

/** v4.1-a P0-2: error classification -> WAITING_CONFIG(KEY_INVALID / ADDRESS_INVALID), retryable 429/5xx. */
class ProcessingClassificationTest {
    private lateinit var server: MockWebServer
    private lateinit var file: File
    @Before fun start() { server=MockWebServer();server.start(InetAddress.getByName("127.0.0.1"),0)
        file=File.createTempFile("audio",".wav").apply { writeBytes("RIFF0000WAVE".toByteArray()) } }
    @After fun stop() { server.shutdown();file.delete() }
    private fun config(base: String=server.url("/v1").toString().trimEnd('/'))=AiSettings("custom",base,"k","asr","llm","")
    private fun failure(response: MockResponse): AiException = runBlocking {
        server.enqueue(response)
        try { AiClient().transcribe(config(),file);fail("expected failure");throw IllegalStateException() } catch(e: AiException) { e }
    }

    @Test fun keyRejectionsWaitForConfigWithKeyReason() {
        for(code in listOf(401,403)) {
            val e=failure(MockResponse().setResponseCode(code))
            val outcome=classifyFailure(e,true)
            assertEquals("WAITING_CONFIG",outcome.state);assertEquals(ProcessingReason.KEY_INVALID,outcome.reason);assertFalse(outcome.countsAsAttempt)
        }
    }
    @Test fun gatewayUpstream401Is502WithHeaderAndMeansKeyInvalid() {
        val e=failure(MockResponse().setResponseCode(502).setHeader("X-Upstream-Status","401"))
        assertEquals(ProcessingReason.KEY_INVALID,classifyFailure(e,true).reason)
        val plain=failure(MockResponse().setResponseCode(502))
        assertEquals(ProcessingReason.RATE_LIMITED,classifyFailure(plain,true).reason)
    }
    @Test fun notFoundIsAddressProblem() {
        val outcome=classifyFailure(failure(MockResponse().setResponseCode(404)),true)
        assertEquals("WAITING_CONFIG",outcome.state);assertEquals(ProcessingReason.ADDRESS_INVALID,outcome.reason)
    }
    @Test fun unknownHostIsAddressOnlyWhenNetworkIsUp() = runBlocking {
        // Deterministic DNS failure (sandboxes / captive proxies may resolve every name).
        val noDns=okhttp3.OkHttpClient.Builder().dns(object: okhttp3.Dns { override fun lookup(hostname: String): List<java.net.InetAddress> = throw java.net.UnknownHostException(hostname) }).build()
        val e=try { AiClient(noDns).transcribe(config("https://gateway.example.com/v1"),file);fail();throw IllegalStateException() } catch(e: AiException) { e }
        assertEquals(AiException.DNS,e.code)
        assertEquals(ProcessingReason.ADDRESS_INVALID,classifyFailure(e,true).reason)
        val offline=classifyFailure(e,false)
        assertEquals("WAITING_NETWORK",offline.state);assertEquals(ProcessingReason.NETWORK,offline.reason)
    }
    @Test fun rateLimitAndServerErrorsRetryWithoutCountingAndHonorRetryAfter() {
        val limited=classifyFailure(failure(MockResponse().setResponseCode(429).setHeader("Retry-After","12")),true)
        assertEquals("WAITING_NETWORK",limited.state);assertEquals(ProcessingReason.RATE_LIMITED,limited.reason)
        assertFalse(limited.countsAsAttempt);assertEquals(12_000L,limited.retryAfterMs)
        val unavailable=classifyFailure(failure(MockResponse().setResponseCode(503)),true)
        assertEquals(ProcessingReason.RATE_LIMITED,unavailable.reason);assertFalse(unavailable.countsAsAttempt)
    }
    @Test fun modelFormatErrorsCountAsAttempts() {
        val e=try { AiClient.objectJson("not json");fail();throw IllegalStateException() } catch(e: AiException) { e }
        val outcome=classifyFailure(e,true)
        assertEquals("FAILED",outcome.state);assertTrue(outcome.countsAsAttempt)
        assertTrue(classifyFailure(IllegalStateException("x"),true).countsAsAttempt)
    }
    @Test fun retryAfterParsing() {
        assertEquals(5_000L,AiException.parseRetryAfter("5"))
        assertEquals(3_600_000L,AiException.parseRetryAfter("999999"))
        assertNull(AiException.parseRetryAfter("soon"))
        val now=java.time.ZonedDateTime.parse("Wed, 14 Oct 2026 09:00:00 GMT",java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        assertEquals(30_000L,AiException.parseRetryAfter("Wed, 14 Oct 2026 09:00:30 GMT",now))
    }
    @Test fun reasonPrefixRoundTrip() {
        val stored=ProcessingReason.KEY_INVALID.encode("服务密钥无效")
        assertEquals(ProcessingReason.KEY_INVALID,ProcessingReason.code(stored))
        assertEquals("服务密钥无效",ProcessingReason.message(stored))
        assertNull(ProcessingReason.code("旧版错误文字"));assertEquals("旧版错误文字",ProcessingReason.message("旧版错误文字"))
    }
    @Test fun regionHint() { // v4.1-a.1: see ServiceRegionTest for the full matrix
        assertEquals(ServiceRegion.DOMESTIC,ServiceRegion.of("https://api.stepfun.com/step_plan/v1"))
        assertEquals(ServiceRegion.OVERSEAS,ServiceRegion.of("https://api.openai.com/v1"))
        assertEquals(ServiceRegion.UNKNOWN,ServiceRegion.of("https://my-gateway.example.com/v1"))
        assertEquals(ServiceRegion.LAN,ServiceRegion.of("http://10.0.2.2:8000/v1"))
    }

    // v4.1-a.1 (qa-v41a P2-6): V4 error text without a [CODE] prefix is a plain failure and never crashes.
    @Test fun legacyErrorWithoutPrefixParsesAsNoReason() {
        for(legacy in listOf("服务请求失败 (HTTP 500)","[未分类] 旧文本","[unknown_code] x","","[]",null)) {
            assertNull(ProcessingReason.code(legacy))
            assertEquals(legacy,ProcessingReason.message(legacy)?.let { legacy })
        }
        assertEquals("服务请求失败 (HTTP 500)",ProcessingReason.message("服务请求失败 (HTTP 500)"))
        assertEquals(ProcessingReason.KEY_INVALID,ProcessingReason.code("[KEY_INVALID] 密钥失效"))
        assertEquals("密钥失效",ProcessingReason.message("[KEY_INVALID] 密钥失效"))
        assertFalse(ProcessingReason.needsServiceFix(ProcessingReason.code("服务请求失败")))
    }

    // v4.1-a.1: a blocked http address is WAITING_CONFIG / CLEARTEXT_BLOCKED, never WAITING_NETWORK, and sends nothing.
    @Test fun cleartextLanAddressIsConfigProblemNotNetwork() = runBlocking {
        val before=server.requestCount
        val e=try { AiClient().transcribe(config("http://192.168.1.10:8000/v1"),file);fail();throw IllegalStateException() } catch(e: AiException) { e }
        assertEquals(AiException.CLEARTEXT,e.code)
        assertEquals(ProcessingReason.CLEARTEXT_BLOCKED,e.reason)
        for(networkUp in listOf(true,false)) {
            val outcome=classifyFailure(e,networkUp)
            assertEquals("WAITING_CONFIG",outcome.state);assertEquals(ProcessingReason.CLEARTEXT_BLOCKED,outcome.reason);assertFalse(outcome.countsAsAttempt)
        }
        assertTrue(ProcessingReason.needsServiceFix(ProcessingReason.CLEARTEXT_BLOCKED))
        assertEquals(ProcessingReason.CLEARTEXT_BLOCKED,ProcessingReason.code(ProcessingReason.CLEARTEXT_BLOCKED.encode(e.message!!)))
        assertEquals(before,server.requestCount)
    }
    @Test fun androidCleartextPolicyExceptionIsRecognised() {
        // Exact text thrown by OkHttp on Android when NSC forbids cleartext.
        val nsc=java.net.UnknownServiceException("CLEARTEXT communication to 192.168.1.10 not permitted by network security policy")
        assertTrue(isCleartextBlocked(nsc))
        assertTrue(isCleartextBlocked(java.io.IOException("wrapped",nsc)))
        assertFalse(isCleartextBlocked(java.net.UnknownHostException("x")))
        assertFalse(isCleartextBlocked(java.net.UnknownServiceException("Unable to find acceptable protocols")))
    }
    @Test fun localhostHttpIsNotBlocked() {
        for(base in listOf("http://localhost:8000/v1","http://127.0.0.1:8000/v1","http://10.0.2.2:8000/v1","https://192.168.1.10/v1")) checkCleartext(base)
        for(base in listOf("http://192.168.1.10:8000/v1","http://example.com/v1")) assertThrows(AiException::class.java) { checkCleartext(base) }
    }
}
