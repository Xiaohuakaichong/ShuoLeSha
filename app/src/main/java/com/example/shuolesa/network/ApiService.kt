package com.example.shuolesa.network

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * HTTP client for Coze platform communication.
 * Handles file uploads and workflow execution.
 */
class ApiService {

    companion object {
        private const val TAG = "ApiService"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS) // generous for audio uploads
        .readTimeout(120, TimeUnit.SECONDS) // generous for LLM/workflow response times
        .build()

    /**
     * Test Coze API Key credentials.
     * Attempts to upload a 4-byte dummy file to verify authorization.
     */
    fun testCozeConnection(baseUrl: String, apiKey: String): Result<String> {
        return try {
            val url = "${baseUrl.trimEnd('/')}/v1/files/upload"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "test.txt",
                    "test".toRequestBody("text/plain".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    Result.failure(Exception("Invalid API Key (Unauthorized)"))
                } else if (response.isSuccessful) {
                    Result.success("Success")
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Coze connection test failed", e)
            Result.failure(e)
        }
    }

    /**
     * Upload an audio file to Coze.
     * Returns the parsed file_id.
     */
    fun uploadCozeFile(baseUrl: String, apiKey: String, file: File): Result<String> {
        return try {
            val url = "${baseUrl.trimEnd('/')}/v1/files/upload"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    file.name,
                    file.asRequestBody("audio/opus".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respStr = response.body?.string() ?: ""
                    val json = org.json.JSONObject(respStr)
                    val code = json.optInt("code", -1)
                    if (code == 0) {
                        val data = json.optJSONObject("data")
                        val fileId = data?.optString("id", "") ?: ""
                        if (fileId.isNotEmpty()) {
                            Result.success(fileId)
                        } else {
                            Result.failure(Exception("File ID missing in Coze response"))
                        }
                    } else {
                        val msg = json.optString("msg", "Unknown error")
                        Result.failure(Exception("Coze Error $code: $msg"))
                    }
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Coze file upload failed", e)
            Result.failure(e)
        }
    }

    /**
     * Run a Coze workflow with the uploaded audio file ID.
     * Returns the raw output string from Coze workflow run.
     */
    fun runCozeWorkflow(baseUrl: String, apiKey: String, workflowId: String, fileId: String): Result<String> {
        return try {
            val url = "${baseUrl.trimEnd('/')}/v1/workflow/run"
            
            // Constructs the parameters JSON matching Coze schema
            val jsonBody = org.json.JSONObject().apply {
                put("workflow_id", workflowId)
                put("parameters", org.json.JSONObject().apply {
                    put("audio_file", "${baseUrl.trimEnd('/')}/v1/files/download?file_id=$fileId")
                    put("audio_file_id", fileId)
                })
            }.toString()

            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respStr = response.body?.string() ?: ""
                    val json = org.json.JSONObject(respStr)
                    val code = json.optInt("code", -1)
                    if (code == 0) {
                        val data = json.optString("data", "")
                        Result.success(data)
                    } else {
                        val msg = json.optString("msg", "Unknown error")
                        Result.failure(Exception("Coze Workflow Error $code: $msg"))
                    }
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Coze workflow run failed", e)
            Result.failure(e)
        }
    }
}
