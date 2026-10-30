package com.example.shuolesa.network

import android.content.Context
import androidx.work.*
import com.example.shuolesa.audio.AacWriter
import com.example.shuolesa.audio.AudioDecoder
import com.example.shuolesa.audio.WavWriter
import com.example.shuolesa.audio.AudioImporter
import com.example.shuolesa.audio.AudioRecorder
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class ProcessingCoordinator(private val context: Context,private val repository: MemoryRepository,
    private val prefs: AppPreferences,val client: AiClient,private val scope: CoroutineScope) {
    private val owner=UUID.randomUUID().toString()
    private val serial=Mutex()
    private val draining=AtomicBoolean(false)
    private val generation=AtomicLong(0)
    /** v4.1-a: job id -> earliest retry time for 429/5xx (Retry-After). In-memory; a delayed work covers restarts. */
    private val retryAt=ConcurrentHashMap<String,Long>()
    private val lastConfigWake=AtomicLong(0)
    /**
     * v4.1.1: job ids whose durable work this process has enqueued and that has not ended yet.
     * enqueueDurableJobs() runs after every finished job; without this set each run re-submitted every
     * pending job to WorkManager (O(n²) WorkManager writes for a day of five-minute chunks).
     */
    private val scheduled=ConcurrentHashMap.newKeySet<String>()
    /** Called by UploadWorker when its work ends without a retry, so a job that is pending again gets new work. */
    fun onDurableWorkFinished(jobId: String) { scheduled.remove(jobId) }
    suspend fun <T> mutateSession(block:suspend ()->T):T=serial.withLock {block()}
    /** v5.2: record id -> the job body working on it right now, so deleting the record can stop the upload. */
    private val inFlight=ConcurrentHashMap<String,Deferred<Unit>>()
    /**
     * v5.2: deletes a record without waiting for the queue. A job still running for it is cancelled; anything it would
     * still write is a no-op (each writer re-checks that the session exists, see MemoryRepository).
     */
    suspend fun deleteSession(sessionId: String) {
        inFlight[sessionId]?.cancel()
        repository.deleteSession(sessionId)
        requestProcessing()
    }
    private suspend fun sessionOf(job: ProcessingJobEntity): String? = when(job.kind) {
        "DERIVE","IMPORT" -> job.subjectId
        "ASR","MAP" -> repository.audio.getById(job.subjectId.toLongOrNull() ?: -1)?.sessionId
        else -> null
    }

    /**
     * v4.1-a P0-2: WAITING_CONFIG jobs are retried on app foreground / network change, at most once
     * every [CONFIG_WAKE_INTERVAL_MS] (unless [force], e.g. after saving settings or consenting).
     */
    suspend fun wakeConfigWaiting(force: Boolean=false) {
        val now=System.currentTimeMillis();val last=lastConfigWake.get()
        if(force) lastConfigWake.set(now)
        else if(now-last<CONFIG_WAKE_INTERVAL_MS || !lastConfigWake.compareAndSet(last,now)) return
        repository.dao.wakeWaiting(true)
        requestProcessing()
    }

    private fun networkAvailable(): Boolean = runCatching {
        val cm=context.getSystemService(ConnectivityManager::class.java)
        val caps=cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    private fun scheduleRetry(jobId: String,delayMs: Long) {
        val work=OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf("job_id" to jobId))
            .setInitialDelay(delayMs.coerceAtLeast(1_000),TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("memory-retry:$jobId",ExistingWorkPolicy.REPLACE,work)
    }

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
        val consented=prefs.isOutboundConsented()
        for(job in repository.dao.pendingJobs()) {
            if(!OutboundGate.allows(job.kind,consented)) continue // v4.1-a: nothing leaves the device before outbound consent
            if(!scheduled.add(job.id)) continue // v4.1.1: work for this job is already enqueued (KEEP would be a no-op)
            val work=OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(workDataOf("job_id" to job.id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if(job.kind=="IMPORT") NetworkType.NOT_REQUIRED else NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,java.util.concurrent.TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("memory:${job.id}",ExistingWorkPolicy.KEEP,work)
        }
    }

    private suspend fun isEligible(job: ProcessingJobEntity): Boolean {
        if(!OutboundGate.allows(job.kind,prefs.isOutboundConsented())) return false
        retryAt[job.id]?.let { if(it>System.currentTimeMillis()) return false else retryAt.remove(job.id) }
        if(job.kind!="DERIVE") return true
        val session=repository.dao.session(job.subjectId) ?: return true
        val chunks=repository.audio.chunks(session.id).filter { it.status!="EMPTY" && it.status!="WRITING" }
        val maps=repository.dao.jobs(chunks.map { "map:${it.id}:${session.sourceRevision}" })
        return maps.size==chunks.size && maps.all { it.state=="COMPLETE" }
    }

    suspend fun runOne(id: String): Boolean = serial.withLock {
        val job=repository.dao.job(id) ?: return@withLock true
        if(job.state=="COMPLETE") return@withLock true
        // v4.1-a: gate every network job on the data-outbound consent; job stays PENDING.
        if(!OutboundGate.allows(job.kind,prefs.isOutboundConsented())) return@withLock true
        retryAt[id]?.let { if(it>System.currentTimeMillis()) return@withLock true else retryAt.remove(id) }
        if(!isEligible(job)) {repository.dao.waitDependencies(id);return@withLock true}
        if(repository.dao.claimJob(id,owner,System.currentTimeMillis())!=1) return@withLock false
        try {
            val config=prefs.aiSettings()
            // v4.3: ASR jobs use the 转写 service; MAP / DERIVE / DIGEST use the 整理 one (the same unless split).
            val service=if(job.kind=="ASR") config else config.forLlm()
            if(job.kind!="IMPORT") checkCleartext(service.baseUrl) // v4.1-a.1: blocked http -> 服务地址被拦截, before KEY_MISSING
            if(job.kind!="IMPORT" && service.apiKey.isBlank() && !service.local) {
                // v4.3: with two services, say whose key is missing.
                val which=if(!config.split) "服务" else if(job.kind=="ASR") "转写服务的" else "整理服务的"
                repository.dao.finishJob(id,owner,"WAITING_CONFIG",ProcessingReason.KEY_MISSING.encode("请先填写${which}密钥"),0,System.currentTimeMillis())
                updateSessionForJob(job,"WAITING_CONFIG",ProcessingReason.KEY_MISSING.encode("还没填${which}密钥，填好后会自动继续"))
                return@withLock true
            }
            val sessionId=sessionOf(job)
            val deleted=supervisorScope {
                val body=async {
                    when(job.kind) {
                        "IMPORT" -> AudioImporter(context,repository).prepare(job.subjectId)
                        "ASR" -> transcribe(job,config)
                        "MAP" -> map(job,config)
                        "DERIVE" -> derive(job,config)
                        "DIGEST" -> digest(job,config)
                    }
                }
                sessionId?.let { inFlight[it]=body }
                try { body.await();false }
                // only the body was cancelled (its record is being deleted), not this runner
                catch(e: CancellationException) { if(body.isCancelled && currentCoroutineContext().isActive) true else throw e }
                finally { sessionId?.let { inFlight.remove(it,body) } }
            }
            if(deleted) { repository.dao.finishJob(id,owner,"PENDING",null,0,System.currentTimeMillis());return@withLock true }
            repository.dao.finishJob(id,owner,"COMPLETE",null,0,System.currentTimeMillis())
            if(job.kind=="MAP") repository.dao.wakeDependencies()
            if(job.kind=="IMPORT") repository.queueDerive(job.subjectId)
            enqueueDurableJobs()
            true
        } catch(e: CancellationException) {
            withContext(NonCancellable) { repository.dao.finishJob(id,owner,"PENDING",null,0,System.currentTimeMillis()) }
            throw e
        } catch(e: Exception) {
            val outcome=classifyFailure(e,networkAvailable())
            if(outcome.state=="PENDING") { // v4.1-a.1: consent gate hit inside AiClient; nothing was sent
                repository.dao.finishJob(id,owner,"PENDING",null,0,System.currentTimeMillis())
                return@withLock true
            }
            val message=(e.message?.take(160) ?: "处理失败，请重试")
            val error=outcome.reason.encode(if(outcome.reason==ProcessingReason.ADDRESS_INVALID && e is AiException && e.code==AiException.DNS) "找不到服务地址，请检查服务地址" else message)
            repository.dao.finishJob(id,owner,outcome.state,error,if(outcome.countsAsAttempt) 1 else 0,System.currentTimeMillis())
            updateSessionForJob(job,outcome.state,error)
            if(outcome.reason==ProcessingReason.RATE_LIMITED) {
                val delay=outcome.retryAfterMs ?: DEFAULT_RETRY_MS
                retryAt[id]=System.currentTimeMillis()+delay
                scheduleRetry(id,delay)
            }
            false
        }
    }

    private suspend fun updateSessionForJob(job: ProcessingJobEntity,state: String,error: String) {
        val session=if(job.kind in listOf("DERIVE","IMPORT")) job.subjectId else repository.audio.getById(job.subjectId.toLongOrNull() ?: -1)?.sessionId
        session?.let { repository.dao.updateProcessing(it,state,error) }
    }
    private suspend fun transcribe(job: ProcessingJobEntity,config: com.example.shuolesa.data.prefs.AiSettings) {
        val chunk=repository.audio.getById(job.subjectId.toLong()) ?: return
        if(repository.dao.transcript(chunk.id)==null && !transcribeMeeting(chunk,config)) {
            repository.audio.updateStatus(chunk.id,"UPLOADING")
            repository.dao.updateProcessing(chunk.sessionId,"TRANSCRIBING")
            val original=File(chunk.filePath)
            val temporary=if(original.extension.lowercase() !in listOf("wav","mp3","ogg","opus","pcm"))
                File(context.cacheDir,"asr-${chunk.id}.wav") else null
            try {
                val upload=temporary?.let { AudioDecoder.toWav(original,it) } ?: original
                val result=client.transcribeDetailed(config,upload)
                repository.saveTranscript(chunk,result.text,AsrSegments.toJson(result.segments,result.speakerScope))
            } finally { temporary?.delete() }
        }
        repository.queueDerive(chunk.sessionId)
    }

    /**
     * v4.2: a finished meeting is transcribed in one diarized pass, so "说话人 2" is the same person in every 片段.
     * Its 片段 are decoded into one temporary WAV, sent once, and the sentences are split back by start time.
     * Runs for the first ASR job of the meeting; the other 片段 then already have their 原话. Returns false (and
     * the 片段 is transcribed on its own) when the service can't do it, the meeting is still open, any 片段 already
     * has 原话 (never mix numbering), audio is missing, or the meeting is longer than [MEETING_DIARIZATION_MAX_MS].
     */
    private suspend fun transcribeMeeting(chunk: AudioRecordEntity,config: com.example.shuolesa.data.prefs.AiSettings): Boolean {
        if(!client.supportsSessionDiarization(config)) return false
        val session=repository.dao.session(chunk.sessionId) ?: return false
        if(session.mode!="meeting" || session.captureState !in listOf("CLOSED","INTERRUPTED")) return false
        repository.dao.job("import:${session.id}")?.let { if(it.state!="COMPLETE") return false }
        val chunks=repository.audio.chunks(session.id).filter { it.status!="EMPTY" }
        if(chunks.size<2 || chunks.any { it.status=="WRITING" || !File(it.filePath).isFile }) return false
        if(chunks.any { repository.dao.transcript(it.id)!=null }) return false
        if(chunks.sumOf { it.durationMs }>MEETING_DIARIZATION_MAX_MS) return false
        chunks.forEach { repository.audio.updateStatus(it.id,"UPLOADING") }
        repository.dao.updateProcessing(session.id,"TRANSCRIBING")
        val merged=File(context.cacheDir,"meeting-${session.id}.wav")
        try {
            val offsets=ArrayList<Pair<Long,Long>>()
            runInterruptible(Dispatchers.IO) {
                val writer=WavWriter(merged)
                var samples=0L
                try {
                    for(c in chunks) {
                        offsets+=c.id to samples*1000/AudioRecorder.SAMPLE_RATE
                        AudioDecoder.decode(File(c.filePath)) { pcm,count -> writer.write(pcm,count);samples+=count }
                    }
                } finally { writer.finish() }
            }
            val byChunk=AsrSegments.splitByChunk(client.transcribeSession(config,merged),offsets)
            repository.saveTranscripts(chunks.associate { c -> val segments=byChunk[c.id].orEmpty()
                c.id to (AsrSegments.text(segments) to AsrSegments.toJson(segments,AsrSegments.SCOPE_SESSION)) })
            return true
        } finally { merged.delete() }
    }
    private suspend fun map(job: ProcessingJobEntity,config: com.example.shuolesa.data.prefs.AiSettings) {
        val chunk=repository.audio.getById(job.subjectId.toLong()) ?: return
        val transcript=repository.dao.transcript(chunk.id) ?: error("原话尚未保存")
        val result=client.mapTopics(config,SourceText(chunk.id,transcript.text,chunk.createdAt,transcript.provenance),chunk.recordingMode ?: "lifelog")
        repository.dao.saveJobResult(job.id,owner,TopicDraftCodec.encode(result))
    }
    private suspend fun derive(job: ProcessingJobEntity,config: com.example.shuolesa.data.prefs.AiSettings) {
        val session=repository.dao.session(job.subjectId) ?: return
        val chunks=repository.audio.chunks(session.id).filter { it.status!="EMPTY" && it.status!="WRITING" }
        val maps=repository.dao.jobs(chunks.map { "map:${it.id}:${session.sourceRevision}" })
        val topics=maps.sortedBy { it.subjectId.toLong() }.flatMap { TopicDraftCodec.decode(it.resultJson ?: "[]") }
        repository.saveDerived(session,client.finalizeNotes(config,topics,session.mode))
        // v4.1-b: 整理完成通知 (only when the app is not in the foreground; badge covers the in-app case)
        runCatching { com.example.shuolesa.service.EventNotifications.organised(context,
            repository.dao.session(session.id)?.title ?: session.title,
            repository.dao.tasksForSession(session.id).count { it.state==com.example.shuolesa.data.model.TaskState.CANDIDATE }) }
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
        // v4.1.1: reuse each chunk's finished MAP result (same immutable text); only chunks without one
        // (legacy text, or not mapped yet) cost a model call. A full day used to re-map every chunk.
        val topics=sources.flatMap { source ->
            repository.dao.latestMapResult(source.chunkId.toString())
                ?.let { raw -> runCatching { TopicDraftCodec.decode(raw) }.getOrNull() }
                ?: client.mapTopics(config,source,"daily")
        }
        val refs=topics.flatMap { it.sources }.distinct()
        val body=if(topics.isEmpty()) "这一天的原话没有整理出可核对的话题。原话仍可在记忆里搜索和回听。"
            else client.digest(config,input.getString("date"),topics)
        check(sources.all {source->repository.dao.transcript(source.chunkId)?.text==source.text}) {"生成期间来源发生变化，请重新生成复盘"}
        repository.dao.saveDigest(DailyDigestEntity(input.getString("date"),input.getString("zone"),
            "${input.getString("date")} 复盘",body,EvidenceRef.toJson(refs),input.getString("fingerprint"),System.currentTimeMillis()))
    }
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstValue(): T = first()

/** v4.1-a P0-2: pure failure classification, unit tested in ProcessingClassificationTest. */
data class FailureOutcome(val state: String,val reason: ProcessingReason,val countsAsAttempt: Boolean,val retryAfterMs: Long?=null)

fun classifyFailure(e: Exception,networkUp: Boolean): FailureOutcome {
    val ai=e as? AiException ?: return FailureOutcome("FAILED",ProcessingReason.FAILED,true)
    return when(ai.reason) {
        // v4.1-a.1: consent revoked mid-run -> back to PENDING, no attempt, nothing was sent.
        ProcessingReason.AWAITING_CONSENT -> FailureOutcome("PENDING",ProcessingReason.AWAITING_CONSENT,false)
        // v4.1-a.1: blocked http address is "地址不对 / 等配置", never "等网络".
        ProcessingReason.CLEARTEXT_BLOCKED -> FailureOutcome("WAITING_CONFIG",ProcessingReason.CLEARTEXT_BLOCKED,false)
        ProcessingReason.KEY_INVALID,ProcessingReason.KEY_MISSING -> FailureOutcome("WAITING_CONFIG",ai.reason,false)
        ProcessingReason.ADDRESS_INVALID ->
            // UnknownHost while offline is a network problem, not a wrong address.
            if(ai.code==AiException.DNS && !networkUp) FailureOutcome("WAITING_NETWORK",ProcessingReason.NETWORK,false)
            else FailureOutcome("WAITING_CONFIG",ProcessingReason.ADDRESS_INVALID,false)
        ProcessingReason.NETWORK -> FailureOutcome("WAITING_NETWORK",ProcessingReason.NETWORK,false)
        ProcessingReason.RATE_LIMITED -> FailureOutcome("WAITING_NETWORK",ProcessingReason.RATE_LIMITED,false,ai.retryAfterMs)
        else -> FailureOutcome("FAILED",ProcessingReason.FAILED,true) // includes model JSON format errors
    }
}

private const val CONFIG_WAKE_INTERVAL_MS=5*60_000L
/** 百炼 recommends ≤ 2 h per file with speaker separation; longer meetings fall back to per-片段 transcription. */
private const val MEETING_DIARIZATION_MAX_MS=2*60*60_000L
private const val DEFAULT_RETRY_MS=60_000L
