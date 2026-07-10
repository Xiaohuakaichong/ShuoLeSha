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

    suspend fun getPendingUploads(): List<AudioRecordEntity> =
        dao.getByStatus(AudioRecordEntity.STATUS_PENDING)

    suspend fun markUploading(id: Long) =
        dao.updateStatus(id, AudioRecordEntity.STATUS_UPLOADING)

    suspend fun markUploaded(id: Long) =
        dao.updateStatus(id, AudioRecordEntity.STATUS_UPLOADED, System.currentTimeMillis())

    suspend fun markUploadedWithResult(id: Long, transcription: String?, agentResult: String?) =
        dao.markUploadedWithResult(id, AudioRecordEntity.STATUS_UPLOADED, System.currentTimeMillis(), transcription, agentResult)

    suspend fun markFailed(id: Long) {
        dao.updateStatus(id, AudioRecordEntity.STATUS_FAILED)
    }

    suspend fun incrementRetry(id: Long) = dao.incrementRetryCount(id)

    suspend fun deleteRecord(id: Long) = dao.deleteById(id)

    suspend fun cleanOldRecords(daysOld: Int = 30) {
        val cutoff = System.currentTimeMillis() - (daysOld.toLong() * 24 * 60 * 60 * 1000)
        val oldRecords = dao.getOldRecords(cutoff)
        oldRecords.forEach { dao.deleteById(it.id) }
    }
}
