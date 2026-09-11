package com.example.shuolesa.network

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.shuolesa.data.db.AppDatabase
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.repository.AudioRepository
import java.io.File

/**
 * WorkManager worker for background audio upload.
 * Runs when network becomes available, uploads pending chunks serially.
 */
class UploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "UploadWorker"
        const val WORK_NAME = "audio_upload_work"
        const val MAX_RETRIES = 5
    }

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val repository = AudioRepository(db.audioRecordDao())
        val prefs = AppPreferences(applicationContext)
        val apiService = ApiService()

        val baseUrl = prefs.getBaseUrlSync()
        val apiKey = prefs.getApiKeySync()
        val asrModel = prefs.getAsrModelSync()
        val llmModel = prefs.getLlmModelSync()
        val systemPrompt = prefs.getSystemPromptSync()
        val keepLocalAudio = prefs.isKeepLocalAudioSync()

        val isLocalHost = baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1") ||
            baseUrl.contains("10.0.2.2") || baseUrl.contains("192.168.")

        if (apiKey.isBlank() && !isLocalHost) {
            Log.w(TAG, "API Key is empty for cloud endpoint, skipping upload")
            return Result.success()
        }

        val pendingRecords = repository.getPendingUploads(MAX_RETRIES)
        if (pendingRecords.isEmpty()) {
            Log.d(TAG, "No pending uploads")
            return Result.success()
        }

        var allSuccess = true
        for (record in pendingRecords) {
            val file = File(record.filePath)
            if (!file.exists()) {
                Log.w(TAG, "File not found: ${record.filePath}, marking as failed")
                repository.markFailed(record.id, "音频文件不存在或已被清理")
                continue
            }

            repository.markUploading(record.id)

            // Step 1: Transcribe audio to text via OpenAI-compatible ASR
            val asrResult = apiService.transcribeAudio(baseUrl, apiKey, asrModel, file)
            if (asrResult.isFailure) {
                val errorMsg = asrResult.exceptionOrNull()?.message ?: "ASR 语音识别失败"
                Log.e(TAG, "ASR failed for record ${record.id}: $errorMsg", asrResult.exceptionOrNull())
                repository.incrementRetry(record.id)
                repository.markFailed(record.id, errorMsg)
                allSuccess = false
                continue
            }

            val transcription = asrResult.getOrThrow()
            Log.d(TAG, "ASR transcription success: $transcription")

            // Step 2: Extract structured title, summary, action items & tags via LLM
            val llmResult = apiService.generateStructuredNotes(
                baseUrl = baseUrl,
                apiKey = apiKey,
                llmModel = llmModel,
                systemPrompt = systemPrompt,
                transcription = transcription,
            )

            val notes = llmResult.getOrNull()
            val title = notes?.title ?: "随手语音"
            val summary = notes?.summary ?: transcription
            val actionItemsJson = notes?.actionItems?.let { com.example.shuolesa.data.model.ActionItemModel.toJsonString(it) }
            val tagsJson = notes?.tags?.let { org.json.JSONArray(it).toString() }
            val rawJson = notes?.rawJson ?: transcription
            val finalTranscription = notes?.structuredTranscript?.takeIf { it.isNotBlank() } ?: transcription

            repository.markProcessedStructured(
                id = record.id,
                title = title,
                summary = summary,
                actionItems = actionItemsJson,
                tags = tagsJson,
                transcription = finalTranscription,
                agentResult = rawJson,
            )

            // Step 3: Handle audio file retention
            if (!keepLocalAudio) {
                file.delete()
            }
        }

        return if (allSuccess) Result.success() else Result.retry()
    }
}
