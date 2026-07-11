package com.example.shuolesa.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioRecordDao {

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

    @Query("UPDATE audio_records SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetryCount(id: Long)

    @Query("DELETE FROM audio_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM audio_records WHERE createdAt < :beforeTimestamp AND status = :status")
    suspend fun getOldRecords(beforeTimestamp: Long, status: String = AudioRecordEntity.STATUS_UPLOADED): List<AudioRecordEntity>

    @Query("SELECT COUNT(*) FROM audio_records WHERE status = :status")
    fun countByStatus(status: String): Flow<Int>
}
