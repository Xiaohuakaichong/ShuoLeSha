package com.example.shuolesa.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.shuolesa.audio.CapturePhase
import com.example.shuolesa.service.AudioCaptureService
import com.example.shuolesa.ui.*

@Composable fun AudioControls(vm:PlaybackViewModel,label:String) {
    val player by vm.state.collectAsStateWithLifecycle()
    val capture by AudioCaptureService.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    Surface(shadowElevation=0.dp,tonalElevation=0.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=20.dp,vertical=12.dp)) {
            Meta(label)
            when { player.loading->LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical=16.dp))
                !player.ready->Meta(player.error ?: "请选择有原音的片段")
                else->{
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick={vm.seek(player.position-10000)}) { Icon(Icons.Default.Replay10,"后退 10 秒") }
                        FilledIconButton(onClick={if(capture.active && capture.phase!=CapturePhase.PAUSED && !player.playing) confirm=true else vm.toggle()}) {
                            Icon(if(player.playing) Icons.Default.Pause else Icons.Default.PlayArrow,if(player.playing) "暂停回听" else "回听原音") }
                        Slider(value=player.position.toFloat().coerceAtMost(player.duration.toFloat()),onValueChange={vm.seek(it.toLong())},valueRange=0f..maxOf(1,player.duration).toFloat(),modifier=Modifier.weight(1f))
                        IconButton(onClick={vm.seek(player.position+10000)}) { Icon(Icons.Default.Forward10,"前进 10 秒") }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Meta(duration(player.position));Meta(duration(player.duration)) }
                    player.error?.let { Meta(it) }
                }
            }
        }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("暂停录音并回听")},text={Text("回听会先暂停当前录音，避免把播放声再次录入。回听结束后可手动继续录音。")},
        confirmButton={TextButton(onClick={confirm=false;vm.toggle(true)}) { Text("暂停并回听") }},dismissButton={TextButton(onClick={confirm=false}) { Text("取消") }})
}
