package com.example.shuolesa.domain

import android.net.Uri
import android.provider.OpenableColumns
import com.example.shuolesa.AppContainer
import com.example.shuolesa.data.db.*
import com.example.shuolesa.data.model.*
import com.example.shuolesa.data.repository.toSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class RecallFilter(val start: Long=0,val end: Long=Long.MAX_VALUE,val mode: String="all")

class AskMemory(private val app: AppContainer) {
    suspend operator fun invoke(question: String,filter: RecallFilter,sessionId: String?=null): RecallAnswer {
        val config=app.preferences.aiSettings()
        require(config.apiKey.isNotBlank() || config.local) { "请先配置转写与整理服务" }
        val terms=app.ai.queryTerms(config,question)
        val candidates=if(sessionId!=null) app.repository.dao.sourcesForSession(sessionId) else
            terms.flatMap { app.repository.search(it,filter.start,filter.end,filter.mode) }.distinctBy { it.chunkId }
        val ranked=candidates.sortedByDescending { row -> terms.count { row.text.contains(it,true) } }
        val windows=ranked.take(10).map { row ->
            val at=terms.map { row.text.indexOf(it,ignoreCase=true) }.filter { it>=0 }.minOrNull() ?: 0
            val start=(at-200).coerceAtLeast(0)
            row.toSource().copy(text=row.text.substring(start,minOf(row.text.length,start+600)),startOffset=start)
        }
        val answer=app.ai.answer(config,question,windows)
        val valid=answer.sources.filter { ref -> app.repository.dao.transcript(ref.chunkId)?.text?.let(ref::isValidFor)==true }
        return if(valid.size==answer.sources.size) answer else RecallAnswer("未找到可核对的来源，请调整问题后重试。",emptyList())
    }
}

class ImportAudio(private val app: AppContainer) {
    suspend operator fun invoke(uri: Uri,mode: String): String = withContext(Dispatchers.IO) {
        require(mode in listOf("lifelog","meeting"))
        val resolver=app.context.contentResolver
        var name="导入音频"
        resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
            if(cursor.moveToFirst()) name=cursor.getString(0) ?: name
        }
        val ext=name.substringAfterLast('.',"").lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "media"
        val id=UUID.randomUUID().toString()
        val dir=File(app.context.filesDir,"imports").apply { check(mkdirs() || isDirectory) }
        val partial=File(dir,"$id.$ext.part");val original=File(dir,"$id.$ext")
        try {
            runInterruptible { resolver.openInputStream(uri)?.use { input -> partial.outputStream().use { output ->
                val buffer=ByteArray(256*1024)
                while(true) {
                    val count=input.read(buffer);if(count<0) break
                    if(app.context.filesDir.usableSpace<25L*1024*1024) error("存储空间不足，请清理后再导入")
                    output.write(buffer,0,count)
                }
            } } ?: error("无法读取选择的文件") }
            require(partial.length()>0) { "音频文件为空" }
            check(partial.renameTo(original)) { "无法保存音频原件" }
            app.repository.createSession(RecordingSessionEntity(id,mode,System.currentTimeMillis(),
                endedAt=System.currentTimeMillis(),title=name.substringBeforeLast('.').take(80),
                processingState="IMPORTING",sourcePath=original.path))
            app.repository.dao.insertJob(ProcessingJobEntity("import:$id","IMPORT",id))
            app.processing.requestProcessing()
            id
        } catch(e: Exception) { partial.delete();if(app.repository.dao.session(id)==null) original.delete();throw e }
    }
}

class GenerateDigest(private val app: AppContainer) {
    suspend operator fun invoke(date: LocalDate): String {
        val zone=ZoneId.systemDefault()
        val sources=app.repository.daySources(date,zone)
        require(sources.isNotEmpty()) { "这一天还没有已转写的原稿" }
        val fingerprint=stableId(*sources.map { "${it.chunkId}:${stableId(it.text)}" }.toTypedArray())
        val key="digest:$date:$fingerprint"
        val input=JSONObject().put("date",date.toString()).put("zone",zone.id)
            .put("fingerprint",fingerprint).put("chunks",JSONArray(sources.map { it.chunkId })).toString()
        app.repository.dao.insertJob(ProcessingJobEntity(key,"DIGEST",date.toString(),resultJson=input))
        app.repository.dao.resetDigest(key,input)
        app.processing.requestProcessing()
        return key
    }
}
