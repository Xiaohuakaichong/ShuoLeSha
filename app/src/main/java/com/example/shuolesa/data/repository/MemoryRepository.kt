package com.example.shuolesa.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.shuolesa.data.db.*
import com.example.shuolesa.data.model.*
import kotlinx.coroutines.flow.combine
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

class MemoryRepository(val database: AppDatabase, private val context: Context) {
    val dao = database.memoryDao()
    val audio = database.audioRecordDao()
    val snapshots = combine(
        combine(dao.observeSessions(), audio.observeChunkMetadata(), dao.observeTranscriptMeta(), dao.observeTopics()) { a,b,c,d ->
            MemorySnapshot(sessions=a,chunks=b,transcripts=c,topics=d)
        },
        combine(dao.observeNotes(),dao.observeTasks(),dao.observeDigests(),dao.observeJobs()) { a,b,c,d ->
            MemorySnapshot(notes=a,tasks=b,digests=c,jobs=d)
        },
    ) { a,b -> a.copy(notes=b.notes,tasks=b.tasks,digests=b.digests,jobs=b.jobs) }

    suspend fun createSession(session: RecordingSessionEntity) = dao.insertSession(session)
    suspend fun registerChunk(chunk: AudioRecordEntity): Long = audio.insert(chunk)
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
    suspend fun saveTranscript(chunk: AudioRecordEntity, text: String) = database.withTransaction {
        if(dao.session(chunk.sessionId)==null || audio.getById(chunk.id)==null) return@withTransaction
        if (dao.insertTranscript(TranscriptEntity(chunk.id,text,"ASR",System.currentTimeMillis())) != -1L) {
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

    suspend fun cleanAudio(sessionId: String) {
        val session=dao.session(sessionId) ?: return
        check(session.captureState !in listOf("STARTING","RECORDING","PAUSED","STOPPING")) { "请先停止录音" }
        check(dao.activeJobs(sessionId)==0) { "这次记录正在处理，请处理结束后再清理或删除" }
        val paths=audio.chunks(sessionId).map { it.filePath }+listOfNotNull(session.sourcePath)
        paths.distinct().forEach(::deleteOwnedAudio)
    }

    suspend fun deleteSession(sessionId: String) {
        cleanAudio(sessionId)
        database.withTransaction {
            dao.detachConfirmedTasks(sessionId); dao.deleteCandidates(sessionId)
            dao.deleteJobs(sessionId); dao.deleteTopics(sessionId); dao.deleteNote(sessionId)
            dao.deleteTranscripts(sessionId); audio.deleteSessionChunks(sessionId); dao.deleteSession(sessionId)
        }
    }

    private fun deleteOwnedAudio(path: String) {
        if(path.isBlank()) return
        val file=File(path).canonicalFile
        val root=context.filesDir.canonicalFile
        if(file.path.startsWith(root.path+File.separator) && file.exists()) check(file.delete()) { "音频清理失败" }
    }

    suspend fun daySources(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<SourceText> =
        dao.sourcesForDay(date.atStartOfDay(zone).toInstant().toEpochMilli(),
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()).map { it.toSource() }
}

fun TranscriptSource.toSource() = SourceText(chunkId,text,createdAt,provenance)
