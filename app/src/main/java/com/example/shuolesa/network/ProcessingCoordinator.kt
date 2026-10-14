package com.example.shuolesa.network

import android.content.Context
import androidx.work.*
import com.example.shuolesa.audio.AacWriter
import com.example.shuolesa.audio.AudioDecoder
import com.example.shuolesa.audio.WavWriter
import com.example.shuolesa.audio.AudioImporter
import com.example.shuolesa.data.db.DailyDigestEntity
import org.json.JSONObject
import com.example.shuolesa.data.db.ProcessingJobEntity
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.model.*
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.repository.MemoryRepository
import com.example.shuolesa.data.repository.toSource
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class ProcessingCoordinator(private val context: Context,private val repository: MemoryRepository,
    private val prefs: AppPreferences,val client: AiClient,private val scope: CoroutineScope) {
    private val owner=UUID.randomUUID().toString()
    private val serial=Mutex()
    private val draining=AtomicBoolean(false)
    private val generation=AtomicLong(0)
    suspend fun <T> mutateSession(block:suspend ()->T):T=serial.withLock {block()}

    suspend fun recover() {
        repository.dao.recoverJobs(owner); repository.audio.recoverUploads()
        repository.dao.recoverCapture(System.currentTimeMillis())
        for(record in repository.audio.unsealedChunks()) {
            var file=File(record.filePath)
            if(!file.exists() && file.path.endsWith(".part")) file=File(file.path.removeSuffix(".part"))
            if(!file.exists()) { repository.audio.updateStatus(record.id,"FAILED"); continue }
            val duration=runCatching {
                if(file.name.contains(".wav")) WavWriter.repair(file) else AacWriter.recover(file)
            }.getOrDefault(0)
            if(file.path.endsWith(".part")) {
                val sealed=File(file.path.removeSuffix(".part"))
                if(file.renameTo(sealed)) file=sealed
            }
            repository.audio.updateFilePath(record.id,file.path)
            if(record.sourceStartMs!=null) repository.audio.setSourceEnd(record.id,record.sourceStartMs+duration)
            repository.sealChunk(record.id,duration,file.length(),true)
        }
        for(session in repository.dao.observeSessions().firstValue()) {
            val chunks=repository.audio.chunks(session.id)
            if(session.captureState=="INTERRUPTED") repository.dao.updateCapture(session.id,"INTERRUPTED",session.endedAt,chunks.sumOf { it.durationMs })
            chunks.filter { it.status=="PENDING" && repository.dao.transcript(it.id)==null }.forEach { repository.queueAsr(it.id) }
            if(session.processingState!="READY") repository.queueDerive(session.id)
        }
    }

    fun requestProcessing() {
        generation.incrementAndGet()
        if(draining.compareAndSet(false,true)) scope.launch {
            var observed=-1L
            try {
                    val attempted=mutableSetOf<String>()
                    do {
                        observed=generation.get()
                        enqueueDurableJobs()
                        val jobs=repository.dao.pendingJobs()
                        var progressed=false
                        for(job in jobs) if(job.id !in attempted && isEligible(job)) {
                            attempted.add(job.id)
                            runOne(job.id); progressed=true
                        }
                        enqueueDurableJobs()
                    } while(generation.get()!=observed || repository.dao.pendingJobs().any { it.id !in attempted && isEligible(it) })
            } finally {
                draining.set(false)
                if(generation.get()!=observed) requestProcessing()
            }
        }
    }

    private suspend fun enqueueDurableJobs() {
        for(job in repository.dao.pendingJobs()) {
            val work=OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(workDataOf("job_id" to job.id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if(job.kind=="IMPORT") NetworkType.NOT_REQUIRED else NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,java.util.concurrent.TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("memory:${job.id}",ExistingWorkPolicy.KEEP,work)
        }
    }

    private suspend fun isEligible(job: ProcessingJobEntity): Boolean {
        if(job.kind!="DERIVE") return true
        val session=repository.dao.session(job.subjectId) ?: return true
        val chunks=repository.audio.chunks(session.id).filter { it.status!="EMPTY" && it.status!="WRITING" }
        val maps=repository.dao.jobs(chunks.map { "map:${it.id}:${session.sourceRevision}" })
        return maps.size==chunks.size && maps.all { it.state=="COMPLETE" }
    }

    suspend fun runOne(id: String): Boolean = serial.withLock {
        val job=repository.dao.job(id) ?: return@withLock true
        if(job.state=="COMPLETE") return@withLock true
        if(!isEligible(job)) {repository.dao.waitDependencies(id);return@withLock true}
        if(repository.dao.claimJob(id,owner,System.currentTimeMillis())!=1) return@withLock false
        try {
            val config=prefs.aiSettings()
            if(job.kind!="IMPORT" && config.apiKey.isBlank() && !config.local) {
                repository.dao.finishJob(id,owner,"WAITING_CONFIG","请先配置转写与整理服务",0,System.currentTimeMillis())
                updateSessionForJob(job,"WAITING_CONFIG","等待配置转写与整理服务")
                return@withLock true
            }
            when(job.kind) {
                "IMPORT" -> AudioImporter(context,repository).prepare(job.subjectId)
                "ASR" -> transcribe(job,config)
                "MAP" -> map(job,config)
                "DERIVE" -> derive(job,config)
                "DIGEST" -> digest(job,config)
            }
            repository.dao.finishJob(id,owner,"COMPLETE",null,0,System.currentTimeMillis())
            if(job.kind=="MAP") repository.dao.wakeDependencies()
            if(job.kind=="IMPORT") repository.queueDerive(job.subjectId)
            enqueueDurableJobs()
            true
        } catch(e: CancellationException) {
            withContext(NonCancellable) { repository.dao.finishJob(id,owner,"PENDING",null,0,System.currentTimeMillis()) }
            throw e
        } catch(e: Exception) {
            val waiting=e is AiException && e.code==0
            val configuration=e is AiException && e.code in listOf(401,403,404)
            val state=if(waiting) "WAITING_NETWORK" else if(configuration) "WAITING_CONFIG" else "FAILED"
            val reason=e.message?.take(180) ?: "处理失败，请重试"
            repository.dao.finishJob(id,owner,state,reason,if(waiting || configuration) 0 else 1,System.currentTimeMillis())
            updateSessionForJob(job,state,reason)
            false
        }
    }

    private suspend fun updateSessionForJob(job: ProcessingJobEntity,state: String,error: String) {
        val session=if(job.kind in listOf("DERIVE","IMPORT")) job.subjectId else repository.audio.getById(job.subjectId.toLongOrNull() ?: -1)?.sessionId
        session?.let { repository.dao.updateProcessing(it,state,error) }
    }
    private suspend fun transcribe(job: ProcessingJobEntity,config: com.example.shuolesa.data.prefs.AiSettings) {
        val chunk=repository.audio.getById(job.subjectId.toLong()) ?: return
        if(repository.dao.transcript(chunk.id)==null) {
            repository.audio.updateStatus(chunk.id,"UPLOADING")
            repository.dao.updateProcessing(chunk.sessionId,"TRANSCRIBING")
            val original=File(chunk.filePath)
            val temporary=if(original.extension.lowercase() !in listOf("wav","mp3","ogg","opus","pcm"))
                File(context.cacheDir,"asr-${chunk.id}.wav") else null
            try {
                val upload=temporary?.let { AudioDecoder.toWav(original,it) } ?: original
                repository.saveTranscript(chunk,client.transcribe(config,upload))
            } finally { temporary?.delete() }
        }
        repository.queueDerive(chunk.sessionId)
    }
    private suspend fun map(job: ProcessingJobEntity,config: com.example.shuolesa.data.prefs.AiSettings) {
        val chunk=repository.audio.getById(job.subjectId.toLong()) ?: return
        val transcript=repository.dao.transcript(chunk.id) ?: error("原稿尚未保存")
        val result=client.mapTopics(config,SourceText(chunk.id,transcript.text,chunk.createdAt,transcript.provenance),chunk.recordingMode ?: "lifelog")
        repository.dao.saveJobResult(job.id,owner,TopicDraftCodec.encode(result))
    }
    private suspend fun derive(job: ProcessingJobEntity,config: com.example.shuolesa.data.prefs.AiSettings) {
        val session=repository.dao.session(job.subjectId) ?: return
        val chunks=repository.audio.chunks(session.id).filter { it.status!="EMPTY" && it.status!="WRITING" }
        val maps=repository.dao.jobs(chunks.map { "map:${it.id}:${session.sourceRevision}" })
        val topics=maps.sortedBy { it.subjectId.toLong() }.flatMap { TopicDraftCodec.decode(it.resultJson ?: "[]") }
        repository.saveDerived(session,client.finalizeNotes(config,topics,session.mode))
        if(session.sourcePath!=null) chunks.filter { it.sourceStartMs!=null }.forEach {
            File(it.filePath).takeIf { file -> file.canonicalPath.startsWith(File(context.filesDir,"import_chunks").canonicalPath+File.separator) }?.delete()
        }
    }

    private suspend fun digest(job: ProcessingJobEntity,config: com.example.shuolesa.data.prefs.AiSettings) {
        val input=JSONObject(job.resultJson ?: error("复盘来源快照不可用"))
        val ids=input.getJSONArray("chunks")
        val sources=(0 until ids.length()).mapNotNull { i ->
            val id=ids.getLong(i);val transcript=repository.dao.transcript(id) ?: return@mapNotNull null
            val chunk=repository.audio.getById(id) ?: return@mapNotNull null
            SourceText(id,transcript.text,chunk.createdAt,transcript.provenance)
        }
        check(sources.isNotEmpty()) { "来源已删除，复盘未生成" }
        val topics=sources.flatMap { source -> client.mapTopics(config,source,"daily") }
        val refs=topics.flatMap { it.sources }.distinct()
        val body=client.digest(config,input.getString("date"),topics)
        check(sources.all {source->repository.dao.transcript(source.chunkId)?.text==source.text}) {"生成期间来源发生变化，请重新生成复盘"}
        repository.dao.saveDigest(DailyDigestEntity(input.getString("date"),input.getString("zone"),
            "${input.getString("date")} 复盘",body,EvidenceRef.toJson(refs),input.getString("fingerprint"),System.currentTimeMillis()))
    }
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstValue(): T = first()
