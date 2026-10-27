package com.example.shuolesa

import com.example.shuolesa.data.model.AsrSegments
import com.example.shuolesa.data.prefs.AiSettings
import com.example.shuolesa.network.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.util.Base64

/** v4.2: 百炼 (DashScope) speaker-separated transcription against a mock server. No real key, no real audio. */
class SpeakerAsrTest {
    private lateinit var server: MockWebServer
    private lateinit var wav: File
    @Before fun start() {
        server=MockWebServer();server.start(InetAddress.getByName("127.0.0.1"),0)
        wav=File.createTempFile("chunk",".wav").apply { writeBytes(ByteArray(64) { it.toByte() }) }
    }
    @After fun stop() { server.shutdown();wav.delete() }
    private fun local(path: String)=server.url(path).newBuilder().host("127.0.0.1").build().toString()
    private fun base()=local("/compatible-mode/v1").trimEnd('/')
    private fun config(model: String="qwen-audio-3.1-asr-flash",diarize: Boolean=true)=
        AiSettings("aliyun",base(),"test-only-key",model,"qwen3.7-plus","","张三\n接口评审、张三",diarize)
    private fun json(body: String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)
    private fun policy()=JSONObject().put("data",JSONObject().put("policy","p").put("signature","s")
        .put("upload_dir","dashscope-instant/test").put("upload_host",local("/oss"))
        .put("expire_in_seconds",300).put("max_file_size_mb",100).put("oss_access_key_id","ak")
        .put("x_oss_object_acl","private").put("x_oss_forbid_overwrite","true")).toString()
    private fun audioData(body: JSONObject)=body.getJSONObject("input").getJSONArray("messages").getJSONObject(0)
        .getJSONArray("content").getJSONObject(0).getJSONObject("input_audio").getString("data")

    @Test fun syncCallSendsInlineAudioAndReadsSpeakers()=runBlocking {
        server.enqueue(json("""{"output":{"text":"下周三评审接口。好的，我来准备。","sentences":[
            {"sentence_id":1,"begin_time":120,"end_time":1500,"text":"下周三评审接口。","speaker_id":0},
            {"sentence_id":2,"begin_time":1600,"end_time":2800,"text":"好的，我来准备。","speaker_id":1}]},"usage":{"duration":3}}"""))
        val result=AiClient().transcribeDetailed(config(),wav)
        assertEquals("下周三评审接口。\n好的，我来准备。",result.text)
        assertEquals(listOf("1","2"),result.segments.map { it.speaker })
        assertEquals(120L,result.segments[0].startMs)
        assertEquals(AsrSegments.SCOPE_SEGMENT,result.speakerScope)
        val request=server.takeRequest()
        assertEquals("/api/v1/services/aigc/multimodal-generation/generation",request.path)
        assertEquals("Bearer test-only-key",request.getHeader("Authorization"))
        val body=JSONObject(request.body.readUtf8())
        assertEquals("qwen-audio-3.1-asr-flash",body.getString("model"))
        val params=body.getJSONObject("parameters")
        assertTrue(params.getBoolean("speaker_diarization_enabled"))
        assertEquals("wav",params.getString("format"))
        assertEquals(setOf("张三","接口评审"),params.getJSONObject("vocabulary").keys().asSequence().toSet())
        val data=audioData(body)
        assertTrue(data.startsWith("data:audio/wav;base64,"))
        assertArrayEquals(wav.readBytes(),Base64.getDecoder().decode(data.removePrefix("data:audio/wav;base64,")))
    }

    @Test fun syncWithoutSentencesKeepsPlainText()=runBlocking {
        server.enqueue(json("""{"output":{"text":" 嗯，好的。 ","sentence":{"begin_time":0,"end_time":900,"text":"嗯，好的。"}}}"""))
        val result=AiClient().transcribeDetailed(config(diarize=false),wav)
        assertEquals("嗯，好的。",result.text)
        assertTrue(result.segments.isEmpty())
        assertFalse(JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("parameters").has("speaker_diarization_enabled"))
    }

    @Test fun largeAudioGoesThroughTemporaryStorage()=runBlocking {
        server.enqueue(json(policy()))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(json("""{"output":{"sentences":[{"begin_time":0,"end_time":800,"text":"大文件。","speaker_id":0}]}}"""))
        val result=DashScopeAsr(AiClient(),maxInlineBytes=10).transcribeChunk(config(),wav)
        assertEquals("大文件。",result.text)
        val policy=server.takeRequest()
        assertEquals("GET",policy.method)
        assertEquals("/api/v1/uploads?action=getPolicy&model=qwen-audio-3.1-asr-flash",policy.path)
        val oss=server.takeRequest()
        assertEquals("/oss",oss.path)
        assertNull(oss.getHeader("Authorization"))
        val form=oss.body.readUtf8()
        assertTrue(form.contains("name=\"OSSAccessKeyId\""))
        assertTrue(form.indexOf("name=\"file\"")>form.indexOf("name=\"key\""))
        val call=server.takeRequest()
        assertEquals("enable",call.getHeader("X-DashScope-OssResourceResolve"))
        assertTrue(audioData(JSONObject(call.body.readUtf8())).startsWith("oss://dashscope-instant/test/"))
    }

    @Test fun meetingPassPollsAndKeepsSpeakersAcrossTheRecording()=runBlocking {
        server.enqueue(json(policy()))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(json("""{"output":{"task_id":"t-1","task_status":"PENDING"}}"""))
        server.enqueue(json("""{"output":{"task_id":"t-1","task_status":"RUNNING"}}"""))
        server.enqueue(json(JSONObject().put("output",JSONObject().put("task_id","t-1").put("task_status","SUCCEEDED")
            .put("results",JSONArray().put(JSONObject().put("subtask_status","SUCCEEDED")
                .put("transcription_url",local("/result.json"))))).toString()))
        server.enqueue(json("""{"transcripts":[{"channel_id":0,"text":"先过一下排期。第二段我来说。","sentences":[
            {"begin_time":1000,"end_time":4000,"text":"先过一下排期。","speaker_id":0},
            {"begin_time":301000,"end_time":303000,"text":"第二段我来说。","speaker_id":1}]}]}"""))
        val segments=DashScopeAsr(AiClient(),pollMs=1).transcribeLong(config(),wav)
        assertEquals(listOf("1","2"),segments.map { it.speaker })
        assertEquals(301000L,segments[1].startMs)
        server.takeRequest();server.takeRequest()
        val submit=server.takeRequest()
        assertEquals("/api/v1/services/audio/asr/transcription",submit.path)
        assertEquals("enable",submit.getHeader("X-DashScope-Async"))
        assertEquals("enable",submit.getHeader("X-DashScope-OssResourceResolve"))
        val body=JSONObject(submit.body.readUtf8())
        assertEquals("qwen-audio-3.1-asr-flash-filetrans",body.getString("model"))
        assertTrue(body.getJSONObject("parameters").getBoolean("diarization_enabled"))
        assertTrue(body.getJSONObject("input").getJSONArray("file_urls").getString(0).startsWith("oss://"))
        assertEquals("/api/v1/tasks/t-1",server.takeRequest().path)
        server.takeRequest()
        val download=server.takeRequest()
        assertEquals("/result.json",download.path)
        assertNull(download.getHeader("Authorization"))
    }

    @Test fun silentMeetingIsEmptyNotFailure()=runBlocking {
        server.enqueue(json(policy()))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(json("""{"output":{"task_id":"t-2","task_status":"PENDING"}}"""))
        server.enqueue(json("""{"output":{"task_id":"t-2","task_status":"FAILED","results":[{"subtask_status":"FAILED","code":"SUCCESS_WITH_NO_VALID_FRAGMENT"}]}}"""))
        assertTrue(DashScopeAsr(AiClient(),pollMs=1).transcribeLong(config(),wav).isEmpty())
    }

    @Test fun failedTaskIsAFailureWithItsMessage()=runBlocking {
        server.enqueue(json(policy()))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(json("""{"output":{"task_id":"t-3","task_status":"PENDING"}}"""))
        server.enqueue(json("""{"output":{"task_id":"t-3","task_status":"FAILED","code":"InvalidFile","message":"bad file"}}"""))
        try { DashScopeAsr(AiClient(),pollMs=1).transcribeLong(config(),wav);fail("failed task accepted") }
        catch(e: AiException) { assertEquals(-1,e.code);assertTrue(e.message!!.contains("bad file")) }
    }

    @Test fun routingAndModels() {
        assertTrue(DashScopeAsr.handles(config()))
        assertFalse(DashScopeAsr.handles(AiSettings("stepfun","https://api.stepfun.com/step_plan/v1","k","stepaudio-2.5-asr","x","")))
        assertTrue(DashScopeAsr.handles(AiSettings("custom","https://dashscope-intl.aliyuncs.com/compatible-mode/v1","k","fun-asr","x","")))
        assertFalse(DashScopeAsr.handles(AiSettings("aliyun","https://dashscope.aliyuncs.com/compatible-mode/v1","k","qwen3-asr-flash","x","")))
        assertEquals("qwen-audio-3.1-asr-flash-filetrans",DashScopeAsr.filetransModel("qwen-audio-3.1-asr-flash"))
        assertNull(DashScopeAsr.filetransModel("fun-asr-flash"))
        assertNull(DashScopeAsr.filetransModel("fun-asr-realtime"))
        assertFalse(DashScopeAsr.syncDiarization("qwen-audio-3.0-asr-flash"))
        assertEquals("https://dashscope-intl.aliyuncs.com",DashScopeAsr.uploadRoot("https://ws1.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1"))
        assertEquals("https://dashscope.aliyuncs.com",DashScopeAsr.apiRoot("https://dashscope.aliyuncs.com/compatible-mode/v1"))
        assertTrue(AiClient().supportsSessionDiarization(config()))
        assertFalse(AiClient().supportsSessionDiarization(config(diarize=false)))
        assertFalse(AiClient().supportsSessionDiarization(config(model="fun-asr-flash")))
    }

    @Test fun chatTurnsThinkingOffOnlyForDashScope()=runBlocking {
        val reply="""{"choices":[{"message":{"content":"{\"ok\":true}"}}]}"""
        server.enqueue(json(reply));server.enqueue(json(reply))
        AiClient().chat(config(),"s","u")
        assertEquals(false,JSONObject(server.takeRequest().body.readUtf8()).getBoolean("enable_thinking"))
        AiClient().chat(AiSettings("custom",base(),"k","whisper-1","llm",""),"s","u")
        assertFalse(JSONObject(server.takeRequest().body.readUtf8()).has("enable_thinking"))
    }

    @Test fun nothingIsSentWithoutOutboundConsent()=runBlocking {
        try { AiClient(outboundAllowed={ false }).transcribeDetailed(config(),wav);fail("sent without consent") }
        catch(e: AiException) { assertEquals(ProcessingReason.AWAITING_CONSENT,e.reason) }
        assertEquals(0,server.requestCount)
    }
}
