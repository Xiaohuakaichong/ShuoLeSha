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
    val sources:List<TranscriptSource> = emptyList(),val tasks:List<TaskEntity> = emptyList(),val loading:Boolean=true)
class DetailViewModel(app:AppContainer,private val sessionId:String,private val topicId:String?):FeatureViewModel(app) {
    val detail=MutableStateFlow(DetailState())
    init { viewModelScope.launch { app.repository.snapshots.collect { snap -> withContext(Dispatchers.IO) {
        detail.value=DetailState(snap.sessions.find { it.id==sessionId },topicId?.let { app.repository.dao.topic(it) },
            snap.notes.find { it.sessionId==sessionId },snap.chunks.filter { it.sessionId==sessionId },
            app.repository.dao.sourcesForSession(sessionId),snap.tasks.filter { it.sessionId==sessionId },false)
    } } } }
    fun regenerate()=work { app.repository.queueDerive(sessionId,true);app.processing.requestProcessing();message.value="已开始重新整理，原稿和已确认待办会保留" }
    fun hideTopic()=work { topicId?.let { app.repository.dao.setTopicHidden(it,true) };message.value="已从记忆列表移除，原稿和原音仍保留" }
    fun delete(onDone:()->Unit)=work { app.processing.mutateSession {app.repository.deleteSession(sessionId)};withContext(Dispatchers.Main) { onDone() } }
    fun cleanAudio()=work { app.processing.mutateSession {app.repository.cleanAudio(sessionId)};message.value="原音已清理，文字和待办仍保留" }
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
    init { work { config.value=app.preferences.aiSettings() } }
    fun preset(preset:ProviderPreset)=work {config.value=AiSettings(preset.id,preset.defaultBaseUrl,
        app.preferences.credentialFor(preset.defaultBaseUrl),preset.defaultAsrModel,preset.defaultLlmModel,
        config.value?.prompt ?: app.preferences.systemPrompt.first())}
    fun save(config:AiSettings)=work { app.preferences.saveAiSettings(config);this@SettingsViewModel.config.value=config
        app.repository.dao.wakeWaiting(true);app.processing.requestProcessing();message.value="配置已保存" }
    fun test(config:AiSettings)=work {
        app.preferences.validate(config)
        val file=File(app.context.cacheDir,"connection-test.wav")
        try { val writer=com.example.shuolesa.audio.WavWriter(file)
            try { writer.write(ShortArray(16000),16000) } finally { writer.finish() }
            app.ai.transcribe(config,file)
            app.ai.chat(config,"请仅回答连接成功","这是一次连接测试",false)
            message.value="转写和整理服务均可连接"
        } finally { file.delete() }
    }
    fun theme(mode:String)=work { app.preferences.setThemeMode(mode) }
    fun recordMode(mode:String)=work { app.preferences.setRecordingMode(mode) }
    fun haptic(value:Boolean)=work { app.preferences.setHapticEnabled(value) }
    fun autoResume(value:Boolean)=work { app.preferences.setAutoResumeAfterCall(value) }
    fun launchStrategy(value:String)=work { app.preferences.setLaunchStrategy(value) }
    fun bitrate(value:Int)=work { app.preferences.setLifelogBitrateKbps(value) }
    fun meetingFormat(value:String)=work { app.preferences.setMeetingFormat(if(value=="aac") "aac_64k" else value) }
    fun blindDuration(mode:String,value:Int)=work { if(mode=="lifelog") app.preferences.setLifelogTriggerDuration(value) else app.preferences.setMeetingTriggerDuration(value) }
    fun export(uri:Uri)=work { BackupMemory(app)(uri);message.value="备份已导出，包含记忆库和原音，不含服务密钥" }
    fun clearAllAudio()=work { app.processing.mutateSession {snapshot.value.sessions.forEach { session ->
        if(session.captureState !in listOf("STARTING","RECORDING","PAUSED","STOPPING")) app.repository.cleanAudio(session.id)
    }};message.value="已清理结束会话的原音，文字和待办已保留" }
}
fun <T:ViewModel> factory(create:()->T)=object:ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <V:ViewModel> create(modelClass:Class<V>):V=create() as V
}
