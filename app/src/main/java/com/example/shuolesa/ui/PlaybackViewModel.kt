package com.example.shuolesa.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shuolesa.AppContainer
import com.example.shuolesa.audio.*
import com.example.shuolesa.service.AudioCaptureService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** v4.2: [completions] counts finished playbacks (continue with the next 片段); [speed] = 1×/1.5×/2×. */
data class PlaybackState(val ready:Boolean=false,val loading:Boolean=false,val playing:Boolean=false,
    val position:Long=0,val duration:Long=0,val error:String?=null,val completions:Int=0,val speed:Float=1f)
class PlaybackViewModel(private val app:AppContainer):ViewModel(),PausePlayback {
    val state=MutableStateFlow(PlaybackState())
    private val player=OpusPlayer(app.context)
    private val mutex=Mutex()
    init {AudioPlaybackGate.register(this);player.onProgressUpdate={ms -> state.value=state.value.copy(position=ms) }
        player.onCompletion={ state.value=state.value.copy(playing=false,completions=state.value.completions+1) }
        player.onError={ state.value=state.value.copy(playing=false,error="音频无法播放，文字来源仍可查看") }
    }
    fun prepare(path:String?,at:Long=0) { viewModelScope.launch {
        mutex.withLock {
            val keep=state.value
            state.value=PlaybackState(loading=true,completions=keep.completions,speed=keep.speed)
            val prepared=withContext(Dispatchers.IO) { player.release();path!=null && File(path).isFile && player.prepare(path) }
            if(prepared) { player.setSpeed(keep.speed);player.seekTo(at)
                state.value=PlaybackState(true,position=at,duration=player.getDurationMs(),completions=keep.completions,speed=keep.speed) }
            else state.value=PlaybackState(error="原音已清理或文件缺失，仍可核对文字来源",completions=keep.completions,speed=keep.speed)
        }
    } }
    fun toggle(pauseRecording:Boolean=false) { viewModelScope.launch {
        if(pauseRecording && AudioCaptureService.state.value.active && AudioCaptureService.state.value.phase!=CapturePhase.PAUSED) {
            app.context.startService(Intent(app.context,AudioCaptureService::class.java).setAction(AudioCaptureService.ACTION_PAUSE))
            val paused=withTimeoutOrNull(8000) { val capture=AudioCaptureService.state.first {it.phase==CapturePhase.PAUSED || !it.active};capture.phase==CapturePhase.PAUSED || !capture.active }
            if(paused!=true) { state.value=state.value.copy(error="录音尚未暂停，请稍后回听");return@launch }
        }
        mutex.withLock { if(!state.value.ready) return@withLock
            if(player.isPlaying()) player.pause() else player.start()
            state.value=state.value.copy(playing=player.isPlaying())
        }
    } }
    fun seek(ms:Long) { viewModelScope.launch { mutex.withLock { player.seekTo(ms);state.value=state.value.copy(position=ms.coerceIn(0,state.value.duration)) } } }
    /** v4.1-b ⑥-A: tap a sentence -> seek and play (pausing recording first when asked). */
    fun seekAndPlay(ms:Long,pauseRecording:Boolean=false) { viewModelScope.launch {
        mutex.withLock { if(!state.value.ready) return@withLock;player.seekTo(ms);state.value=state.value.copy(position=ms.coerceIn(0,state.value.duration)) }
        if(!state.value.playing) toggle(pauseRecording)
    } }
    /** Quote entry / next 片段 auto-play: wait for prepare() to finish, then start (pausing recording when asked). */
    suspend fun awaitReadyAndPlay(pauseRecording:Boolean=false) { val s=withTimeoutOrNull(5000) { state.first { !it.loading } } ?: return
        if(s.ready && !s.playing) toggle(pauseRecording) }
    /** v4.2: cycles 1× → 1.5× → 2× → 1×. */
    fun cycleSpeed() { viewModelScope.launch { mutex.withLock {
        val next=when { state.value.speed<1.25f->1.5f; state.value.speed<1.75f->2f; else->1f }
        player.setSpeed(next);state.value=state.value.copy(speed=next) } } }
    fun deactivate() { app.scope.launch {mutex.withLock {player.release();state.value=PlaybackState()}} }
    override suspend fun pausePlayback() {mutex.withLock {player.pause();state.value=state.value.copy(playing=false)}}
    override fun onCleared() {AudioPlaybackGate.unregister(this);app.scope.launch { mutex.withLock { player.release() } } }
}
