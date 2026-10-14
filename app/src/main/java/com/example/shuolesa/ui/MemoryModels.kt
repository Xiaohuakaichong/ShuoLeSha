package com.example.shuolesa.ui

import com.example.shuolesa.data.db.*
import com.example.shuolesa.data.model.*
import com.example.shuolesa.data.repository.MemorySnapshot
import java.time.*
import java.time.format.DateTimeFormatter

data class MemoryCard(val key:String,val sessionId:String,val topicId:String?,val title:String,val summary:String,
    val time:Long,val mode:String,val duration:Long,val status:String,val candidates:Int,val legacy:Boolean)

fun MemorySnapshot.cards():List<MemoryCard> = sessions.flatMap { session ->
    val note=notes.find { it.sessionId==session.id }
    val revision=note?.sourceRevision ?: topics.filter { it.sessionId==session.id }.maxOfOrNull { it.revision }
    val visible=topics.filter { it.sessionId==session.id && (revision==null || it.revision==revision) }
    val legacy=transcripts.any { t -> t.provenance=="LEGACY" && chunks.any { it.id==t.chunkId && it.sessionId==session.id } }
    val candidates=tasks.count { it.sessionId==session.id && it.state==TaskState.CANDIDATE }
    val status=if(session.captureState in listOf("STARTING","RECORDING","PAUSED","STOPPING")) session.captureState else session.processingState
    if(session.mode=="lifelog" && visible.isNotEmpty()) visible.map { topic ->
        val refs=EvidenceRef.fromJson(topic.sourcesJson)
        val count=tasks.count { task -> task.sessionId==session.id && task.state==TaskState.CANDIDATE &&
            EvidenceRef.fromJson(task.sourcesJson).any { it in refs } }
        MemoryCard(topic.id,session.id,topic.id,topic.title,topic.summary,topic.createdAt,session.mode,
            session.durationMs,status,count,legacy)
    } else if(session.mode=="lifelog" && session.processingState=="READY" && revision!=null && EvidenceRef.fromJson(note?.sourcesJson).isNotEmpty()) emptyList()
    else listOf(MemoryCard(session.id,session.id,null,note?.title ?: session.title,note?.summary ?: "",
        session.startedAt,session.mode,session.durationMs,status,candidates,legacy))
}.sortedByDescending { it.time }

fun clock(ms:Long)=DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
fun dateLabel(ms:Long)=DateTimeFormatter.ofPattern("M月d日").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
fun duration(ms:Long):String { val total=ms/1000;return if(total>=3600) "%d:%02d:%02d".format(total/3600,total/60%60,total%60) else "%02d:%02d".format(total/60,total%60) }
fun statusLabel(state:String)=when(state) {
    "READY"->"已整理";"TRANSCRIBING"->"转写中";"DERIVING"->"整理中";"IMPORTING"->"导入中"
    "WAITING_CONFIG"->"等待配置";"WAITING_NETWORK"->"等待网络";"FAILED"->"处理失败"
    "EMPTY"->"未录到音频";"RECORDING"->"录音中";"PAUSED"->"已暂停";"STARTING"->"准备录音";"STOPPING"->"正在保存"
    else->"等待处理"
}
