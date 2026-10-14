package com.example.shuolesa.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioRecordDao {

    @Query("SELECT * FROM audio_records WHERE sessionId=:id AND status!='SOURCE' ORDER BY chunkIndex,id")
    suspend fun chunks(id: String): List<AudioRecordEntity>

    @Query("SELECT * FROM audio_records WHERE status='WRITING'")
    suspend fun unsealedChunks(): List<AudioRecordEntity>

    @Query("UPDATE audio_records SET durationMs=:duration,fileSizeBytes=:size,status=:status WHERE id=:id")
    suspend fun sealChunk(id: Long, duration: Long, size: Long, status: String = "PENDING")

    @Query("DELETE FROM audio_records WHERE sessionId=:id")
    suspend fun deleteSessionChunks(id: String)

    @Query("UPDATE audio_records SET status='PENDING' WHERE status='UPLOADING'")
    suspend fun recoverUploads()

    @Query("UPDATE audio_records SET filePath=:path WHERE id=:id")
    suspend fun updateFilePath(id: Long, path: String)

    @Query("UPDATE audio_records SET sourceEndMs=:end WHERE id=:id")
    suspend fun setSourceEnd(id: Long, end: Long)

    @Query("""SELECT id,sessionId,chunkIndex,filePath,durationMs,fileSizeBytes,createdAt,status,
        uploadedAt,retryCount,NULL AS title,NULL AS summary,NULL AS actionItems,NULL AS tags,
        NULL AS transcription,NULL AS agentResult,recordingMode,audioFormat,NULL AS segmentsJson,
        sourceStartMs,sourceEndMs FROM audio_records WHERE filePath!='' ORDER BY createdAt DESC""")
    fun observeChunkMetadata(): Flow<List<AudioRecordEntity>>

    @Insert
    suspend fun insert(record: AudioRecordEntity): Long

    @Query("SELECT * FROM audio_records WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getByStatus(status: String): List<AudioRecordEntity>

    @Query(
        """
        SELECT * FROM audio_records
        WHERE status IN ('PENDING', 'FAILED') AND retryCount < :maxRetries
        ORDER BY createdAt ASC
        """,
    )
    suspend fun getUploadCandidates(maxRetries: Int): List<AudioRecordEntity>

    @Query("SELECT * FROM audio_records WHERE status = :status ORDER BY createdAt ASC")
    fun observeByStatus(status: String): Flow<List<AudioRecordEntity>>

    @Query("SELECT * FROM audio_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AudioRecordEntity>>

    @Query("SELECT * FROM audio_records ORDER BY createdAt DESC")
    suspend fun getAll(): List<AudioRecordEntity>

    @Query("UPDATE audio_records SET status = :status, uploadedAt = :uploadedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, uploadedAt: Long? = null)

    @Query("UPDATE audio_records SET status = :status, uploadedAt = :uploadedAt, transcription = :transcription, agentResult = :agentResult WHERE id = :id")
    suspend fun markUploadedWithResult(id: Long, status: String, uploadedAt: Long?, transcription: String?, agentResult: String?)

    @Query(
        """
        UPDATE audio_records 
        SET status = :status, uploadedAt = :uploadedAt, title = :title, summary = :summary, 
            actionItems = :actionItems, tags = :tags, transcription = :transcription, agentResult = :agentResult 
        WHERE id = :id
        """
    )
    suspend fun markProcessedStructured(
        id: Long,
        status: String,
        uploadedAt: Long?,
        title: String?,
        summary: String?,
        actionItems: String?,
        tags: String?,
        transcription: String?,
        agentResult: String?,
    )

    @Query("UPDATE audio_records SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetryCount(id: Long)

    @Query("UPDATE audio_records SET status = 'PENDING', retryCount = 0 WHERE status IN ('FAILED', 'PENDING')")
    suspend fun resetPendingAndFailed()

    @Query("UPDATE audio_records SET status = :status, title = :title, summary = :summary, agentResult = :agentResult WHERE id = :id")
    suspend fun markFailedWithReason(
        id: Long,
        status: String = AudioRecordEntity.STATUS_FAILED,
        title: String?,
        summary: String?,
        agentResult: String?,
    )

    @Query("DELETE FROM audio_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM audio_records WHERE createdAt < :beforeTimestamp AND status = :status")
    suspend fun getOldRecords(beforeTimestamp: Long, status: String = AudioRecordEntity.STATUS_UPLOADED): List<AudioRecordEntity>

    @Query("SELECT COUNT(*) FROM audio_records WHERE status = :status")
    fun countByStatus(status: String): Flow<Int>

    @Query("SELECT * FROM audio_records WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<AudioRecordEntity?>

    @Query("SELECT * FROM audio_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AudioRecordEntity?

    @Query("UPDATE audio_records SET actionItems = :actionItems WHERE id = :id")
    suspend fun updateActionItems(id: Long, actionItems: String?)

    @Query("UPDATE audio_records SET segmentsJson = :segmentsJson WHERE id = :id")
    suspend fun updateSegments(id: Long, segmentsJson: String)

    @Query("SELECT * FROM audio_records WHERE createdAt >= :startTime AND createdAt <= :endTime ORDER BY createdAt ASC")
    suspend fun getRecordsBetween(startTime: Long, endTime: Long): List<AudioRecordEntity>

    @Query("SELECT * FROM audio_records WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getBySessionId(sessionId: String): AudioRecordEntity?
}
