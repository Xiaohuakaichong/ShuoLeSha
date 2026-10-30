package com.example.shuolesa.ui

import com.example.shuolesa.data.db.*
import com.example.shuolesa.data.model.*
import com.example.shuolesa.data.repository.MemorySnapshot
import java.time.*
import java.time.format.DateTimeFormatter

/** v4.2: [transcribed]/[total] = 片段 with 原话 / 片段 with audio, for the progress line on cards still being processed. */
data class MemoryCard(val key:String,val sessionId:String,val topicId:String?,val title:String,val summary:String,
    val time:Long,val mode:String,val duration:Long,val status:String,val candidates:Int,val legacy:Boolean,
    val transcribed:Int=0,val total:Int=0,
    /** v5.2: overall progress 0..1 and its one-line description while the record is still being processed. */
    val progress:Float?=null,val progressLabel:String?=null)

/**
 * Builds the memory list. Runs in composition (remember) on every snapshot change, so every per-session
 * lookup goes through maps built once here (v4.1.1). The former nested scans were O(sessions × legacy
 * transcripts × chunks) and re-parsed every candidate's sources for each topic card.
 */
fun MemorySnapshot.cards(consented:Boolean=true):List<MemoryCard> {
    val notesBySession=notes.associateBy { it.sessionId }
    val topicsBySession=topics.groupBy { it.sessionId }
    val chunkSession=chunks.associate { it.id to it.sessionId }
    val legacySessions=transcripts.asSequence().filter { it.provenance=="LEGACY" }.mapNotNull { chunkSession[it.chunkId] }.toHashSet()
    val candidatesBySession=tasks.asSequence().filter { it.state==TaskState.CANDIDATE }
        .mapNotNull { task -> task.sessionId?.let { it to task } }.groupBy({ it.first },{ it.second })
    val transcribedIds=transcripts.mapTo(HashSet()) { it.chunkId }
    val audioChunks=chunks.filter { it.status !in setOf("EMPTY","SOURCE","WRITING") }.groupBy { it.sessionId }
    val chunksBySession=chunks.groupBy { it.sessionId }
    val jobsById=jobs.associateBy { it.id }
    return sessions.flatMap { session ->
        val note=notesBySession[session.id]
        val own=topicsBySession[session.id].orEmpty()
        val revision=note?.sourceRevision ?: own.maxOfOrNull { it.revision }
        val visible=own.filter { revision==null || it.revision==revision }
        val legacy=session.id in legacySessions
        val sessionCandidates=candidatesBySession[session.id].orEmpty()
        val status=if(session.captureState in listOf("STARTING","RECORDING","PAUSED","STOPPING")) session.captureState else session.processingState
        val sessionAudio=audioChunks[session.id].orEmpty()
        val total=sessionAudio.size
        val transcribed=sessionAudio.count { it.id in transcribedIds }
        val progress=if(session.processingState=="READY") null else com.example.shuolesa.ui.logic.ProgressLogic.of(session,
            chunksBySession[session.id].orEmpty(),transcribedIds,jobsById::get,consented)?.takeIf { !it.finished }
        if(session.mode=="lifelog" && visible.isNotEmpty()) {
            val candidateRefs=sessionCandidates.map { EvidenceRef.fromJson(it.sourcesJson) }
            visible.map { topic ->
                val refs=EvidenceRef.fromJson(topic.sourcesJson).toHashSet()
                val count=candidateRefs.count { taskRefs -> taskRefs.any { it in refs } }
                MemoryCard(topic.id,session.id,topic.id,topic.title,topic.summary,topic.createdAt,session.mode,
                    session.durationMs,status,count,legacy,transcribed,total,progress?.fraction,progress?.headline)
            }
        } else if(session.mode=="lifelog" && session.processingState=="READY" && revision!=null && EvidenceRef.fromJson(note?.sourcesJson).isNotEmpty()) emptyList()
        else listOf(MemoryCard(session.id,session.id,null,note?.title ?: session.title,note?.summary ?: "",
            session.startedAt,session.mode,session.durationMs,status,sessionCandidates.size,legacy,transcribed,total,progress?.fraction,progress?.headline))
    }.sortedByDescending { it.time }
}

fun clock(ms:Long)=DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
fun dateLabel(ms:Long)=DateTimeFormatter.ofPattern("M月d日").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
fun duration(ms:Long):String { val total=ms/1000;return if(total>=3600) "%d:%02d:%02d".format(total/3600,total/60%60,total%60) else "%02d:%02d".format(total/60,total%60) }
/** v4.1 ⑧: one vocabulary everywhere (see ui/logic/UiStatus). */
fun statusLabel(state:String)=when(state) {
    "EMPTY"->"未录到音频";"RECORDING"->"录音中";"PAUSED"->"已暂停";"STARTING"->"准备录音";"STOPPING"->"正在保存"
    else->com.example.shuolesa.ui.logic.UiStatus.of(null,state).label
}
