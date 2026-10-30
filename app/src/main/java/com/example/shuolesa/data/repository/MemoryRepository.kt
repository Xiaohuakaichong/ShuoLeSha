package com.example.shuolesa.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.shuolesa.data.db.*
import com.example.shuolesa.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.shareIn
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

data class MemorySnapshot(
    val sessions: List<RecordingSessionEntity> = emptyList(),
    val chunks: List<AudioRecordEntity> = emptyList(),
    val transcripts: List<TranscriptMeta> = emptyList(),
    val topics: List<MemoryTopicEntity> = emptyList(),
    val notes: List<DerivedNoteEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val digests: List<DailyDigestEntity> = emptyList(),
    val jobs: List<ProcessingJobEntity> = emptyList(),
)

class MemoryRepository(val database: AppDatabase, private val context: Context, scope: CoroutineScope? = null) {
    val dao = database.memoryDao()
    val audio = database.audioRecordDao()
    private val liveSnapshots = combine(
        combine(dao.observeSessions(), audio.observeChunkMetadata(), dao.observeTranscriptMeta(), dao.observeTopics()) { a,b,c,d ->
            MemorySnapshot(sessions=a,chunks=b,transcripts=c,topics=d)
        },
        combine(dao.observeNotes(),dao.observeTasks(),dao.observeDigests(),dao.observeJobs()) { a,b,c,d ->
            MemorySnapshot(notes=a,tasks=b,digests=c,jobs=d)
        },
    ) { a,b -> a.copy(notes=b.notes,tasks=b.tasks,digests=b.digests,jobs=b.jobs) }
    /**
     * v4.1.1: one shared upstream for every screen. Each ViewModel's stateIn used to start its own copy of the
     * eight Room queries, so one table change re-ran them once per open screen. Without a scope (tests) the
     * flow stays cold.
     */
    val snapshots: Flow<MemorySnapshot> = scope?.let {
        liveSnapshots.shareIn(it, SharingStarted.WhileSubscribed(stopTimeoutMillis=5_000, replayExpirationMillis=0), replay=1)
    } ?: liveSnapshots

    suspend fun createSession(session: RecordingSessionEntity) = dao.insertSession(session)
    /** v5.2: refuses a 片段 for a record that was deleted meanwhile (an import still running when it was deleted). */
    suspend fun registerChunk(chunk: AudioRecordEntity): Long = database.withTransaction {
        check(dao.session(chunk.sessionId)!=null) { "这条记录已删除" }
        audio.insert(chunk)
    }
    suspend fun sealChunk(id: Long, duration: Long, size: Long, processNow: Boolean) {
        database.withTransaction {
            val compressed=audio.getById(id)?.audioFormat?.startsWith("aac")==true
            val hasSamples=duration>0 && size>(if(compressed) 7 else 44)
            audio.sealChunk(id, duration, size, if (hasSamples) "PENDING" else "EMPTY")
            if (processNow && hasSamples) queueAsr(id)
        }
    }

    suspend fun closeSession(id: String, duration: Long, interrupted: Boolean = false) {
        database.withTransaction {
            dao.updateCapture(id, if(interrupted) "INTERRUPTED" else "CLOSED", System.currentTimeMillis(),duration)
            val chunks=audio.chunks(id)
            chunks.filter { it.status == "PENDING" }.forEach { queueAsr(it.id) }
            if(chunks.isEmpty() || chunks.all {it.status=="EMPTY"}) dao.updateProcessing(id,"EMPTY")
        }
    }
    suspend fun queueAsr(id: Long) = dao.insertJob(ProcessingJobEntity("asr:$id","ASR",id.toString()))
    suspend fun queueDerive(sessionId: String, regenerate: Boolean = false) {
        val session = dao.session(sessionId) ?: return
        if (session.captureState !in listOf("CLOSED","INTERRUPTED")) return
        if(dao.job("import:$sessionId")?.state?.let { it!="COMPLETE" }==true) return
        val chunks = audio.chunks(sessionId).filter { it.status != "EMPTY" && it.status != "WRITING" }
        if (chunks.isEmpty() || chunks.any { dao.transcript(it.id) == null }) return
        chunks.forEach { chunk ->
            val mapKey="map:${chunk.id}:${session.sourceRevision}"
            dao.insertJob(ProcessingJobEntity(mapKey,"MAP",chunk.id.toString()))
            if(regenerate) dao.resetJob(mapKey)
        }
        val key="derive:$sessionId:${session.sourceRevision}"
        dao.insertJob(ProcessingJobEntity(key,"DERIVE",sessionId))
        if(regenerate) dao.resetJob(key)
        dao.updateProcessing(sessionId,"DERIVING")
    }
    /** [segmentsJson] (v4.2): sentence times / speakers in the AsrSegments.toJson format, or null. */
    suspend fun saveTranscript(chunk: AudioRecordEntity, text: String, segmentsJson: String? = null) = database.withTransaction {
        insertTranscript(chunk, text, segmentsJson)
    }

    /**
     * v4.2: transcripts of several 片段 from one diarized pass over a meeting, saved together so the meeting never
     * shows a mix of speaker numbering. chunkId -> (原话, segmentsJson). An existing transcript is never replaced.
     */
    suspend fun saveTranscripts(results: Map<Long, Pair<String, String?>>) = database.withTransaction {
        results.forEach { (id, result) -> audio.getById(id)?.let { insertTranscript(it, result.first, result.second) } }
    }

    private suspend fun insertTranscript(chunk: AudioRecordEntity, text: String, segmentsJson: String?) {
        if(dao.session(chunk.sessionId)==null || audio.getById(chunk.id)==null) return
        if (dao.insertTranscript(TranscriptEntity(chunk.id,text,"ASR",System.currentTimeMillis(),segmentsJson)) != -1L) {
            dao.incrementRevision(chunk.sessionId)
        }
        audio.updateStatus(chunk.id, "UPLOADED", System.currentTimeMillis())
    }

    suspend fun saveDerived(session: RecordingSessionEntity, draft: SessionDraft) = database.withTransaction {
        if(dao.session(session.id)?.sourceRevision!=session.sourceRevision) return@withTransaction
        val allowed=dao.sourcesForSession(session.id).associateBy {it.chunkId}
        require(draft.topics.flatMap {it.sources+it.actions.flatMap {a->a.sources}}.all { ref->allowed[ref.chunkId]?.text?.let(ref::isValidFor)==true }) { "整理结果包含无效来源" }
        val previous = dao.tasksForSession(session.id).filter {it.state!=TaskState.CANDIDATE}.toMutableList()
        dao.clearCandidates(session.id)
        draft.topics.forEach { topic ->
            val sourceKey = topic.sources.joinToString("|") { "${it.chunkId}:${it.start}:${it.end}" }
            val id = stableId("topic",session.id,sourceKey)
            val created = topic.sources.firstOrNull()?.let { audio.getById(it.chunkId)?.createdAt } ?: session.startedAt
            val entity = MemoryTopicEntity(id,session.id,topic.title,topic.summary,EvidenceRef.toJson(topic.sources),created,session.sourceRevision,actionsJson=ActionDraftCodec.encode(topic.actions))
            if (dao.insertTopic(entity)==-1L) dao.updateMappedTopic(id,entity.title,entity.summary,entity.sourcesJson,session.sourceRevision,entity.actionsJson)
            topic.actions.filter { it.sources.isNotEmpty() }.forEach { action ->
                val anchors=action.sources.joinToString("|") { "${it.chunkId}:${it.start}:${it.end}" }
                val normalized=action.text.replace(Regex("[\\s\\p{Punct}，。！？：；]"), "")
                // Keep accepted/ignored work when a model paraphrases the same source again.
                val existing=previous.any { task ->
                    task.text.replace(Regex("[\\s\\p{Punct}，。！？：；]"), "")==normalized ||
                        (task.state!=TaskState.CANDIDATE && EvidenceRef.fromJson(task.sourcesJson).any { old ->
                            action.sources.any { it.chunkId==old.chunkId && it.start==old.start && it.end==old.end }
                        })
                }
                if (!existing) {
                    val task=TaskEntity(stableId("task",session.id,anchors,normalized),
                        session.id,action.text,TaskState.CANDIDATE,EvidenceRef.toJson(action.sources),created,action.whenHint)
                    dao.insertTask(task);previous.add(task)
                }
            }
        }
        val refs=draft.topics.flatMap { it.sources }.distinct()
        dao.saveNote(DerivedNoteEntity(session.id,draft.title,draft.summary,EvidenceRef.toJson(refs),"general",
            session.sourceRevision,System.currentTimeMillis()))
        dao.markSessionReady(session.id,draft.title)
    }

    suspend fun search(term: String, start: Long = 0, end: Long = Long.MAX_VALUE, mode: String = "all"): List<TranscriptSource> {
        if(term.isBlank()) return emptyList()
        val fts="\"${term.trim().replace("\"", "\"\"")}\""
        val indexed=runCatching { dao.searchFts(fts,start,end,mode) }.getOrDefault(emptyList())
        return (indexed+dao.searchSubstring(term.trim(),start,end,mode)).distinctBy { it.chunkId }
    }

    suspend fun retry() { dao.retryJobs(); audio.recoverUploads() }
    /** v4.1 单条重试. */
    suspend fun retrySession(sessionId: String) { dao.retryJobsForSession(sessionId); audio.recoverUploads() }
    /** v4.1 数据页：本机原音占用（片段文件 + 导入原件，按实际文件大小）。 */
    fun audioUsageBytes(snapshot: MemorySnapshot): Long =
        (snapshot.chunks.map { it.filePath } + snapshot.sessions.mapNotNull { it.sourcePath }).distinct()
            .sumOf { path -> java.io.File(path).takeIf { it.isFile }?.length() ?: 0L }

    suspend fun cleanAudio(sessionId: String) {
        val session=dao.session(sessionId) ?: return
        check(session.captureState !in listOf("STARTING","RECORDING","PAUSED","STOPPING")) { "请先停止录音" }
        check(dao.activeJobs(sessionId)==0) { "这次记录正在转写，转写完成后再清理原音" }
        // v5.2: audio that has no 原话 yet is the only copy of what was said; cleaning it would lose it for good
        val untranscribed=audio.chunks(sessionId).count { it.status !in setOf("EMPTY","SOURCE","WRITING") && dao.transcript(it.id)==null }
        check(untranscribed==0) { "还有 $untranscribed 段没转写完，清理原音后就再也无法转写" }
        val paths=audio.chunks(sessionId).map { it.filePath }+listOfNotNull(session.sourcePath)
        paths.distinct().forEach { check(deleteOwnedAudio(it)) { "音频清理失败" } }
    }

    /**
     * v5.2: rows first, in one transaction, then the files (best effort). It no longer refuses while a job is running:
     * the caller cancels that job, and a late write from it finds no session and does nothing. Files left by an import
     * that was still cutting 片段 are removed by their name prefix.
     */
    suspend fun deleteSession(sessionId: String) {
        val session=dao.session(sessionId) ?: return
        check(session.captureState !in listOf("STARTING","RECORDING","PAUSED","STOPPING")) { "正在录音，先停止录音再删除" }
        val paths=audio.chunks(sessionId).map { it.filePath }+listOfNotNull(session.sourcePath)
        database.withTransaction {
            dao.detachConfirmedTasks(sessionId); dao.deleteCandidates(sessionId)
            dao.deleteJobs(sessionId); dao.deleteTopics(sessionId); dao.deleteNote(sessionId); dao.deleteMarks(sessionId)
            dao.deleteTranscripts(sessionId); audio.deleteSessionChunks(sessionId); dao.deleteSession(sessionId)
        }
        paths.distinct().forEach { deleteOwnedAudio(it) }
        File(context.filesDir,"import_chunks").listFiles { f -> f.name.startsWith("${sessionId}_") }?.forEach { it.delete() }
        File(context.cacheDir,"meeting-$sessionId.wav").delete()
    }

    /** Deletes [path] when it lies inside the app files; true when it is gone afterwards. */
    private fun deleteOwnedAudio(path: String): Boolean {
        if(path.isBlank()) return true
        val file=File(path).canonicalFile
        val root=context.filesDir.canonicalFile
        if(!file.path.startsWith(root.path+File.separator) || !file.exists()) return true
        return file.delete()
    }

    suspend fun daySources(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<SourceText> =
        dao.sourcesForDay(date.atStartOfDay(zone).toInstant().toEpochMilli(),
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()).map { it.toSource() }
}

fun TranscriptSource.toSource() = SourceText(chunkId,text,createdAt,provenance)
