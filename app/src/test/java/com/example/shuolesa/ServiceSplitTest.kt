package com.example.shuolesa

import com.example.shuolesa.data.prefs.AiSettings
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.prefs.ProviderPreset
import com.example.shuolesa.network.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.nio.file.Files

/** v4.3: 转写 and 整理与提问 on different services — routing, keys, consent target, per-service thinking settings. */
class ServiceSplitTest {
    private lateinit var asrServer: MockWebServer
    private lateinit var llmServer: MockWebServer
    private lateinit var dir: File
    @Before fun start() {
        asrServer=MockWebServer();asrServer.start(InetAddress.getByName("127.0.0.1"),0)
        llmServer=MockWebServer();llmServer.start(InetAddress.getByName("127.0.0.1"),0)
        dir=Files.createTempDirectory("split").toFile()
    }
    @After fun stop() { asrServer.shutdown();llmServer.shutdown();dir.deleteRecursively() }
    private fun local(server: MockWebServer)=server.url("/v1").newBuilder().host("127.0.0.1").build().toString().trimEnd('/')
    private fun split(llmModel: String="llm")=AiSettings("custom",local(asrServer),"asr-key","whisper-1",llmModel,"",
        llmProvider=ProviderPreset.CUSTOM_LLM.id,llmBaseUrl=local(llmServer),llmApiKey="llm-key")
    private val chatReply="""{"choices":[{"message":{"content":"{\"ok\":true}"}}]}"""
    private fun json(body: String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)

    @Test fun settingsDescribeBothServices() {
        val same=AiSettings("aliyun","https://dashscope.aliyuncs.com/compatible-mode/v1","k","qwen-audio-3.1-asr-flash","qwen3.8-flash","")
        assertFalse(same.split);assertSame(same,same.forLlm())
        assertEquals(listOf(same.baseUrl),same.endpoints);assertEquals(same.baseUrl,same.outboundTarget)
        val s=split()
        assertTrue(s.split)
        val llm=s.forLlm()
        assertEquals(ProviderPreset.CUSTOM_LLM.id,llm.provider);assertEquals(local(llmServer),llm.baseUrl);assertEquals("llm-key",llm.apiKey)
        assertFalse(llm.split);assertEquals("llm",llm.llmModel)
        assertEquals(local(asrServer)+" + "+local(llmServer),s.outboundTarget)
        assertTrue(s.configured)
        assertFalse(s.copy(llmApiKey="",llmBaseUrl="https://api.deepseek.com").configured)
        assertTrue("a local 整理 proxy needs no key",s.copy(llmApiKey="").configured)
        // keys never appear in toString
        assertFalse(s.toString().contains("llm-key"));assertFalse(s.toString().contains("asr-key"))
    }
    @Test fun validationCoversTheLlmAddress() {
        split().validate()
        val lan=assertThrows(IllegalArgumentException::class.java) { split().copy(llmBaseUrl="http://192.168.1.5:8080/v1").validate() }
        assertTrue(lan.message!!.contains("整理服务地址"))
        assertThrows(IllegalArgumentException::class.java) { split().copy(llmBaseUrl="").validate() }
        split().copy(llmBaseUrl="https://generativelanguage.googleapis.com/v1beta/openai").validate()
    }
    @Test fun presetsAreOfferedPerJob() {
        assertTrue(ProviderPreset.asrPresets.none { it==ProviderPreset.DEEPSEEK || it==ProviderPreset.GEMINI || it==ProviderPreset.CUSTOM_LLM })
        assertFalse("转写 gateway and 整理 proxy keep separate keys",ProviderPreset.LOCAL_OR_CUSTOM in ProviderPreset.llmPresets)
        assertTrue(ProviderPreset.ALIYUN in ProviderPreset.asrPresets && ProviderPreset.ALIYUN in ProviderPreset.llmPresets)
        assertEquals("qwen3.8-flash",ProviderPreset.ALIYUN.defaultLlmModel)
        assertEquals(ProviderPreset.entries.size,ProviderPreset.entries.map { it.id }.distinct().size)
    }
    @Test fun chatGoesToLlmServiceAndAudioToAsrService()=runBlocking {
        llmServer.enqueue(json(chatReply))
        asrServer.enqueue(json("""{"text":"原话"}"""))
        val ai=AiClient()
        ai.chat(split(),"s","u")
        val chat=llmServer.takeRequest()
        assertEquals("/v1/chat/completions",chat.path);assertEquals("Bearer llm-key",chat.getHeader("Authorization"))
        val wav=File(dir,"a.wav").apply { writeBytes(ByteArray(64)) }
        assertEquals("原话",ai.transcribe(split(),wav))
        val audio=asrServer.takeRequest()
        assertEquals("/v1/audio/transcriptions",audio.path);assertEquals("Bearer asr-key",audio.getHeader("Authorization"))
        assertEquals(1,llmServer.requestCount);assertEquals(1,asrServer.requestCount)
    }
    @Test fun geminiRunsWithLowThinkingAndNoSamplingOrJsonMode()=runBlocking {
        llmServer.enqueue(json(chatReply))
        AiClient().chat(split("gemini-3.8-flash"),"s","u")
        val body=JSONObject(llmServer.takeRequest().body.readUtf8())
        assertEquals("low",body.getString("reasoning_effort"))
        assertFalse(body.has("temperature"));assertFalse(body.has("response_format"));assertFalse(body.has("enable_thinking"))
        assertEquals(16384,body.getInt("max_tokens"))
    }
    @Test fun otherServicesKeepJsonModeWithoutThinkingFields()=runBlocking {
        llmServer.enqueue(json(chatReply))
        AiClient().chat(split(),"s","u")
        val body=JSONObject(llmServer.takeRequest().body.readUtf8())
        assertEquals("json_object",body.getJSONObject("response_format").getString("type"))
        assertFalse(body.has("thinking"));assertFalse(body.has("reasoning_effort"));assertFalse(body.has("enable_thinking"))
        assertEquals(0.1,body.getDouble("temperature"),0.0001);assertEquals(4096,body.getInt("max_tokens"))
    }
    @Test fun thinkingSettingsPerService() {
        fun of(base: String,model: String="m",provider: String="custom-llm")=ChatTuning.of(AiSettings(provider,base,"k","a",model,""))
        assertEquals(ChatTuning.DASHSCOPE,of("https://dashscope.aliyuncs.com/compatible-mode/v1"))
        assertEquals(ChatTuning.THINKING_TYPE,of("https://api.deepseek.com","deepseek-flash"))
        assertEquals(ChatTuning.THINKING_TYPE,of("https://ark.cn-beijing.volces.com/api/v3"))
        assertEquals(ChatTuning.THINKING_TYPE,of("https://open.bigmodel.cn/api/paas/v4"))
        assertEquals(ChatTuning.GEMINI,of("https://generativelanguage.googleapis.com/v1beta/openai","gemini-3.8-flash"))
        assertEquals("a proxy serving Gemini",ChatTuning.GEMINI,of("http://localhost:8080/v1","Gemini-3.8-Flash"))
        assertEquals(ChatTuning.PLAIN,of("https://api.siliconflow.cn/v1","deepseek-ai/DeepSeek-V3"))
        val p=JSONObject();ChatTuning.THINKING_TYPE.apply(p)
        assertEquals("disabled",p.getJSONObject("thinking").getString("type"))
    }
    @Test fun jsonIsTakenOutOfSurroundingText() {
        assertTrue(AiClient.objectJson("好的，结果如下：\n{\"topics\":[]}\n以上。").has("topics"))
        assertTrue(AiClient.objectJson("```json\n{\"a\":1}\n```").has("a"))
        assertThrows(AiException::class.java) { AiClient.objectJson("没有 JSON") }
    }
    @Test fun consentTargetNormalisesEachAddress() {
        val target="HTTP://127.0.0.1:80/v1/ + https://API.deepseek.com:443/"
        assertEquals(listOf("HTTP://127.0.0.1:80/v1/","https://API.deepseek.com:443/"),AppPreferences.targets(target))
        assertEquals("http://127.0.0.1/v1 + https://api.deepseek.com",AppPreferences.normalizeTarget(target))
        assertTrue(ConnectionTester.matchesConsent(target,"http://127.0.0.1/v1 + https://api.deepseek.com"))
        assertFalse("整理 address changed",ConnectionTester.matchesConsent(target,"http://127.0.0.1/v1 + https://api.example.com"))
        assertFalse("整理 service added",ConnectionTester.matchesConsent("http://127.0.0.1/v1","http://127.0.0.1/v1 + https://api.deepseek.com"))
        assertFalse("整理 service removed",ConnectionTester.matchesConsent(target,"http://127.0.0.1/v1"))
    }
    @Test fun connectionTestNeedsConsentForBothAndNamesTheFailingService()=runBlocking {
        val ai=AiClient(outboundAllowed={ true })
        val onlyAsr=try { ConnectionTester.run(ai,split(),true,local(asrServer),dir);null } catch(e: AiException) { e }
        assertEquals(AiException.DRAFT_CHANGED,onlyAsr!!.code)
        assertEquals(0,asrServer.requestCount+llmServer.requestCount)
        asrServer.enqueue(json("""{"text":"ok"}"""))
        llmServer.enqueue(MockResponse().setResponseCode(401))
        val e=try { ConnectionTester.run(ai,split(),true,split().outboundTarget,dir);null } catch(x: AiException) { x }
        assertEquals(ProcessingReason.KEY_INVALID,e!!.reason)
        assertTrue(e.message!!,e.message!!.startsWith("整理服务："))
        assertEquals(1,asrServer.requestCount);assertEquals(1,llmServer.requestCount)
    }
}
