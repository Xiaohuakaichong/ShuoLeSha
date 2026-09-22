package com.example.shuolesa.network

import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.example.shuolesa.data.model.ActionItemModel
import com.example.shuolesa.data.model.MemoryAnswer
import com.example.shuolesa.data.model.MemorySegment
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Data structure for parsed AI meeting/note summary.
 */
data class StructuredNotes(
    val title: String,
    val summary: String,
    val actionItems: List<ActionItemModel>,
    val tags: List<String>,
    val structuredTranscript: String? = null,
    val rawJson: String,
)

/**
 * OpenAI-compatible HTTP client for speech-to-text (ASR)
 * and LLM notes summarization (Chat Completions).
 * Compatible with StepFun, SiliconFlow, Local FastAPI, and custom gateways.
 */
class ApiService {

    companion object {
        private const val TAG = "ApiService"

        private fun normalizeBaseUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim().trimEnd('/')
            return if (trimmed.endsWith("/v1")) trimmed else "$trimmed/v1"
        }

    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS) // generous for audio uploads
        .readTimeout(180, TimeUnit.SECONDS)  // generous for long speech transcription & reasoning
        .build()

    /**
     * Test API connection with a lightweight chat completion request.
     */
    fun testConnection(baseUrl: String, apiKey: String, llmModel: String): Result<String> {
        return try {
            val url = "${normalizeBaseUrl(baseUrl)}/chat/completions"
            val requestJson = JSONObject().apply {
                put("model", llmModel.ifBlank { "step-router-v1" })
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "hi")
                    })
                })
                put("max_tokens", 10)
            }.toString()

            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestJson.toRequestBody("application/json".toMediaType()))

            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                when {
                    response.code == 401 -> Result.failure(Exception("API Key 无效或已过期 (401 Unauthorized)"))
                    response.code == 404 -> Result.failure(Exception("端点未找到 (404 Not Found)，请检查 Base URL"))
                    response.isSuccessful -> Result.success("连接成功！服务响应正常。")
                    else -> Result.failure(Exception("HTTP 错误 ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            Result.failure(Exception("连接失败: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * OpenAI 兼容的模型目录：GET {baseUrl}/models。
     * 只返回 id，不区分转写和整理。
     */
    fun listModels(baseUrl: String, apiKey: String): Result<List<String>> {
        return try {
            val url = "${normalizeBaseUrl(baseUrl)}/models"
            val requestBuilder = Request.Builder().url(url).get()
            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }
            client.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = when (response.code) {
                        401 -> "API Key 无效 (401)"
                        404 -> "这个地址没有模型列表 (404)"
                        else -> "拉取失败 (${response.code})"
                    }
                    return Result.failure(Exception(message))
                }
                val data = try {
                    JSONObject(body).optJSONArray("data")
                } catch (_: Exception) {
                    null
                } ?: return Result.failure(Exception("返回里没有模型列表"))
                val ids = buildList {
                    for (i in 0 until data.length()) {
                        val id = data.optJSONObject(i)?.optString("id").orEmpty().trim()
                        if (id.isNotEmpty()) add(id)
                    }
                }.distinct().sorted()
                if (ids.isEmpty()) Result.failure(Exception("模型列表是空的")) else Result.success(ids)
            }
        } catch (e: Exception) {
            Log.e(TAG, "List models failed", e)
            Result.failure(Exception("拉取失败: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * 转写音频。Step Plan 没有 /audio/transcriptions，只能走套餐上的 SSE 接口，额度才扣 Credit。
     */
    fun transcribeAudio(baseUrl: String, apiKey: String, asrModel: String, audioFile: File): Result<String> {
        val base = normalizeBaseUrl(baseUrl)
        return if (base.contains("stepfun", ignoreCase = true) && base.contains("step_plan")) {
            transcribeStepPlanSse(base, apiKey, asrModel, audioFile)
        } else {
            transcribeOpenAiMultipart(baseUrl, apiKey, asrModel, audioFile)
        }
    }

    private fun transcribeStepPlanSse(
        baseUrl: String,
        apiKey: String,
        asrModel: String,
        audioFile: File,
    ): Result<String> {
        return try {
            val url = "$baseUrl/audio/asr/sse"
            val formatType = when {
                audioFile.name.endsWith(".wav", ignoreCase = true) -> "wav"
                audioFile.name.endsWith(".mp3", ignoreCase = true) -> "mp3"
                audioFile.name.endsWith(".ogg", ignoreCase = true) ||
                    audioFile.name.endsWith(".opus", ignoreCase = true) -> "ogg"
                audioFile.name.endsWith(".pcm", ignoreCase = true) -> "pcm"
                else -> "m4a"
            }
            val audio = JSONObject().apply {
                put("data", Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP))
                put(
                    "input",
                    JSONObject().apply {
                        put(
                            "transcription",
                            JSONObject().apply {
                                put("model", asrModel.ifBlank { "stepaudio-2.5-asr" })
                                put("language", "zh")
                                put("enable_itn", true)
                            },
                        )
                        put(
                            "format",
                            JSONObject().apply {
                                put("type", formatType)
                                if (formatType == "pcm") {
                                    put("codec", "pcm_s16le")
                                    put("rate", 16000)
                                    put("bits", 16)
                                    put("channel", 1)
                                }
                            },
                        )
                    },
                )
            }
            val body = JSONObject().put("audio", audio).toString()
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", "text/event-stream")
                .post(body.toRequestBody("application/json".toMediaType()))
            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }
            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Step Plan ASR failed ${response.code}: $bodyStr")
                    val errMsg = try {
                        JSONObject(bodyStr).optJSONObject("error")?.optString("message") ?: bodyStr
                    } catch (_: Exception) {
                        bodyStr
                    }
                    return Result.failure(Exception("语音识别失败 (${response.code}): $errMsg"))
                }
                var doneText = ""
                val deltas = StringBuilder()
                bodyStr.lineSequence().forEach { rawLine ->
                    val line = rawLine.trim()
                    if (!line.startsWith("data:")) return@forEach
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]" || !payload.startsWith("{")) return@forEach
                    val event = try {
                        JSONObject(payload)
                    } catch (_: Exception) {
                        return@forEach
                    }
                    when (event.optString("type")) {
                        "transcript.text.done" -> doneText = event.optString("text")
                        "transcript.text.delta" -> deltas.append(event.optString("delta"))
                    }
                }
                val text = doneText.ifBlank { deltas.toString() }.trim()
                if (text.isBlank()) {
                    Result.failure(Exception("音频未检测到清晰人声发言"))
                } else {
                    Result.success(text)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Step Plan ASR error", e)
            Result.failure(Exception("语音识别网络异常: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * 标准 OpenAI Multipart ASR 协议：POST {baseUrl}/audio/transcriptions
     */
    private fun transcribeOpenAiMultipart(baseUrl: String, apiKey: String, asrModel: String, audioFile: File): Result<String> {
        return try {
            val url = "${normalizeBaseUrl(baseUrl)}/audio/transcriptions"

            val mediaType = when {
                audioFile.name.endsWith(".wav", ignoreCase = true) -> "audio/wav".toMediaType()
                audioFile.name.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg".toMediaType()
                audioFile.name.endsWith(".m4a", ignoreCase = true) -> "audio/mp4".toMediaType()
                audioFile.name.endsWith(".aac", ignoreCase = true) -> "audio/aac".toMediaType()
                audioFile.name.endsWith(".flac", ignoreCase = true) -> "audio/flac".toMediaType()
                audioFile.name.endsWith(".ogg", ignoreCase = true) -> "audio/ogg".toMediaType()
                audioFile.name.endsWith(".opus", ignoreCase = true) -> "audio/opus".toMediaType()
                audioFile.name.endsWith(".amr", ignoreCase = true) -> "audio/amr".toMediaType()
                else -> "audio/wav".toMediaType()
            }

            val modelToUse = when {
                asrModel.isNotBlank() -> asrModel
                baseUrl.contains("stepfun", ignoreCase = true) -> "stepaudio-2.5-asr"
                baseUrl.contains("siliconflow", ignoreCase = true) -> "FunAudioLLM/SenseVoiceSmall"
                else -> "whisper-1"
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", modelToUse)
                .addFormDataPart("response_format", "json")
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody(mediaType)
                )
                .build()

            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestBody)

            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val text = try {
                        val json = JSONObject(bodyStr)
                        json.optString("text", "").ifEmpty {
                            json.optString("transcription", bodyStr)
                        }
                    } catch (_: Exception) {
                        bodyStr
                    }
                    if (text.isNotBlank()) {
                        Result.success(text.trim())
                    } else {
                        Result.failure(Exception("音频未检测到清晰人声发言"))
                    }
                } else {
                    Log.e(TAG, "ASR failed with code ${response.code}: $bodyStr")
                    val errMsg = try {
                        val json = JSONObject(bodyStr)
                        json.optJSONObject("error")?.optString("message") ?: bodyStr
                    } catch (_: Exception) {
                        bodyStr
                    }
                    Result.failure(Exception("语音识别失败 (${response.code}): $errMsg"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio transcription error", e)
            Result.failure(Exception("语音识别网络异常: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * Call LLM Chat Completion to analyze the transcription and extract
     * title, summary, action items, and tags in structured format.
     */
    fun generateStructuredNotes(
        baseUrl: String,
        apiKey: String,
        llmModel: String,
        systemPrompt: String,
        transcription: String
    ): Result<StructuredNotes> {
        return try {
            val url = "${normalizeBaseUrl(baseUrl)}/chat/completions"

            val userPrompt = """以下是录音的转录文字：
---
$transcription
---
请按照系统提示要求，输出符合格式的结构化 JSON。"""

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            }

            val requestJson = JSONObject().apply {
                put("model", llmModel.ifBlank { "step-router-v1" })
                put("messages", messages)
                put("temperature", 0.1)
                if (baseUrl.contains("stepfun", ignoreCase = true) || baseUrl.contains("siliconflow", ignoreCase = true) || baseUrl.contains("openai", ignoreCase = true)) {
                    put("response_format", JSONObject().put("type", "json_object"))
                }
            }.toString()

            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestJson.toRequestBody("application/json".toMediaType()))

            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val rootJson = JSONObject(respStr)
                    val choices = rootJson.optJSONArray("choices")
                    val message = choices?.optJSONObject(0)?.optJSONObject("message")
                    val content = message?.optString("content", "") ?: ""

                    val parsed = parseStructuredJson(content)
                    Result.success(parsed)
                } else {
                    Log.e(TAG, "LLM Chat completion failed: $respStr")
                    Result.failure(Exception("LLM 响应失败 (${response.code}): $respStr"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Structured notes extraction error", e)
            Result.failure(e)
        }
    }

    /**
     * 基于录音文稿进行智能问答追问 (Ask AI)
     */
    fun askQuestionAboutNote(
        baseUrl: String,
        apiKey: String,
        llmModel: String,
        transcription: String,
        question: String,
    ): Result<String> {
        return try {
            val url = "${normalizeBaseUrl(baseUrl)}/chat/completions"
            val systemPrompt = """你是一个智能随身语音笔记助手。
请基于用户提供的录音转录文稿，针对用户提出的问题进行客观、精准、简明的回答。
若录音内容中并未提及该问题相关的信息，请如实告知“录音中未明确提及”。回答直接切入要害，语言精练。"""

            val userContent = """【录音全文稿】：
$transcription

【用户追问】：
$question"""

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
            }

            val requestJson = JSONObject().apply {
                put("model", llmModel.ifBlank { "step-router-v1" })
                put("messages", messages)
                put("temperature", 0.3)
            }.toString()

            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestJson.toRequestBody("application/json".toMediaType()))

            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val root = JSONObject(respStr)
                    val choices = root.optJSONArray("choices")
                    val message = choices?.optJSONObject(0)?.optJSONObject("message")
                    val answer = message?.optString("content", "") ?: ""
                    Result.success(answer.trim())
                } else {
                    Log.e(TAG, "Ask question failed: $respStr")
                    Result.failure(Exception("AI 响应失败 (${response.code})"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ask question error", e)
            Result.failure(e)
        }
    }

    /**
     * 全天 LifeLog 跨录音综合复盘提炼
     */
    fun generateLifeLogSummary(
        baseUrl: String,
        apiKey: String,
        llmModel: String,
        dateStr: String,
        recordsContext: String,
    ): Result<com.example.shuolesa.data.model.LifeLogResult> {
        return try {
            val url = "${normalizeBaseUrl(baseUrl)}/chat/completions"
            val systemPrompt = """你是一位细腻、温暖且极富洞察力的私人生活观察家与每日复盘首席秘书。
用户在这一天中记录了多段语音，包括工作会议、与朋友的闲聊、琐碎自语和突发灵感。
请综合全天所有的录音内容，挖掘日常生活中的温情细节、人际互动趣事与重要行动，生成一份排版生动、富有记忆温度的【全天 LifeLog 生活手记】。

必须输出严格合法的纯 JSON 格式：
{
  "title": "$dateStr 生活手记 · <一句有画面感、有诗意或幽默的今日主题>",
  "summary": "全天生活与心境综述（100-250字，以第二人称‘你’或温暖的第一人称叙事，将今天的工作、交流与心境娓娓道来）",
  "timeline_highlights": [
    {"time": "时段（如 上午/午后/傍晚）", "title": "节点标题", "content": "发生了什么或说了什么精彩点"}
  ],
  "social_and_chats": "朋友与人际闲聊温情亮点（特别提炼与朋友聊到了什么趣事、彼此的吐槽或共鸣、关于生活/爱好/八卦的交流，若无闲聊则写'今天主要是专注自我与工作'）",
  "work_and_decisions": "工作推进与核心决议（会议达成的结果、讨论的关键点、推动的事务，若无工作内容则写'今天没有工作会议羁绊，纯粹属于生活'）",
  "unified_action_items": [
    {"text": "全天聚合待办1（清晰明确）", "done": false}
  ],
  "daily_quote": "基于今天一切经历提炼的今日心境金句/人生感悟"
}
注意：
1. 仔细提取散落在各段录音中的所有行动待办，合并归纳到 unified_action_items；
2. 敏锐捕捉人际闲聊中的温情与幽默，让闲聊像电影切片一样被永久记录；
3. 必须输出且仅输出合法标准 JSON，不要带有 markdown 标记或多余文字。"""

            val userContent = """【日期】：$dateStr
【今日录音记录与文稿汇总】：
$recordsContext

请生成今日的 LifeLog 全天复盘与聚合待办 JSON。"""

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
            }

            val requestJson = JSONObject().apply {
                put("model", llmModel.ifBlank { "step-router-v1" })
                put("messages", messages)
                put("temperature", 0.3)
                if (baseUrl.contains("stepfun", ignoreCase = true) || baseUrl.contains("siliconflow", ignoreCase = true) || baseUrl.contains("openai", ignoreCase = true)) {
                    put("response_format", JSONObject().put("type", "json_object"))
                }
            }.toString()

            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestJson.toRequestBody("application/json".toMediaType()))

            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val root = JSONObject(respStr)
                    val choices = root.optJSONArray("choices")
                    val message = choices?.optJSONObject(0)?.optJSONObject("message")
                    val content = message?.optString("content", "") ?: ""
                    val parsed = com.example.shuolesa.data.model.LifeLogResult.fromJson(content)
                    Result.success(parsed)
                } else {
                    Log.e(TAG, "LifeLog generation failed: $respStr")
                    Result.failure(Exception("LifeLog 提炼失败 (${response.code}): $respStr"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "LifeLog generation exception", e)
            Result.failure(e)
        }
    }

    /**
     * 把一篇转写按话题切成多段，并标出匿名说话人、决定、开放问题和带来源的待办。
     * 短录音由调用方直接落成一段，不必打到这里。
     */
    fun segmentTranscript(
        baseUrl: String,
        apiKey: String,
        llmModel: String,
        transcription: String,
    ): Result<List<MemorySegment>> {
        val clipped = if (transcription.length > 12000) {
            transcription.take(12000) + "\n…（后文已截断）"
        } else {
            transcription
        }
        val system = """你把一段录音转写切成可回顾的记忆片段。只输出 JSON：
{
  "segments": [
    {
      "title": "10字内标题",
      "summary": "这段在说什么，两句话以内",
      "transcript": "这一段对应的原话，尽量保留原句",
      "speakers": ["说话人 1", "说话人 2"],
      "decisions": ["已经定下来的事，没有就空数组"],
      "open_questions": ["没定下来的问题"],
      "action_items": [{"text": "待办", "quote": "原话里的那一句", "when": "提到的时间，没有就空字符串"}]
    }
  ]
}
规则：
1. 话题变了才新开一段。很短的独白只输出一段。
2. 说话人只用「说话人 1」「说话人 2」这种匿名编号。分不清就都算说话人 1。不要编真人姓名。
3. 没有说定的事不要写成 decisions。quote 必须能在原文里找到。
4. 只输出 JSON。"""
        val raw = completeChat(baseUrl, apiKey, llmModel, system, clipped, 0.1, jsonObject = true)
        return raw.map { content ->
            val parsed = MemorySegment.parse(content)
            if (parsed.isEmpty()) throw IllegalStateException("没有切出片段")
            parsed
        }
    }

    fun askAcrossRecords(
        baseUrl: String,
        apiKey: String,
        llmModel: String,
        catalog: String,
        question: String,
    ): Result<MemoryAnswer> {
        val system = """你根据用户自己的录音目录回答问题。只依据目录里写明的内容。
输出 JSON：{"answer":"直接回答","record_ids":[用到的记录 id]}
目录里没有的事就说没找到，record_ids 留空数组。不要编造。"""
        val user = "【目录】\n$catalog\n\n【问题】\n$question"
        return completeChat(baseUrl, apiKey, llmModel, system, user, 0.2, jsonObject = true).map { content ->
            val parsed = MemorySegment.parseAnswer(content)
            parsed ?: MemoryAnswer(content, emptyList())
        }
    }

    fun meetingFollowUp(
        baseUrl: String,
        apiKey: String,
        llmModel: String,
        transcription: String,
        title: String,
    ): Result<String> {
        val system = """你写一份会后跟进，只用录音里说过的话。输出 Markdown，按这个顺序，空的小节直接省略：
## 决定
## 我欠对方的
## 对方欠我的
## 开放问题
每一条尽量带上原话。没定下来的不要写成决定。不要写开场白。"""
        val user = "标题：$title\n\n$transcription"
        return completeChat(baseUrl, apiKey, llmModel, system, user.take(12000), 0.2, jsonObject = false)
    }

    private fun completeChat(
        baseUrl: String,
        apiKey: String,
        llmModel: String,
        systemPrompt: String,
        userContent: String,
        temperature: Double,
        jsonObject: Boolean,
    ): Result<String> {
        return try {
            val url = "${normalizeBaseUrl(baseUrl)}/chat/completions"
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
            }
            val requestJson = JSONObject().apply {
                put("model", llmModel.ifBlank { "step-router-v1" })
                put("messages", messages)
                put("temperature", temperature)
                if (jsonObject && (
                        baseUrl.contains("stepfun", ignoreCase = true) ||
                            baseUrl.contains("siliconflow", ignoreCase = true) ||
                            baseUrl.contains("openai", ignoreCase = true)
                        )
                ) {
                    put("response_format", JSONObject().put("type", "json_object"))
                }
            }.toString()
            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestJson.toRequestBody("application/json".toMediaType()))
            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }
            client.newCall(requestBuilder.build()).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Chat failed: $respStr")
                    Result.failure(Exception("LLM 响应失败 (${response.code})"))
                } else {
                    val content = JSONObject(respStr)
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content", "")
                        .orEmpty()
                    if (content.isBlank()) Result.failure(Exception("模型返回空内容"))
                    else Result.success(content.trim())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Chat error", e)
            Result.failure(e)
        }
    }

    private fun parseStructuredJson(rawContent: String): StructuredNotes {
        val clean = rawContent.trim()
        val fenceRegex = Regex("""```(?:json|JSON)?\s*([\s\S]*?)\s*```""")
        val fenceMatch = fenceRegex.find(clean)
        val jsonCandidate = if (fenceMatch != null) {
            fenceMatch.groupValues[1].trim()
        } else {
            val firstBrace = clean.indexOf('{')
            val lastBrace = clean.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace > firstBrace) {
                clean.substring(firstBrace, lastBrace + 1).trim()
            } else {
                clean
            }
        }

        return try {
            val json = JSONObject(jsonCandidate)
            val title = json.optString("title", "随手语音记录")
            val summary = json.optString("summary", "").ifBlank {
                json.optString("content", jsonCandidate)
            }

            val actionItems = mutableListOf<ActionItemModel>()
            val actionsArray = json.optJSONArray("action_items")
                ?: json.optJSONArray("actionItems")
                ?: json.optJSONArray("todos")
                ?: json.optJSONArray("actions")
            if (actionsArray != null) {
                for (i in 0 until actionsArray.length()) {
                    val item = actionsArray.opt(i)
                    when (item) {
                        is JSONObject -> {
                            val text = item.optString("text", item.optString("task", item.optString("title", ""))).trim()
                            val done = item.optBoolean("done", item.optBoolean("isDone", false))
                            if (text.isNotEmpty()) {
                                actionItems.add(ActionItemModel(text = text, isDone = done))
                            }
                        }
                        is String -> {
                            val text = item.trim()
                            if (text.isNotEmpty()) {
                                actionItems.add(ActionItemModel(text = text, isDone = false))
                            }
                        }
                    }
                }
            }

            val tags = mutableListOf<String>()
            val tagsArray = json.optJSONArray("tags")
                ?: json.optJSONArray("categories")
            if (tagsArray != null) {
                for (i in 0 until tagsArray.length()) {
                    val tag = tagsArray.optString(i, "").trim()
                    if (tag.isNotEmpty()) tags.add(tag)
                }
            }

            val structuredTranscript = json.optString("structured_transcript", "").ifBlank { null }

            StructuredNotes(
                title = title,
                summary = summary,
                actionItems = actionItems,
                tags = tags,
                structuredTranscript = structuredTranscript,
                rawJson = clean,
            )
        } catch (_: Exception) {
            // Fallback if model output is plain text
            StructuredNotes(
                title = "语音便签",
                summary = rawContent,
                actionItems = emptyList(),
                tags = listOf("便签"),
                structuredTranscript = null,
                rawJson = rawContent,
            )
        }
    }
}
