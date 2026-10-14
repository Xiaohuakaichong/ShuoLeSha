package com.example.shuolesa.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.shuolesa.AppContainer
import com.example.shuolesa.audio.*
import com.example.shuolesa.data.db.RecordingSessionEntity
import com.example.shuolesa.util.HapticFeedback
import com.example.shuolesa.util.PhoneStateMonitor
import com.example.shuolesa.util.StorageChecker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.math.sqrt

class AudioCaptureService: Service() {
    companion object {
        const val ACTION_START="com.example.shuolesa.ACTION_START_RECORDING"
        const val ACTION_STOP="com.example.shuolesa.ACTION_STOP_RECORDING"
        const val ACTION_PAUSE="com.example.shuolesa.ACTION_PAUSE_RECORDING"
        const val ACTION_RESUME="com.example.shuolesa.ACTION_RESUME_RECORDING"
        const val EXTRA_RECORDING_MODE="com.example.shuolesa.EXTRA_RECORDING_MODE"
        private val snapshot=MutableStateFlow(CaptureState())
        val state=snapshot.asStateFlow()
        val isRunning get()=snapshot.value.active
        val elapsedMs get()=snapshot.value.durationMs
        val currentRecordingMode get()=snapshot.value.mode
        val currentRecordingModeTitle get()=snapshot.value.modeLabel
        val currentRecordingFormatDesc get()=if(currentRecordingMode=="meeting") "会议录音 · 原件保留" else "压缩录音 · 原件保留"
    }
    private val app by lazy { AppContainer.from(this) }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val commands=Mutex()
    private var recorder: AudioRecorder?=null
    private var chunks: ChunkManager?=null
    private var capture: Job?=null
    private var renewal: Job?=null
    private var wake: PowerManager.WakeLock?=null
    private var phone: PhoneStateMonitor?=null
    private lateinit var notifications: NotificationHelper
    private var startedAt=0L
    private var finishing=false
    private var pausedForCall=false
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onCreate() { super.onCreate();notifications=NotificationHelper(this) }

    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        when(intent?.action) {
            ACTION_START -> if(!isRunning) {
                if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
                    snapshot.value=CaptureState(phase=CapturePhase.ERROR,error="请授予麦克风权限后重试");stopSelf()
                } else {
                    snapshot.value=CaptureState(phase=CapturePhase.STARTING)
                    startedAt=System.currentTimeMillis()
                    try {
                        ServiceCompat.startForeground(this,NotificationHelper.NOTIFICATION_ID,
                            notifications.buildRecordingNotification(startedAt,"准备录音"),
                            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
                        scope.launch { commands.withLock { start(intent.getStringExtra(EXTRA_RECORDING_MODE)) } }
                    } catch(e: Exception) { snapshot.value=CaptureState(phase=CapturePhase.ERROR,error="无法启动录音服务，请在应用内重试");stopSelf() }
                }
            }
            ACTION_PAUSE -> scope.launch { commands.withLock { pausedForCall=false;pause() } }
            ACTION_RESUME -> scope.launch { commands.withLock { resume() } }
            ACTION_STOP,NotificationHelper.ACTION_FORCE_STOP -> scope.launch { commands.withLock { finish() } }
        }
        // Never restart a microphone just because Android recreates a dead service.
        return START_NOT_STICKY
    }

    private suspend fun start(override: String?) {
        try {
            app.awaitReady()
            check(StorageChecker(this).hasEnoughStorage()) { "存储空间不足，请先清理原音" }
            val prefs=app.preferences
            val mode=override?.takeIf { it in listOf("lifelog","meeting") } ?: prefs.recordingMode.first()
            val id=UUID.randomUUID().toString()
            app.repository.createSession(RecordingSessionEntity(id,mode,startedAt,title=if(mode=="meeting") "会议录音" else "随身记录",captureState="STARTING"))
            chunks=ChunkManager(this,app.repository,id,mode,prefs.lifelogBitrateKbps.first(),prefs.meetingFormat.first())
            snapshot.value=CaptureState(CapturePhase.STARTING,id,mode)
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED) {
                phone=PhoneStateMonitor(this).also { monitor -> monitor.start(object: PhoneStateMonitor.PhoneStateCallback {
                    override fun onCallStarted() { scope.launch { commands.withLock {
                        if(snapshot.value.phase==CapturePhase.RECORDING) {pausedForCall=true;pause()}
                    } } }
                    override fun onCallEnded() { scope.launch {commands.withLock {
                        if(pausedForCall && prefs.autoResumeAfterCall.first()) resume()
                        pausedForCall=false
                    }} }
                }) }
            }
            startCapture()
            if(prefs.hapticEnabled.first()) HapticFeedback(this).singlePulse()
        } catch(e: Exception) { finish(e.message ?: "录音启动失败",true) }
    }
    private suspend fun startCapture() {
        com.example.shuolesa.audio.AudioPlaybackGate.pauseAll()
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
            throw SecurityException("麦克风权限已撤销，请重新授予权限")
        val native=AudioRecorder()
        if(!native.init()) { native.release();error("麦克风初始化失败") }
        recorder=native
        val manager=chunks ?: error("录音会话不可用")
        app.repository.dao.updateCapture(manager.sessionId,"RECORDING",null,manager.durationMs)
        snapshot.value=snapshot.value.copy(phase=CapturePhase.RECORDING,error=null)
        withContext(Dispatchers.Main) { notifications.updateRecording(System.currentTimeMillis()-manager.durationMs,snapshot.value.modeLabel) }
        val power=getSystemService(PowerManager::class.java)
        wake=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"shuolesha:recording").apply { setReferenceCounted(false);acquire(600_000) }
        renewal=scope.launch { while(isActive) { delay(300_000);wake?.acquire(600_000) } }
        capture=scope.launch {
            var emittedAt=0L;var checkedAt=System.currentTimeMillis();var lastSaved=manager.savedChunks
            try {
                native.capture { pcm,count ->
                    manager.onPcmFrame(pcm,count)
                    val now=System.currentTimeMillis()
                    if(now-checkedAt>60_000) { check(StorageChecker(this@AudioCaptureService).hasEnoughStorage(25)) { "存储空间不足，已停止并保留录音" };checkedAt=now }
                    if(now-emittedAt>=200) {
                        var energy=0.0;repeat(count) { energy+=pcm[it].toDouble()*pcm[it] }
                        snapshot.value=snapshot.value.copy(durationMs=manager.durationMs,savedChunks=manager.savedChunks,
                            level=(sqrt(energy/count)/32768).toFloat().coerceIn(0f,1f))
                        emittedAt=now
                    }
                    if(manager.savedChunks>lastSaved) { lastSaved=manager.savedChunks;app.processing.requestProcessing() }
                }
            } catch(e: CancellationException) { throw e }
            catch(e: SecurityException) { scope.launch { commands.withLock {finish("麦克风权限已撤销，已保存内容仍保留",true)} } }
            catch(e: Exception) { scope.launch { commands.withLock { finish(e.message ?: "录音中断",true) } } }
            finally { native.release() }
        }
    }
    private suspend fun joinCapture() {
        recorder?.stop()
        capture?.cancelAndJoin()
        recorder=null;capture=null
        renewal?.cancel();renewal=null
        wake?.let { if(it.isHeld) it.release() };wake=null
    }
    private suspend fun pause() {
        if(snapshot.value.phase!=CapturePhase.RECORDING) return
        try {
            joinCapture();chunks?.closeChunk()
            val manager=chunks ?: return
            snapshot.value=snapshot.value.copy(phase=CapturePhase.PAUSED,durationMs=manager.durationMs,savedChunks=manager.savedChunks,level=0f)
            app.repository.dao.updateCapture(manager.sessionId,"PAUSED",null,manager.durationMs)
            withContext(Dispatchers.Main) { notifications.updateRecording(startedAt,snapshot.value.modeLabel,true) }
            app.processing.requestProcessing()
        } catch(e: Exception) { finish(e.message ?: "暂停失败",true) }
    }
    private suspend fun resume() {
        if(snapshot.value.phase!=CapturePhase.PAUSED) return
        try { startCapture() } catch(e: Exception) { finish(e.message ?: "无法继续录音",true) }
    }
    private suspend fun finish(reason: String?=null,interrupted: Boolean=false) {
        if(finishing) return
        finishing=true
        val manager=chunks
        var error=reason
        snapshot.value=snapshot.value.copy(phase=CapturePhase.STOPPING,level=0f)
        try {
            joinCapture()
            manager?.closeChunk()
        } catch(e: Exception) { error=e.message ?: "结束录音时出现问题，原件已保留" }
        finally {
            withContext(NonCancellable) {
            recorder?.release();recorder=null
            renewal?.cancel();wake?.let { if(it.isHeld) it.release() };wake=null
            phone?.stop();phone=null
            try { if(manager!=null) {
                app.repository.closeSession(manager.sessionId,manager.durationMs,interrupted || error!=null)
                if(error!=null) app.repository.dao.updateProcessing(manager.sessionId,"FAILED",error)
                app.repository.queueDerive(manager.sessionId)
            }} catch(e:Exception) {error="原音已保留，下次启动会恢复记录"}
            chunks=null
            snapshot.value=CaptureState(if(error==null) CapturePhase.IDLE else CapturePhase.ERROR,
                manager?.sessionId,snapshot.value.mode,manager?.durationMs ?: 0,savedChunks=manager?.savedChunks ?: 0,error=error)
            app.processing.requestProcessing()
            withContext(Dispatchers.Main) { stopForeground(STOP_FOREGROUND_REMOVE);stopSelf() }
            }
        }
    }
    override fun onDestroy() {
        recorder?.stop()
        val active=capture
        active?.cancel()
        if(snapshot.value.active && !finishing) {
            app.scope.launch { commands.withLock { runCatching { active?.join();finish("录音服务已中断，已保存内容会恢复",true) } } }
        }
        renewal?.cancel();wake?.let { if(it.isHeld) it.release() }
        phone?.stop()
        scope.cancel()
        super.onDestroy()
    }
}
