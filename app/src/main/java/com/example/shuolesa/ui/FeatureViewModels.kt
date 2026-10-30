package com.example.shuolesa.ui

import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.shuolesa.AppContainer
import com.example.shuolesa.data.db.*
import com.example.shuolesa.data.model.*
import com.example.shuolesa.data.prefs.AiSettings
import com.example.shuolesa.data.prefs.KeyNotice
import com.example.shuolesa.data.prefs.ProviderPreset
import com.example.shuolesa.data.repository.MemorySnapshot
import com.example.shuolesa.domain.*
import com.example.shuolesa.service.AudioCaptureService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.time.LocalDate

open class FeatureViewModel(protected val app:AppContainer):ViewModel() {
    val snapshot=app.repository.snapshots.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),MemorySnapshot())
    val message=MutableStateFlow<String?>(null)
    val busy=MutableStateFlow(false)
    protected fun work(block:suspend ()->Unit) { viewModelScope.launch {
        busy.value=true
        try { withContext(Dispatchers.IO) { block() } }
        catch(e:CancellationException) { throw e }
        catch(e:Exception) { message.value=e.message?.take(180) ?: "操作失败，请重试" }
        finally { busy.value=false }
    } }
    fun retry()=work { app.repository.retry();app.processing.requestProcessing();message.value="已重新加入处理队列" }
}
class MemoryViewModel(app:AppContainer):FeatureViewModel(app) {
    fun import(uri:Uri,mode:String)=work { ImportAudio(app)(uri,mode);message.value="原件已保存，正在准备转写" }
    fun command(action:String,mode:String?=null) {
        try { val intent=Intent(app.context,AudioCaptureService::class.java).setAction(action)
            mode?.let { intent.putExtra(AudioCaptureService.EXTRA_RECORDING_MODE,it) }
            if(action==AudioCaptureService.ACTION_START) ContextCompat.startForegroundService(app.context,intent)
            else app.context.startService(intent)
        } catch(e:Exception) { message.value="录音服务无法启动，请重试" }
    }
    /** v4.1-b ⑦: notifications were off when the blind trigger failed -> home shows a fallback card. */
    val blindFailedPending=app.preferences.blindFailedPending
    fun clearBlindFailed()=work { app.preferences.setBlindFailedPending(false) }
    /** 「以后不提示」 on the home fallback card: persisted; only informational cards allow this. */
    val homeBlindCardOff=app.preferences.homeBlindCardOff
    fun dismissBlindCardForever()=work { app.preferences.setHomeBlindCardOff(true);app.preferences.setBlindFailedPending(false) }
    /** v4.1-b ⑧: retry one record from its card. */
    fun retrySession(sessionId:String)=work { app.repository.retrySession(sessionId);app.processing.requestProcessing();message.value="已重新加入处理队列" }
}
data class RecallState(val query:String="",val results:List<TranscriptSource> = emptyList(),val answer:RecallAnswer?=null,
    val loading:Boolean=false,val searched:Boolean=false,val error:String?=null)
class RecallViewModel(app:AppContainer):FeatureViewModel(app) {
    val state=MutableStateFlow(RecallState())
    private var request:Job?=null
    fun clear() {request?.cancel();state.value=RecallState()}
    fun search(query:String,filter:RecallFilter,ask:Boolean=false,sessionId:String?=null) {
        request?.cancel()
        request=viewModelScope.launch {
            state.value=RecallState(query,loading=true,searched=true)
            try { withContext(Dispatchers.IO) {
                val results=if(sessionId==null) app.repository.search(query,filter.start,filter.end,filter.mode)
                    else app.repository.dao.sourcesForSession(sessionId).filter { it.text.contains(query,true) }
                val answer=if(ask) AskMemory(app)(query,filter,sessionId) else null
                state.value=RecallState(query,results,answer,searched=true)
            } } catch(e:CancellationException) { throw e }
            catch(e:Exception) { state.value=RecallState(query,searched=true,error=e.message?.take(180)) }
        }
    }
}
data class DetailState(val session:RecordingSessionEntity?=null,val topic:MemoryTopicEntity?=null,
    val note:DerivedNoteEntity?=null,val chunks:List<AudioRecordEntity> = emptyList(),
    val sources:List<TranscriptSource> = emptyList(),val tasks:List<TaskEntity> = emptyList(),val loading:Boolean=true,
    /** v4.1-b: chunkId -> transcripts.segmentsJson (null => ⑥-B). */ val segments:Map<Long,String?> = emptyMap(),
    /** v4.2: capture marks, offsets from the start of the recording. */ val marks:List<Long> = emptyList())
/** The part of the global snapshot one detail page depends on. */
private data class DetailKey(val session:RecordingSessionEntity?,val note:DerivedNoteEntity?,val chunks:List<AudioRecordEntity>,
    val tasks:List<TaskEntity>,val topic:MemoryTopicEntity?)
class DetailViewModel(app:AppContainer,private val sessionId:String,private val topicId:String?):FeatureViewModel(app) {
    val detail=MutableStateFlow(DetailState())
    // v4.1.1: reload only when this session's own rows change. Every job-state change or recording checkpoint
    // elsewhere used to re-read all of this session's transcripts plus one full transcript per chunk.
    init { viewModelScope.launch { app.repository.snapshots.map { snap -> DetailKey(snap.sessions.find { it.id==sessionId },
            snap.notes.find { it.sessionId==sessionId },snap.chunks.filter { it.sessionId==sessionId },
            snap.tasks.filter { it.sessionId==sessionId },topicId?.let { id -> snap.topics.find { it.id==id } })
        }.distinctUntilChanged().collect { key -> withContext(Dispatchers.IO) {
            val segments=app.repository.dao.segmentsForSession(sessionId).associate { it.chunkId to it.segmentsJson }
            detail.value=DetailState(key.session,topicId?.let { app.repository.dao.topic(it) },key.note,key.chunks,
                app.repository.dao.sourcesForSession(sessionId),key.tasks,false,
                key.chunks.associate { it.id to segments[it.id] },app.repository.dao.marksForSession(sessionId))
        } } } }
    /** v5.2: step-by-step processing progress of this record (null when no audio was recorded). */
    val progress=combine(app.repository.snapshots,app.preferences.outboundConsented) { snap,consented ->
        snap.sessions.find { it.id==sessionId }?.let { session ->
            val chunks=snap.chunks.filter { it.sessionId==sessionId }
            val ids=chunks.mapTo(HashSet()) { it.id }
            val transcribed=snap.transcripts.asSequence().map { it.chunkId }.filter { it in ids }.toHashSet()
            val jobs=snap.jobs.associateBy { it.id }
            com.example.shuolesa.ui.logic.ProgressLogic.of(session,chunks,transcribed,jobs::get,consented)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.Default).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    fun retrySession()=work { app.repository.retrySession(sessionId);app.processing.requestProcessing();message.value="已重新加入处理队列" }
    fun regenerate()=work { app.repository.queueDerive(sessionId,true);app.processing.requestProcessing();message.value="已开始重新整理，原话和已确认待办会保留" }
    fun hideTopic()=work { topicId?.let { app.repository.dao.setTopicHidden(it,true) };message.value="已从记忆列表移除，原话和原音仍保留" }
    /**
     * v5.2: 删除整条记录. It used to wait for the processing queue lock, which a long upload holds for minutes, and it ran
     * on this page scope, so leaving the page cancelled it and the record stayed. Now the running job for this record is
     * cancelled, the rows go at once, and the work runs on the app scope; the result is reported on the home screen.
     * Returns false (and says why) when it cannot start.
     */
    fun delete():Boolean {
        val s=detail.value.session
        if(s!=null && s.captureState in listOf("STARTING","RECORDING","PAUSED","STOPPING")) { message.value="正在录音，先停止录音再删除";return false }
        app.scope.launch {
            try { app.processing.deleteSession(sessionId);app.homeMessage.value="已删除这条记录" }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) { app.homeMessage.value="删除没有完成：${e.message?.take(80) ?: "请重试"}" }
        }
        return true
    }
    fun cleanAudio()=work { app.processing.mutateSession {app.repository.cleanAudio(sessionId)};message.value="原音已清理，文字和待办仍保留" }
    /** v4.2 要点页: confirm / ignore / complete a 待办 right where it was found (same rules as the 待办 page). */
    fun transition(task:TaskEntity,next:String)=work {
        if(app.repository.dao.transitionTask(task.id,task.state,next)==1)
            message.value=when(next) { TaskState.ACTIVE->"已加入待办";TaskState.IGNORED->"已忽略，可在待办页恢复";TaskState.DONE->"已完成";else->"已恢复为候选" }
    }
    /** v4.2: rename a speaker of this recording; a blank name restores 说话人 N. Display only, stays on the device. */
    fun renameSpeaker(key:String,name:String)=work {
        val session=app.repository.dao.session(sessionId) ?: return@work
        app.repository.dao.setSpeakerNames(sessionId,com.example.shuolesa.ui.logic.SpeakerNames.set(session.speakerNamesJson,key,name))
    }
    suspend fun chunk(id:Long)=app.repository.audio.getById(id)
}
data class SourceState(val text:String="",val provenance:String="ASR",val chunk:AudioRecordEntity?=null,
    val session:RecordingSessionEntity?=null,val loading:Boolean=true)
class SourceViewModel(app:AppContainer,id:Long):FeatureViewModel(app) {
    val source=MutableStateFlow(SourceState())
    init { viewModelScope.launch(Dispatchers.IO) {
        val chunk=app.repository.audio.getById(id)
        val text=app.repository.dao.transcript(id)
        source.value=SourceState(text?.text ?: "",text?.provenance ?: "ASR",chunk,
            chunk?.let { app.repository.dao.session(it.sessionId) },false)
    } }
}
class TodayViewModel(app:AppContainer):FeatureViewModel(app) {
    val selectedDate=MutableStateFlow(LocalDate.now())
    fun change(days:Long) { selectedDate.value=selectedDate.value.plusDays(days) }
    fun today() { selectedDate.value=LocalDate.now() }
    fun generate()=work { GenerateDigest(app)(selectedDate.value);message.value="复盘已加入队列，生成期间可以继续录音" }
}
class TasksViewModel(app:AppContainer):FeatureViewModel(app) {
    fun transition(task:TaskEntity,next:String)=work {
        if(app.repository.dao.transitionTask(task.id,task.state,next)==1)
            message.value=when(next) { TaskState.ACTIVE->"已加入待办";TaskState.IGNORED->"已忽略，可在忽略列表恢复";TaskState.DONE->"已完成";else->"已恢复为候选" }
    }
}
class SettingsViewModel(app:AppContainer):FeatureViewModel(app) {
    val config=MutableStateFlow<AiSettings?>(null)
    val preferences=app.preferences
    /** v4.1-b 数据页：本机原音占用（字节），IO 线程计算。 */
    val audioUsage=MutableStateFlow<Long?>(null)
    fun refreshAudioUsage(snapshot:com.example.shuolesa.data.repository.MemorySnapshot) { viewModelScope.launch(Dispatchers.IO) { audioUsage.value=app.repository.audioUsageBytes(snapshot) } }
    init { work { config.value=app.preferences.aiSettings() } }
    /** v4.1-a: a save waiting for the base-URL re-confirmation sheet. */
    data class PendingSave(val config:AiSettings,val clearKey:Boolean,val keyNotice:KeyNotice=KeyNotice.REUSE_SAVED,
        /** v4.3: key notice for a separate 整理 service; null when 整理 uses the 转写 service. */
        val llmKeyNotice:KeyNotice?=null)
    val pendingSave=MutableStateFlow<PendingSave?>(null)
    val outboundConsented=app.preferences.outboundConsented
    val outboundBase=app.preferences.outboundConsentBase
    val privacyConsented=app.preferences.privacyConsented
    /** 转写 service preset. v4.3: a separate 整理 service (and its model) is kept as it is. */
    fun preset(preset:ProviderPreset)=work { val c=config.value
        val keep=c?.split==true
        config.value=AiSettings(preset.id,preset.defaultBaseUrl,
            app.preferences.credentialFor(preset.id),preset.defaultAsrModel,if(keep) c.llmModel else preset.defaultLlmModel,
            c?.prompt ?: app.preferences.systemPrompt.first(),
            c?.hotwords.orEmpty(),c?.diarize ?: true,
            if(keep) c.llmProvider else "",if(keep) c.llmBaseUrl else "",if(keep) c.llmApiKey else "")}
    /**
     * v4.3: 整理与提问 service. null = same as 转写 (its preset's 整理 model); otherwise that preset's address, saved key
     * and default model. Like [preset], this resets unsaved edits in the form.
     */
    fun llmPreset(preset:ProviderPreset?)=work { val c=config.value ?: app.preferences.aiSettings()
        config.value=if(preset==null) c.copy(llmProvider="",llmBaseUrl="",llmApiKey="",
            llmModel=ProviderPreset.entries.find { it.id==c.provider }?.defaultLlmModel?.takeIf { c.split } ?: c.llmModel)
        else c.copy(llmProvider=preset.id,llmBaseUrl=preset.defaultBaseUrl,llmApiKey=app.preferences.credentialFor(preset.id),
            llmModel=preset.defaultLlmModel) }
    /** v4.3: 「清除整理服务密钥」. */
    fun clearLlmKey()=work { val c=config.value ?: return@work
        if(!c.split) return@work
        app.preferences.clearApiKey(c.llmProvider);config.value=c.copy(llmApiKey="");message.value="已清除整理服务的密钥" }
    /** Saves, or first asks to re-confirm data outbound when the base URL is not the confirmed one. */
    private val flow=ServiceConfigFlow(app.preferences) { c -> testNow(c) }
    private val pendingFlow=MutableStateFlow<ServiceConfigFlow.Pending?>(null)
    private suspend fun requestSave(config:AiSettings,clearKey:Boolean,andConfirm:Boolean) {
        val p=flow.requestSave(config,clearKey,andConfirm)
        if(p==null) { afterSave();return }
        // v4.1-a.1 ⓪-3: same provider -> reuse its saved key; provider changed -> 新服务需要填写密钥
        val saved=app.preferences.aiSettings()
        val notice=KeyNotice.of(saved.provider,config.provider,if(clearKey) "" else config.apiKey,
            app.preferences.credentialFor(config.provider),config.local)
        // v4.3: the separate 整理 service needs its own key too (unless it is a local address).
        val llmNotice=if(!config.split) null else KeyNotice.of(saved.forLlm().provider,config.llmProvider,config.llmApiKey,
            app.preferences.credentialFor(config.llmProvider),config.llmLocal)
        pendingFlow.value=p;pendingSave.value=PendingSave(config,clearKey,notice,llmNotice)
    }
    fun save(config:AiSettings,clearKey:Boolean=false)=work { requestSave(config,clearKey,false) }
    /** 地址已修改 banner「保存并确认」: save draft -> re-confirm dialog -> (confirm) auto 测试连接 once. */
    fun saveAndConfirm(config:AiSettings)=work { testDraftChanged.value=false;requestSave(config,false,true) }
    fun confirmPendingSave()=work { val p=pendingFlow.value ?: return@work;pendingFlow.value=null;pendingSave.value=null
        flow.confirm(p);config.value=app.preferences.aiSettings()
        app.processing.wakeConfigWaiting(true);if(!p.autoTest) message.value="配置已保存，之后的记录会发送到新地址" }
    fun cancelPendingSave() { val p=pendingFlow.value;pendingFlow.value=null;pendingSave.value=null
        if(p?.saveOnCancel==true) work { flow.cancel(p);config.value=app.preferences.aiSettings();message.value="地址已保存，未确认数据外发，暂不能测试连接" } }
    private suspend fun afterSave() { config.value=app.preferences.aiSettings();app.processing.wakeConfigWaiting(true);message.value="配置已保存" }
    fun revokePrivacy()=work {
        if(AudioCaptureService.isRunning) error("请先停止录音，再撤回同意")
        app.preferences.revokeOutboundConsent();app.preferences.setPrivacyConsent(false)
        com.example.shuolesa.service.TileConsentCache.clear() // v4.1-a.3
    }
    fun revokeOutbound()=work { app.preferences.revokeOutboundConsent();message.value="已关闭数据外发，新录音只保存在本机" }
    /** qa v4.1-final P1: draft address ≠ consented address -> friendly card with「去保存」, not a raw error. */
    val testDraftChanged=MutableStateFlow(false)
    fun clearDraftChanged() { testDraftChanged.value=false }
    fun test(config:AiSettings)=work { testNow(config) }
    private suspend fun testNow(config:AiSettings) {
        // v4.1-a.1: 测试连接 is outbound data too; gate + draft check + cleartext check live in ConnectionTester.
        testDraftChanged.value=false
        try { com.example.shuolesa.network.ConnectionTester.run(app,config) }
        catch(e:com.example.shuolesa.network.AiException) {
            if(e.code==com.example.shuolesa.network.AiException.DRAFT_CHANGED) { testDraftChanged.value=true;return } else throw e }
        message.value="转写和整理服务均可连接"
    }
    fun theme(mode:String)=work { app.preferences.setThemeMode(mode) }
    fun restoreAllHints()=work { app.preferences.clearAllHintDismissals();message.value="已恢复" }
    fun recordMode(mode:String)=work { app.preferences.setRecordingMode(mode) }
    fun haptic(value:Boolean)=work { app.preferences.setHapticEnabled(value) }
    fun liveCaptions(value:Boolean)=work { app.preferences.setLiveCaptions(value) }
    fun autoResume(value:Boolean)=work { app.preferences.setAutoResumeAfterCall(value) }
    fun launchStrategy(value:String)=work { app.preferences.setLaunchStrategy(value) }
    fun bitrate(value:Int)=work { app.preferences.setLifelogBitrateKbps(value) }
    fun meetingFormat(value:String)=work { app.preferences.setMeetingFormat(if(value=="aac") "aac_64k" else value) }
    fun blindDuration(mode:String,value:Int)=work { if(mode=="lifelog") app.preferences.setLifelogTriggerDuration(value) else app.preferences.setMeetingTriggerDuration(value) }
    fun export(uri:Uri)=work { BackupMemory(app)(uri);message.value="备份已导出，包含记忆库和原音，不含服务密钥" }
    /** v5.2: one record that can't be cleaned (not transcribed yet) no longer stops the rest; it is kept and counted. */
    fun clearAllAudio()=work { var kept=0
        app.processing.mutateSession { snapshot.value.sessions.forEach { session ->
            if(session.captureState in listOf("STARTING","RECORDING","PAUSED","STOPPING")) return@forEach
            if(runCatching { app.repository.cleanAudio(session.id) }.isFailure) kept++
        } }
        message.value=if(kept==0) "已清理结束会话的原音，文字和待办已保留" else "已清理原音；$kept 条还没转写完，原音先保留" }
}
fun <T:ViewModel> factory(create:()->T)=object:ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <V:ViewModel> create(modelClass:Class<V>):V=create() as V
}
