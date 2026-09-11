package com.example.shuolesa.network

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.shuolesa.data.db.AppDatabase
import com.example.shuolesa.data.model.ActionItemModel
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.repository.AudioRepository
import com.example.shuolesa.util.AppLogger
import com.example.shuolesa.util.AppScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
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
        private val uploadMutex = Mutex()

        /**
         * 调度后台 WorkManager（REPLACE 策略）并同时在前台 Application 协程中立即触发。
         * 双重保障：
         * 1. 解决国产系统因 gstatic.com 导致 WorkManager CONNECTED 约束被卡死的问题；
         * 2. 在 App 处于前台或存活状态时实现即录即转，零延迟处理。
         */
        fun enqueueOrRunNow(context: Context) {
            val appContext = context.applicationContext
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val uploadRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(appContext)
                    .enqueueUniqueWork(
                        WORK_NAME,
                        ExistingWorkPolicy.REPLACE,
                        uploadRequest,
                    )
                AppLogger.d(TAG, "WorkManager upload task enqueued (REPLACE)")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to enqueue WorkManager task", e)
            }

            // 立即在常驻 AppScope 协程中尝试执行，避免排队卡顿
            AppScope.launch(Dispatchers.IO) {
                try {
                    processPendingUploads(appContext)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "In-process upload execution error", e)
                }
            }
        }

        /**
         * 处理所有待处理上传记录
         */
        suspend fun processPendingUploads(appContext: Context): Result {
            if (!uploadMutex.tryLock()) {
                AppLogger.d(TAG, "Upload already in progress, skipping concurrent trigger")
                return Result.success()
            }
            return try {
                val (_, errorCount) = doProcessPendingUploads(appContext)
                if (errorCount == 0) Result.success() else Result.retry()
            } finally {
                uploadMutex.unlock()
            }
        }

        /**
         * 用于手动点击重试，返回 (成功条数, 失败条数)
         */
        suspend fun processPendingUploadsManual(appContext: Context): Pair<Int, Int> {
            uploadMutex.lock()
            return try {
                doProcessPendingUploads(appContext)
            } finally {
                uploadMutex.unlock()
            }
        }

        private suspend fun doProcessPendingUploads(appContext: Context): Pair<Int, Int> {
            AppLogger.d(TAG, "Starting doProcessPendingUploads...")
            val db = AppDatabase.getInstance(appContext)
            val repository = AudioRepository(db.audioRecordDao())
            val prefs = AppPreferences(appContext)
            val apiService = ApiService()

            val baseUrl = prefs.getBaseUrlSync()
            val apiKey = prefs.getApiKeySync()
            val asrModel = prefs.getAsrModelSync()
            val llmModel = prefs.getLlmModelSync()
            val systemPrompt = prefs.getSystemPromptSync()
            val keepLocalAudio = prefs.isKeepLocalAudioSync()

            val isLocalHost = baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1") ||
                baseUrl.contains("10.0.2.2") || baseUrl.contains("192.168.")

            AppLogger.d(TAG, "Upload config: baseUrl=$baseUrl, asrModel=$asrModel, llmModel=$llmModel, keyLength=${apiKey.length}, isLocalHost=$isLocalHost")

            if (apiKey.isBlank() && !isLocalHost) {
                AppLogger.w(TAG, "API Key is empty for cloud endpoint! Marking pending records as failed.")
                val pendingRecords = repository.getPendingUploads(MAX_RETRIES)
                for (record in pendingRecords) {
                    repository.markFailed(record.id, "未配置 API Key，请前往系统设置中配置后重试")
                }
                return Pair(0, pendingRecords.size)
            }

            val pendingRecords = repository.getPendingUploads(MAX_RETRIES)
            AppLogger.d(TAG, "Found ${pendingRecords.size} pending upload records in DB")
            if (pendingRecords.isEmpty()) {
                return Pair(0, 0)
            }

            var successCount = 0
            var errorCount = 0

            for (record in pendingRecords) {
                AppLogger.d(TAG, "Processing record #${record.id} (${record.recordingMode}, ${record.audioFormat}, path=${record.filePath})")
                val file = File(record.filePath)
                if (!file.exists()) {
                    AppLogger.w(TAG, "File not found: ${record.filePath}, marking as failed")
                    repository.markFailed(record.id, "音频文件不存在或已被清理")
                    errorCount++
                    continue
                }
                if (file.length() == 0L) {
                    AppLogger.w(TAG, "File is 0 bytes: ${record.filePath}, marking as failed")
                    repository.markFailed(record.id, "录音文件大小为 0 字节")
                    errorCount++
                    continue
                }

                repository.markUploading(record.id)
                AppLogger.d(TAG, "Record #${record.id} marked as UPLOADING, uploading to ASR...")

                // Step 1: Transcribe audio to text via OpenAI-compatible ASR
                val asrResult = apiService.transcribeAudio(baseUrl, apiKey, asrModel, file)
                if (asrResult.isFailure) {
                    val errorMsg = asrResult.exceptionOrNull()?.message ?: "ASR 语音识别失败"
                    AppLogger.e(TAG, "ASR failed for record #${record.id}: $errorMsg", asrResult.exceptionOrNull())
                    repository.incrementRetry(record.id)
                    repository.markFailed(record.id, errorMsg)
                    errorCount++
                    continue
                }

                val transcription = asrResult.getOrThrow()
                AppLogger.d(TAG, "ASR transcription success for record #${record.id} (${transcription.length} chars): ${transcription.take(60)}...")

                // Step 2: Extract structured title, summary, action items & tags via LLM
                AppLogger.d(TAG, "Sending transcription to LLM ($llmModel)...")
                val llmResult = apiService.generateStructuredNotes(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    llmModel = llmModel,
                    systemPrompt = systemPrompt,
                    transcription = transcription,
                )

                val notes = llmResult.getOrNull()
                val isMeeting = record.isMeeting()
                val defaultTitle = if (isMeeting) "会议纪要" else "随身生活记录"
                val title = notes?.title?.takeIf { it.isNotBlank() } ?: defaultTitle
                val summary = notes?.summary ?: transcription
                val actionItemsJson = notes?.actionItems?.let { ActionItemModel.toJsonString(it) }

                val tagsList = notes?.tags?.toMutableList() ?: mutableListOf()
                val modeTag = if (isMeeting) "会议" else "随身"
                if (!tagsList.contains(modeTag)) {
                    tagsList.add(0, modeTag)
                }
                val tagsJson = JSONArray(tagsList).toString()
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
                AppLogger.d(TAG, "Record #${record.id} successfully processed and saved as UPLOADED! Title: $title")
                successCount++

                // Step 3: Handle audio file retention
                if (!keepLocalAudio) {
                    file.delete()
                    AppLogger.d(TAG, "Deleted local audio file for record #${record.id}")
                }
            }

            return Pair(successCount, errorCount)
        }
    }

    override suspend fun doWork(): Result {
        return processPendingUploads(applicationContext)
    }
}
