package com.example.shuolesa.data.repository

import com.example.shuolesa.data.db.AudioRecordDao
import com.example.shuolesa.data.db.AudioRecordEntity
import kotlinx.coroutines.flow.Flow

class AudioRepository(private val dao: AudioRecordDao) {

    fun observeAllRecords(): Flow<List<AudioRecordEntity>> = dao.observeAll()

    fun observePendingRecords(): Flow<List<AudioRecordEntity>> =
        dao.observeByStatus(AudioRecordEntity.STATUS_PENDING)

    fun observePendingCount(): Flow<Int> =
        dao.countByStatus(AudioRecordEntity.STATUS_PENDING)

    suspend fun insertRecord(record: AudioRecordEntity): Long = dao.insert(record)

    suspend fun getPendingUploads(maxRetries: Int = 5): List<AudioRecordEntity> =
        dao.getUploadCandidates(maxRetries)

    suspend fun markUploading(id: Long) =
        dao.updateStatus(id, AudioRecordEntity.STATUS_UPLOADING)

    suspend fun markUploaded(id: Long) =
        dao.updateStatus(id, AudioRecordEntity.STATUS_UPLOADED, System.currentTimeMillis())

    suspend fun markUploadedWithResult(id: Long, transcription: String?, agentResult: String?) =
        dao.markUploadedWithResult(id, AudioRecordEntity.STATUS_UPLOADED, System.currentTimeMillis(), transcription, agentResult)

    suspend fun markProcessedStructured(
        id: Long,
        title: String?,
        summary: String?,
        actionItems: String?,
        tags: String?,
        transcription: String?,
        agentResult: String?,
    ) = dao.markProcessedStructured(
        id = id,
        status = AudioRecordEntity.STATUS_UPLOADED,
        uploadedAt = System.currentTimeMillis(),
        title = title,
        summary = summary,
        actionItems = actionItems,
        tags = tags,
        transcription = transcription,
        agentResult = agentResult,
    )

    suspend fun markFailed(id: Long, reason: String? = null) {
        if (!reason.isNullOrBlank()) {
            dao.markFailedWithReason(
                id = id,
                status = AudioRecordEntity.STATUS_FAILED,
                title = "处理失败",
                summary = reason,
                agentResult = reason,
            )
        } else {
            dao.updateStatus(id, AudioRecordEntity.STATUS_FAILED)
        }
    }

    suspend fun incrementRetry(id: Long) = dao.incrementRetryCount(id)

    suspend fun deleteRecord(id: Long) = dao.deleteById(id)

    suspend fun cleanOldRecords(daysOld: Int = 30) {
        val cutoff = System.currentTimeMillis() - (daysOld.toLong() * 24 * 60 * 60 * 1000)
        val oldRecords = dao.getOldRecords(cutoff)
        oldRecords.forEach { dao.deleteById(it.id) }
    }
}
