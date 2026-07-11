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

        val cozeBaseUrl = prefs.getCozeBaseUrlSync()
        val cozeApiKey = prefs.getCozeApiKeySync()
        val cozeWorkflowId = prefs.getCozeWorkflowIdSync()

        if (cozeApiKey.isBlank() || cozeWorkflowId.isBlank()) {
            Log.w(TAG, "Coze API Key or Workflow ID not configured, skipping upload")
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
                repository.markFailed(record.id)
                continue
            }

            repository.markUploading(record.id)

            // Step 1: Upload file to Coze to get fileId
            val uploadResult = apiService.uploadCozeFile(cozeBaseUrl, cozeApiKey, file)
            if (uploadResult.isSuccess) {
                val fileId = uploadResult.getOrThrow()
                Log.d(TAG, "Uploaded file to Coze: ${file.name}, fileId: $fileId")

                // Step 2: Run Coze workflow using the fileId
                val workflowResult = apiService.runCozeWorkflow(cozeBaseUrl, cozeApiKey, cozeWorkflowId, fileId)
                if (workflowResult.isSuccess) {
                    Log.d(TAG, "Successfully ran Coze workflow for: ${file.name}")
                    val responseStr = workflowResult.getOrNull()
                    var trans: String? = null
                    var agentRes: String? = null
                    if (!responseStr.isNullOrBlank()) {
                        try {
                            val json = org.json.JSONObject(responseStr)
                            // Workflow may return nested JSON string in "data"
                            val payload = try {
                                val inner = org.json.JSONObject(responseStr)
                                inner
                            } catch (_: Exception) {
                                json
                            }
                            trans = payload.optString("transcription").takeIf { it.isNotEmpty() }
                            agentRes = payload.optString("agentResult").takeIf { it.isNotEmpty() }
                                ?: payload.optString("result").takeIf { it.isNotEmpty() }
                        } catch (_: Exception) {
                            agentRes = responseStr
                        }
                    }
                    repository.markUploadedWithResult(record.id, trans, agentRes)
                    // Delete local file after successful upload
                    file.delete()
                } else {
                    Log.e(TAG, "Coze workflow run failed: ${file.name}", workflowResult.exceptionOrNull())
                    repository.incrementRetry(record.id)
                    repository.markFailed(record.id)
                    allSuccess = false
                }
            } else {
                Log.e(TAG, "Coze upload failed: ${file.name}", uploadResult.exceptionOrNull())
                repository.incrementRetry(record.id)
                repository.markFailed(record.id)
                allSuccess = false
            }
        }

        return if (allSuccess) Result.success() else Result.retry()
    }
}
