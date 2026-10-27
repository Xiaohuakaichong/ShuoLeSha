package com.example.shuolesa.network

import com.example.shuolesa.data.model.AsrResult
import com.example.shuolesa.data.model.AsrSegment
import com.example.shuolesa.data.model.AsrSegments
import com.example.shuolesa.data.prefs.AiSettings
import com.example.shuolesa.data.prefs.ProviderPreset
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * v4.2: 阿里云百炼 (DashScope) native speech recognition, which returns sentence times and speaker numbers.
 *
 *  - 片段 (≤ 5 min): qwen-audio-3.x-asr-flash, synchronous. Audio goes inline as a Base64 data URL when it fits the
 *    10 MB request limit, otherwise through 百炼's temporary storage (oss://, kept 48 h by 百炼).
 *  - Whole meeting: the matching *-filetrans model (or fun-asr / paraformer), asynchronous: upload, submit, poll,
 *    then download the result JSON. Speaker numbers then hold across every 片段 of the meeting.
 *
 * The same API key serves the OpenAI-compatible chat endpoint used for 整理, so one 百炼 key covers both.
 * Every request goes through [AiClient.call], i.e. the data-outbound consent gate.
 * Field names follow the 百炼 HTTP API reference (2026-10); see docs/v4.2-research.html.
 */
internal class DashScopeAsr(private val client: AiClient, private val maxInlineBytes: Long=MAX_INLINE_BYTES,
                            private val pollMs: Long=3_000, private val maxWaitMs: Long=30*60_000L) {

    /** One 片段. */
    suspend fun transcribeChunk(config: AiSettings, file: File): AsrResult {
        prepare(config)
        val model=config.asrModel.trim()
        if(!isSyncModel(model)) {
            val segments=transcribeLong(config,file)
            return AsrResult(AsrSegments.text(segments),segments,scopeOf(segments,AsrSegments.SCOPE_SEGMENT))
        }
        val format=formatOf(file)
        val params=JSONObject().put("format",format)
        if(config.diarize && syncDiarization(model)) params.put("speaker_diarization_enabled",true)
        vocabulary(config,model)?.let { params.put("vocabulary",it) }
        val fitsInline=file.length()<=maxInlineBytes
        val ossUrl=if(fitsInline) null else upload(config,file,model)
        val body: RequestBody=if(ossUrl==null) {
            val head="{\"model\":${JSONObject.quote(model)},\"input\":{\"messages\":[{\"role\":\"user\",\"content\":" +
                "[{\"type\":\"input_audio\",\"input_audio\":{\"data\":\"data:${mimeOf(format)};base64,"
            Base64JsonBody(file,head,"\"}}]}]},\"parameters\":$params}")
        } else {
            JSONObject().put("model",model)
                .put("input",JSONObject().put("messages",JSONArray().put(JSONObject().put("role","user")
                    .put("content",JSONArray().put(JSONObject().put("type","input_audio")
                        .put("input_audio",JSONObject().put("data",ossUrl)))))))
                .put("parameters",params).toString().toRequestBody(JSON_TYPE)
        }
        val request=authorized(config,Request.Builder().url("${apiRoot(config.baseUrl)}$SYNC_PATH"))
            .header("X-DashScope-SSE","disable")
            .apply { if(ossUrl!=null) header(OSS_RESOLVE,"enable") }
            .post(body).build()
        return parseSync(client.call(request))
    }

    /** A whole recording (≤ 12 h; ≤ 2 h recommended with speaker separation). Times are relative to [file]. */
    suspend fun transcribeLong(config: AiSettings, file: File): List<AsrSegment> {
        prepare(config)
        val model=filetransModel(config.asrModel.trim()) ?: throw AiException(-1,"当前转写模型不支持整段转写")
        val url=upload(config,file,model)
        val params=JSONObject()
        if(config.diarize) params.put("diarization_enabled",true)
        vocabulary(config,model)?.let { params.put("vocabulary",it) }
        val submit=JSONObject().put("model",model)
            .put("input",JSONObject().put("file_urls",JSONArray().put(url))).put("parameters",params)
        val root=apiRoot(config.baseUrl)
        val accepted=json(client.call(authorized(config,Request.Builder().url("$root$ASYNC_PATH"))
            .header("X-DashScope-Async","enable").header(OSS_RESOLVE,"enable")
            .post(submit.toString().toRequestBody(JSON_TYPE)).build()))
        val taskId=accepted.optJSONObject("output")?.optString("task_id").orEmpty()
        if(taskId.isBlank()) throw AiException(-1,"转写任务没有返回编号")
        val started=System.currentTimeMillis()
        while(true) {
            delay(pollMs)
            val task=json(client.call(authorized(config,Request.Builder().url("$root/api/v1/tasks/$taskId")).get().build()))
            val output=task.optJSONObject("output") ?: throw AiException(-1,"转写任务状态不可读")
            when(output.optString("task_status")) {
                "SUCCEEDED" -> return result(output)
                "FAILED","CANCELED","UNKNOWN" -> {
                    if(noSpeech(output)) return emptyList()
                    throw AiException(-1,"转写任务失败："+output.optString("message").ifBlank { output.optString("code") }.take(80))
                }
            }
            // A dropped task is retried later with a fresh upload; nothing is half-saved.
            if(System.currentTimeMillis()-started>maxWaitMs) throw AiException(0,"转写任务等待超时，稍后会自动重试")
        }
    }

    private suspend fun result(output: JSONObject): List<AsrSegment> {
        val first=output.optJSONArray("results")?.optJSONObject(0) ?: throw AiException(-1,"转写任务没有结果")
        if(first.has("subtask_status") && first.optString("subtask_status")!="SUCCEEDED") {
            if(first.optString("code")==NO_VALID_FRAGMENT) return emptyList()
            throw AiException(-1,"转写失败："+first.optString("message").ifBlank { first.optString("code") }.take(80))
        }
        val url=first.optString("transcription_url")
        if(url.isBlank()) throw AiException(-1,"转写任务没有结果文件")
        // The result file sits on 百炼's own storage behind a signed link; no credential is sent.
        return parseFiletrans(client.call(Request.Builder().url(url).get().build(),long=true,maxBytes=MAX_RESULT_BYTES))
    }

    /** 百炼 temporary storage: get a policy, POST the file to OSS, refer to it as oss://<key> (valid 48 h). */
    private suspend fun upload(config: AiSettings, file: File, model: String): String {
        val policyUrl="${uploadRoot(config.baseUrl)}/api/v1/uploads".toHttpUrl().newBuilder()
            .addQueryParameter("action","getPolicy").addQueryParameter("model",model).build()
        val data=json(client.call(authorized(config,Request.Builder().url(policyUrl)).get().build())).optJSONObject("data")
            ?: throw AiException(-1,"没有拿到临时上传凭证")
        val maxMb=data.optLong("max_file_size_mb",0)
        if(maxMb>0 && file.length()>maxMb*1024*1024) throw AiException(413,"音频超出临时上传上限（${maxMb} MB）")
        val key=field(data,"upload_dir").trimEnd('/')+"/"+UUID.randomUUID()+"-"+file.name
        val form=MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("OSSAccessKeyId",field(data,"oss_access_key_id"))
            .addFormDataPart("Signature",field(data,"signature"))
            .addFormDataPart("policy",field(data,"policy"))
            .addFormDataPart("x-oss-object-acl",data.optString("x_oss_object_acl"))
            .addFormDataPart("x-oss-forbid-overwrite",data.optString("x_oss_forbid_overwrite"))
            .addFormDataPart("key",key)
            .addFormDataPart("success_action_status","200")
            .addFormDataPart("file",file.name,file.asRequestBody(mimeOf(formatOf(file)).toMediaType())) // must be last
            .build()
        client.call(Request.Builder().url(field(data,"upload_host")).post(form).build(),long=true)
        return "oss://$key"
    }

    private fun prepare(config: AiSettings) { checkCleartext(config.baseUrl);config.validate() }
    private fun authorized(config: AiSettings, builder: Request.Builder)=builder.apply {
        if(config.apiKey.isNotBlank()) header("Authorization","Bearer ${config.apiKey.trim()}")
    }

    companion object {
        const val MAX_INLINE_BYTES=7_000_000L // Base64 grows 4/3: ~9.4 MB, under the 10 MB request limit
        const val MAX_RESULT_BYTES=64L*1024*1024
        const val NO_VALID_FRAGMENT="SUCCESS_WITH_NO_VALID_FRAGMENT"
        private const val OSS_RESOLVE="X-DashScope-OssResourceResolve"
        private const val SYNC_PATH="/api/v1/services/aigc/multimodal-generation/generation"
        private const val ASYNC_PATH="/api/v1/services/audio/asr/transcription"
        private val JSON_TYPE="application/json".toMediaType()

        /** True for the 百炼 presets, or a DashScope host, with a model this adapter knows. */
        fun handles(config: AiSettings): Boolean {
            val model=config.asrModel.trim()
            return (isSyncModel(model) || filetransModel(model)!=null) && isDashScope(config)
        }
        /** The configured service is 百炼 (preset, or a DashScope / workspace host). */
        fun isDashScope(config: AiSettings): Boolean {
            if(config.provider==ProviderPreset.ALIYUN.id || config.provider==ProviderPreset.ALIYUN_INTL.id) return true
            val host=config.baseUrl.trim().toHttpUrlOrNull()?.host ?: return false
            return host=="dashscope.aliyuncs.com" || host=="dashscope-intl.aliyuncs.com" || host.endsWith(".maas.aliyuncs.com")
        }
        /** Synchronous short-audio models (≤ 5 min). */
        fun isSyncModel(model: String)=(model.startsWith("qwen-audio-") && model.endsWith("-asr-flash")) || model=="fun-asr-flash"
        /** Only qwen-audio 3.1 and later separate speakers in the synchronous call. */
        fun syncDiarization(model: String)=model.startsWith("qwen-audio-") && !model.startsWith("qwen-audio-3.0")
        /** The asynchronous model used for a whole recording, or null when there is none. */
        fun filetransModel(model: String): String?=when {
            model.startsWith("qwen-audio-") && model.endsWith("-asr-flash") -> "$model-filetrans"
            model.startsWith("qwen-audio-") && model.endsWith("-filetrans") -> model
            model=="fun-asr" || (model.startsWith("fun-asr-") && !model.startsWith("fun-asr-flash") && !model.contains("realtime")) -> model
            model.startsWith("paraformer-") && !model.contains("realtime") -> model
            else -> null
        }
        /** scheme://host[:port] of the configured address; the native APIs live beside /compatible-mode. */
        fun apiRoot(baseUrl: String): String=baseUrl.trim().toHttpUrl().newBuilder().encodedPath("/").query(null).fragment(null)
            .build().toString().trimEnd('/')
        /** Temporary storage is served from the regional dashscope host, also for workspace (maas) addresses. */
        fun uploadRoot(baseUrl: String): String {
            val host=baseUrl.trim().toHttpUrl().host
            return when {
                host.endsWith(".cn-beijing.maas.aliyuncs.com") -> "https://dashscope.aliyuncs.com"
                host.endsWith(".ap-southeast-1.maas.aliyuncs.com") -> "https://dashscope-intl.aliyuncs.com"
                else -> apiRoot(baseUrl)
            }
        }
        fun formatOf(file: File)=when(file.extension.lowercase()) {
            "mp3"->"mp3"; "ogg"->"ogg"; "opus"->"opus"; "aac"->"aac"; "m4a"->"m4a"; "pcm"->"pcm"; else->"wav" }
        fun mimeOf(format: String)=when(format) {
            "mp3"->"audio/mpeg"; "ogg","opus"->"audio/ogg"; "aac"->"audio/aac"; "m4a"->"audio/mp4"; "pcm"->"audio/pcm"; else->"audio/wav" }

        /** Hotwords as 百炼 instant vocabulary (weight 4 of 1–5). Only qwen-audio models accept it inline. */
        fun vocabulary(config: AiSettings, model: String): JSONObject? {
            if(!model.startsWith("qwen-audio-")) return null
            val words=config.hotwordList
            return if(words.isEmpty()) null else JSONObject().apply { words.forEach { put(it,4) } }
        }

        /** Synchronous response: output.sentences[] when speakers are separated, otherwise output.text. */
        fun parseSync(raw: String): AsrResult {
            val output=json(raw).optJSONObject("output") ?: throw AiException(-1,"转写响应没有结果字段")
            val segments=sentences(output.optJSONArray("sentences") ?: output.optJSONArray("sentence"))
            if(segments.isNotEmpty()) return AsrResult(AsrSegments.text(segments),segments,scopeOf(segments,AsrSegments.SCOPE_SEGMENT))
            if(output.has("text")) return AsrResult(output.optString("text").trim())
            val content=output.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.opt("content")
            val text=when(content) {
                is String -> content
                is JSONArray -> (0 until content.length()).joinToString("") { content.optJSONObject(it)?.optString("text").orEmpty() }
                else -> throw AiException(-1,"转写响应没有文字字段")
            }
            return AsrResult(text.trim())
        }

        /** Asynchronous result file: transcripts[0].sentences[] with begin_time / end_time (ms) and speaker_id. */
        fun parseFiletrans(raw: String): List<AsrSegment> {
            val channel=json(raw).optJSONArray("transcripts")?.optJSONObject(0) ?: return emptyList()
            val segments=sentences(channel.optJSONArray("sentences"))
            if(segments.isNotEmpty()) return segments
            val text=channel.optString("text").trim()
            return if(text.isEmpty()) emptyList() else listOf(AsrSegment(text,null,null))
        }

        /** Sentences with times; speaker_id is made 1-based (百炼 numbers speakers from 0). */
        private fun sentences(array: JSONArray?): List<AsrSegment> {
            if(array==null) return emptyList()
            val items=(0 until array.length()).mapNotNull { array.optJSONObject(it) }
            val ids=items.mapNotNull { o -> if(o.has("speaker_id") && !o.isNull("speaker_id")) o.optInt("speaker_id",-1).takeIf { it>=0 } else null }
            val shift=if(ids.isNotEmpty() && ids.min()==0) 1 else 0
            return items.mapNotNull { o ->
                val text=o.optString("text").trim()
                if(text.isEmpty()) return@mapNotNull null
                val speaker=if(o.has("speaker_id") && !o.isNull("speaker_id")) o.optInt("speaker_id",-1).takeIf { it>=0 }?.let { (it+shift).toString() } else null
                AsrSegment(text,o.optLong("begin_time",-1).takeIf { it>=0 },o.optLong("end_time",-1).takeIf { it>=0 },speaker)
            }
        }
        private fun scopeOf(segments: List<AsrSegment>, scope: String)=if(segments.any { it.speaker!=null }) scope else null
        private fun noSpeech(output: JSONObject)=output.optJSONArray("results")?.let { r ->
            (0 until r.length()).any { r.optJSONObject(it)?.optString("code")==NO_VALID_FRAGMENT } } ?: false
        private fun json(raw: String): JSONObject=try { JSONObject(raw) } catch(_: Exception) { throw AiException(-1,"转写服务返回格式不正确") }
        private fun field(o: JSONObject, name: String): String=o.optString(name).ifBlank { throw AiException(-1,"临时上传凭证缺少 $name") }
    }
}

/** Streams `head + Base64(file) + tail` without holding the file or its Base64 text in memory. */
internal class Base64JsonBody(private val file: File, head: String, tail: String): RequestBody() {
    private val prefix=head.toByteArray(Charsets.UTF_8)
    private val suffix=tail.toByteArray(Charsets.UTF_8)
    override fun contentType()="application/json".toMediaType()
    override fun contentLength(): Long=prefix.size+(file.length()+2)/3*4+suffix.size
    override fun writeTo(sink: BufferedSink) {
        sink.write(prefix)
        val out=object: java.io.OutputStream() {
            override fun write(b: Int) { sink.writeByte(b) }
            override fun write(b: ByteArray, off: Int, len: Int) { sink.write(b,off,len) }
            override fun close() { /* keep the OkHttp sink open */ }
        }
        val encoder=java.util.Base64.getEncoder().wrap(out)
        file.inputStream().use { input ->
            val buffer=ByteArray(48*1024)
            while(true) { val n=input.read(buffer); if(n<0) break; encoder.write(buffer,0,n) }
        }
        encoder.close()
        sink.write(suffix)
    }
}
