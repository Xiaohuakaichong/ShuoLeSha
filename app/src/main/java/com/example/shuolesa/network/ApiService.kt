package com.example.shuolesa.network

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Data structure for parsed AI meeting/note summary.
 */
data class StructuredNotes(
    val title: String,
    val summary: String,
    val actionItems: List<String>,
    val tags: List<String>,
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

        private fun normalizeAsrUrl(rawUrl: String): String {
            val base = normalizeBaseUrl(rawUrl)
            val asrBase = if (base.contains("api.stepfun.com/step_plan")) {
                base.replace("step_plan/v1", "v1").replace("step_plan", "")
            } else {
                base
            }
            return "$asrBase/audio/transcriptions"
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
     * 转写音频文件。
     * 自动兼容阶跃星辰（自动路由至官方稳定 ASR 端点）、硅基流动及 OpenAI 兼容服务。
     */
    fun transcribeAudio(baseUrl: String, apiKey: String, asrModel: String, audioFile: File): Result<String> {
        return transcribeOpenAiMultipart(baseUrl, apiKey, asrModel, audioFile)
    }

    /**
     * 标准 OpenAI Multipart ASR 协议：POST /audio/transcriptions
     */
    private fun transcribeOpenAiMultipart(baseUrl: String, apiKey: String, asrModel: String, audioFile: File): Result<String> {
        return try {
            val url = if (baseUrl.contains("stepfun", ignoreCase = true)) {
                // 阶跃星辰语音识别官方稳定端点
                "https://api.stepfun.com/v1/audio/transcriptions"
            } else {
                "${normalizeBaseUrl(baseUrl)}/audio/transcriptions"
            }

            val mediaType = when {
                audioFile.name.endsWith(".wav", ignoreCase = true) -> "audio/wav".toMediaType()
                audioFile.name.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg".toMediaType()
                audioFile.name.endsWith(".m4a", ignoreCase = true) -> "audio/mp4".toMediaType()
                audioFile.name.endsWith(".opus", ignoreCase = true) -> "audio/opus".toMediaType()
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

            val actionItems = mutableListOf<String>()
            val actionsArray = json.optJSONArray("action_items")
                ?: json.optJSONArray("actionItems")
                ?: json.optJSONArray("todos")
                ?: json.optJSONArray("actions")
            if (actionsArray != null) {
                for (i in 0 until actionsArray.length()) {
                    val item = actionsArray.optString(i, "").trim()
                    if (item.isNotEmpty()) actionItems.add(item)
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

            StructuredNotes(
                title = title,
                summary = summary,
                actionItems = actionItems,
                tags = tags,
                rawJson = clean,
            )
        } catch (_: Exception) {
            // Fallback if model output is plain text
            StructuredNotes(
                title = "语音便签",
                summary = rawContent,
                actionItems = emptyList(),
                tags = listOf("便签"),
                rawJson = rawContent,
            )
        }
    }
}
