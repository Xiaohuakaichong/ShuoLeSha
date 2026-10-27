package com.example.shuolesa.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: RecordingSessionEntity): Long
    @Query("SELECT * FROM recording_sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<RecordingSessionEntity>>
    @Query("SELECT * FROM recording_sessions WHERE id=:id")
    suspend fun session(id: String): RecordingSessionEntity?
    @Query("UPDATE recording_sessions SET captureState=:state, endedAt=:endedAt, durationMs=:duration WHERE id=:id")
    suspend fun updateCapture(id: String, state: String, endedAt: Long?, duration: Long)
    @Query("UPDATE recording_sessions SET processingState=:state, error=:error WHERE id=:id")
    suspend fun updateProcessing(id: String, state: String, error: String? = null)
    @Query("UPDATE recording_sessions SET title=:title, processingState='READY', error=NULL WHERE id=:id")
    suspend fun markSessionReady(id: String, title: String)
    @Query("UPDATE recording_sessions SET sourceRevision=sourceRevision+1 WHERE id=:id")
    suspend fun incrementRevision(id: String)
    @Query("UPDATE recording_sessions SET captureState='INTERRUPTED', endedAt=:now WHERE captureState IN ('STARTING','RECORDING','PAUSED','STOPPING')")
    suspend fun recoverCapture(now: Long)
    @Query("DELETE FROM recording_sessions WHERE id=:id")
    suspend fun deleteSession(id: String)
    @Query("UPDATE recording_sessions SET speakerNamesJson=:json WHERE id=:id")
    suspend fun setSpeakerNames(id: String, json: String?)

    @Insert
    suspend fun insertMark(mark: CaptureMarkEntity): Long
    @Query("SELECT offsetMs FROM capture_marks WHERE sessionId=:id ORDER BY offsetMs")
    suspend fun marksForSession(id: String): List<Long>
    @Query("DELETE FROM capture_marks WHERE sessionId=:id")
    suspend fun deleteMarks(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTranscript(transcript: TranscriptEntity): Long
    @Query("SELECT * FROM transcripts WHERE rowid=:id")
    suspend fun transcript(id: Long): TranscriptEntity?
    @Query("SELECT * FROM transcripts")
    fun observeTranscripts(): Flow<List<TranscriptEntity>>

    @Query("SELECT rowid AS chunkId,provenance,createdAt,length(text) AS textLength FROM transcripts")
    fun observeTranscriptMeta(): Flow<List<TranscriptMeta>>
    @Query("SELECT t.rowid AS chunkId,t.text,t.provenance,a.createdAt,a.sessionId,a.recordingMode FROM transcripts t JOIN audio_records a ON a.id=t.rowid WHERE a.sessionId=:id ORDER BY a.chunkIndex,a.id")
    suspend fun sourcesForSession(id: String): List<TranscriptSource>
    @Query("SELECT t.rowid AS chunkId,t.text,t.provenance,a.createdAt,a.sessionId,a.recordingMode FROM transcripts t JOIN audio_records a ON a.id=t.rowid WHERE a.createdAt>=:start AND a.createdAt<:end ORDER BY a.createdAt")
    suspend fun sourcesForDay(start: Long, end: Long): List<TranscriptSource>
    /** v4.1.1: one query for the detail page instead of one full-text read per chunk. */
    @Query("SELECT t.rowid AS chunkId,t.segmentsJson FROM transcripts t JOIN audio_records a ON a.id=t.rowid WHERE a.sessionId=:id")
    suspend fun segmentsForSession(id: String): List<TranscriptSegments>
    @Query("SELECT t.rowid AS chunkId,t.text,t.provenance,a.createdAt,a.sessionId,a.recordingMode FROM transcripts t JOIN audio_records a ON a.id=t.rowid WHERE a.createdAt>=:start AND a.createdAt<:end AND (:mode='all' OR a.recordingMode=:mode) AND instr(lower(t.text),lower(:term))>0 ORDER BY a.createdAt DESC LIMIT :limit")
    suspend fun searchSubstring(term: String, start: Long, end: Long, mode: String, limit: Int = 60): List<TranscriptSource>
    @Query("SELECT t.rowid AS chunkId,t.text,t.provenance,a.createdAt,a.sessionId,a.recordingMode FROM transcripts t JOIN audio_records a ON a.id=t.rowid JOIN transcripts_fts f ON f.rowid=t.rowid WHERE transcripts_fts MATCH :term AND a.createdAt>=:start AND a.createdAt<:end AND (:mode='all' OR a.recordingMode=:mode) ORDER BY a.createdAt DESC LIMIT :limit")
    suspend fun searchFts(term: String, start: Long, end: Long, mode: String, limit: Int = 60): List<TranscriptSource>
    @Query("DELETE FROM transcripts WHERE rowid IN (SELECT id FROM audio_records WHERE sessionId=:sessionId)")
    suspend fun deleteTranscripts(sessionId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTopic(topic: MemoryTopicEntity): Long
    @Query("UPDATE memory_topics SET title=:title,summary=:summary,sourcesJson=:sources,revision=:revision WHERE id=:id")
    suspend fun updateTopicContent(id: String, title: String, summary: String, sources: String, revision: Int)

    @Query("UPDATE memory_topics SET title=:title,summary=:summary,sourcesJson=:sources,revision=:revision,actionsJson=:actions WHERE id=:id")
    suspend fun updateMappedTopic(id: String, title: String, summary: String, sources: String, revision: Int, actions: String)
    @Query("SELECT * FROM memory_topics WHERE hidden=0 ORDER BY createdAt DESC")
    fun observeTopics(): Flow<List<MemoryTopicEntity>>
    @Query("SELECT * FROM memory_topics WHERE sessionId=:id AND hidden=0 ORDER BY createdAt")
    suspend fun topics(id: String): List<MemoryTopicEntity>
    @Query("SELECT * FROM memory_topics WHERE id=:id")
    suspend fun topic(id: String): MemoryTopicEntity?
    @Query("UPDATE memory_topics SET hidden=:hidden WHERE id=:id")
    suspend fun setTopicHidden(id: String, hidden: Boolean)
    @Query("DELETE FROM memory_topics WHERE sessionId=:id")
    suspend fun deleteTopics(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNote(note: DerivedNoteEntity)
    @Query("SELECT * FROM derived_notes WHERE sessionId=:id")
    suspend fun note(id: String): DerivedNoteEntity?
    @Query("SELECT * FROM derived_notes")
    fun observeNotes(): Flow<List<DerivedNoteEntity>>
    @Query("DELETE FROM derived_notes WHERE sessionId=:id")
    suspend fun deleteNote(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDigest(digest: DailyDigestEntity)
    @Query("SELECT * FROM daily_digests")
    fun observeDigests(): Flow<List<DailyDigestEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTask(task: TaskEntity): Long
    @Query("SELECT * FROM action_tasks ORDER BY createdAt DESC")
    fun observeTasks(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM action_tasks WHERE sessionId=:id")
    suspend fun tasksForSession(id: String): List<TaskEntity>
    /** v5.0: for the 待办 widget (one-shot reads, newest first). */
    @Query("SELECT * FROM action_tasks WHERE state=:state ORDER BY createdAt DESC LIMIT :limit")
    suspend fun tasksByState(state: String, limit: Int): List<TaskEntity>
    @Query("SELECT COUNT(*) FROM action_tasks WHERE state=:state")
    suspend fun taskCount(state: String): Int
    @Query("UPDATE action_tasks SET state=:next WHERE id=:id AND state=:expected")
    suspend fun transitionTask(id: String, expected: String, next: String): Int
    @Query("UPDATE action_tasks SET sessionId=NULL,sourcesJson='[]',sourceDeleted=1 WHERE sessionId=:id AND state IN ('ACTIVE','DONE')")
    suspend fun detachConfirmedTasks(id: String)
    @Query("DELETE FROM action_tasks WHERE sessionId=:id")
    suspend fun deleteCandidates(id: String)
    @Query("DELETE FROM action_tasks WHERE sessionId=:id AND state='CANDIDATE'")
    suspend fun clearCandidates(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertJob(job: ProcessingJobEntity): Long
    @Query("SELECT * FROM processing_jobs WHERE state IN ('PENDING','FAILED') AND attempts<5 ORDER BY updatedAt")
    suspend fun pendingJobs(): List<ProcessingJobEntity>
    @Query("SELECT * FROM processing_jobs WHERE id=:id")
    suspend fun job(id: String): ProcessingJobEntity?
    @Query("SELECT id,kind,subjectId,state,attempts,error,owner,updatedAt,NULL AS resultJson FROM processing_jobs")
    fun observeJobs(): Flow<List<ProcessingJobEntity>>
    @Query("UPDATE processing_jobs SET state='RUNNING',owner=:owner,updatedAt=:now WHERE id=:id AND state IN ('PENDING','FAILED') AND attempts<5")
    suspend fun claimJob(id: String, owner: String, now: Long): Int
    @Query("UPDATE processing_jobs SET state=:state,error=:error,owner=NULL,attempts=attempts+:increment,updatedAt=:now WHERE id=:id AND owner=:owner")
    suspend fun finishJob(id: String, owner: String, state: String, error: String?, increment: Int, now: Long)
    @Query("UPDATE processing_jobs SET state='PENDING',owner=NULL WHERE state='RUNNING' AND (owner IS NULL OR owner!=:owner)")
    suspend fun recoverJobs(owner: String)
    @Query("UPDATE processing_jobs SET state='PENDING',attempts=0,error=NULL,owner=NULL WHERE state IN ('FAILED','WAITING_CONFIG','WAITING_NETWORK','WAITING_DEPENDENCY')")
    suspend fun retryJobs()
    /** v4.1: retry one record (单条重试): its session-level jobs and its chunks' ASR/MAP jobs. */
    @Query("UPDATE processing_jobs SET state='PENDING',attempts=0,error=NULL,owner=NULL WHERE state IN ('FAILED','WAITING_CONFIG','WAITING_NETWORK','WAITING_DEPENDENCY') AND (subjectId=:sessionId OR (kind IN ('ASR','MAP') AND subjectId IN (SELECT CAST(id AS TEXT) FROM audio_records WHERE sessionId=:sessionId)))")
    suspend fun retryJobsForSession(sessionId: String)

    @Query("UPDATE processing_jobs SET state='PENDING',error=NULL WHERE state='WAITING_NETWORK' OR (:configurationChanged AND state='WAITING_CONFIG')")
    suspend fun wakeWaiting(configurationChanged: Boolean)
    @Query("UPDATE processing_jobs SET state='PENDING',attempts=0,error=NULL,resultJson=NULL WHERE id=:id AND state!='RUNNING'")
    suspend fun resetJob(id: String)

    @Query("UPDATE processing_jobs SET resultJson=:result WHERE id=:id AND owner=:owner AND state='RUNNING'")
    suspend fun saveJobResult(id: String, owner: String, result: String)

    @Query("UPDATE processing_jobs SET state='PENDING',attempts=0,error=NULL,resultJson=:input WHERE id=:id AND state!='RUNNING'")
    suspend fun resetDigest(id: String, input: String)

    @Query("SELECT * FROM processing_jobs WHERE id IN (:ids)")
    suspend fun jobs(ids: List<String>): List<ProcessingJobEntity>
    @Query("DELETE FROM processing_jobs WHERE subjectId=:sessionId OR (kind IN ('ASR','MAP') AND subjectId IN (SELECT CAST(id AS TEXT) FROM audio_records WHERE sessionId=:sessionId))")
    suspend fun deleteJobs(sessionId: String)
    @Query("SELECT COUNT(*) FROM processing_jobs WHERE state='RUNNING' AND (subjectId=:sessionId OR (kind IN ('ASR','MAP') AND subjectId IN (SELECT CAST(id AS TEXT) FROM audio_records WHERE sessionId=:sessionId)))")
    suspend fun activeJobs(sessionId: String): Int
    @Query("UPDATE processing_jobs SET state='WAITING_DEPENDENCY',error='等待原稿分段整理完成' WHERE id=:id AND state IN ('PENDING','FAILED')")
    suspend fun waitDependencies(id: String)
    @Query("UPDATE processing_jobs SET state='PENDING',error=NULL WHERE state='WAITING_DEPENDENCY'")
    suspend fun wakeDependencies()
    @Query("UPDATE processing_jobs SET state='PENDING',error=NULL WHERE id=:id AND state='WAITING_NETWORK'")
    suspend fun wakeNetworkJob(id: String)
    /**
     * v4.1.1: latest finished MAP result for one chunk, any revision. Transcripts are immutable, so it maps
     * exactly the text a daily digest would map again; the digest reuses it instead of another model call.
     */
    @Query("SELECT resultJson FROM processing_jobs WHERE kind='MAP' AND subjectId=:chunkId AND state='COMPLETE' AND resultJson IS NOT NULL ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestMapResult(chunkId: String): String?
}
