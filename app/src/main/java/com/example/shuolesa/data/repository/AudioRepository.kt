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

    fun observeRecordById(id: Long): Flow<AudioRecordEntity?> = dao.observeById(id)

    suspend fun getRecordById(id: Long): AudioRecordEntity? = dao.getById(id)

    suspend fun updateActionItems(id: Long, actionItemsJson: String) =
        dao.updateActionItems(id, actionItemsJson)

    suspend fun getRecordsBetween(startTime: Long, endTime: Long): List<AudioRecordEntity> =
        dao.getRecordsBetween(startTime, endTime)

    suspend fun getRecordBySessionId(sessionId: String): AudioRecordEntity? =
        dao.getBySessionId(sessionId)

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

    suspend fun resetPendingAndFailed() = dao.resetPendingAndFailed()

    suspend fun deleteRecord(id: Long) {
        val record = dao.getById(id)
        if (record != null) deleteRecord(record) else dao.deleteById(id)
    }

    suspend fun deleteRecord(record: AudioRecordEntity) {
        dao.deleteById(record.id)
        val path = record.filePath
        if (path.isNotBlank()) {
            runCatching { java.io.File(path).takeIf { it.exists() }?.delete() }
        }
    }

    suspend fun cleanOldRecords(daysOld: Int = 30) {
        val cutoff = System.currentTimeMillis() - (daysOld.toLong() * 24 * 60 * 60 * 1000)
        val oldRecords = dao.getOldRecords(cutoff)
        oldRecords.forEach { dao.deleteById(it.id) }
    }

    /**
     * 本地全文检索：优先 FTS4，失败时降级 LIKE。
     * 空关键字返回空列表（由 UI 展示「请输入」空态）。
     */
    suspend fun searchRecords(keyword: String): List<AudioRecordEntity> {
        val q = keyword.trim()
        if (q.isEmpty()) return emptyList()
        val ftsQuery = buildFtsQuery(q)
        if (ftsQuery.isNotEmpty()) {
            try {
                return dao.searchByKeywordFts(ftsQuery)
            } catch (_: Exception) {
                // fall through to LIKE
            }
        }
        return dao.searchByKeywordLike(q)
    }

    companion object {
        /** 将用户输入转为较安全的 FTS4 前缀查询；去掉特殊运算符。 */
        fun buildFtsQuery(raw: String): String {
            val tokens = raw.trim()
                .split(Regex("\\s+"))
                .map { it.replace(Regex("""["*():^]"""), "") }
                .filter { it.isNotBlank() }
            if (tokens.isEmpty()) return ""
            // FTS4 前缀：token* ；多词默认 AND
            return tokens.joinToString(" ") { "$it*" }
        }
    }
}
