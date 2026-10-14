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

data class PlaybackState(val ready:Boolean=false,val loading:Boolean=false,val playing:Boolean=false,
    val position:Long=0,val duration:Long=0,val error:String?=null)
class PlaybackViewModel(private val app:AppContainer):ViewModel(),PausePlayback {
    val state=MutableStateFlow(PlaybackState())
    private val player=OpusPlayer(app.context)
    private val mutex=Mutex()
    init {AudioPlaybackGate.register(this);player.onProgressUpdate={ms -> state.value=state.value.copy(position=ms) }
        player.onCompletion={ state.value=state.value.copy(playing=false) }
        player.onError={ state.value=state.value.copy(playing=false,error="音频无法播放，文字来源仍可查看") }
    }
    fun prepare(path:String?,at:Long=0) { viewModelScope.launch {
        mutex.withLock {
            state.value=PlaybackState(loading=true)
            val prepared=withContext(Dispatchers.IO) { player.release();path!=null && File(path).isFile && player.prepare(path) }
            if(prepared) { player.seekTo(at);state.value=PlaybackState(true,position=at,duration=player.getDurationMs()) }
            else state.value=PlaybackState(error="原音已清理或文件缺失，仍可核对文字来源")
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
    fun deactivate() { app.scope.launch {mutex.withLock {player.release();state.value=PlaybackState()}} }
    override suspend fun pausePlayback() {mutex.withLock {player.pause();state.value=state.value.copy(playing=false)}}
    override fun onCleared() {AudioPlaybackGate.unregister(this);app.scope.launch { mutex.withLock { player.release() } } }
}
