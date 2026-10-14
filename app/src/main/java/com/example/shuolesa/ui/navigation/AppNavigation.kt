package com.example.shuolesa.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import com.example.shuolesa.AppContainer
import com.example.shuolesa.audio.CapturePhase
import com.example.shuolesa.data.model.EvidenceRef
import com.example.shuolesa.service.AudioCaptureService
import com.example.shuolesa.ui.*
import com.example.shuolesa.ui.components.*
import com.example.shuolesa.ui.screens.*
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import kotlinx.serialization.Serializable

@Serializable data object Home:NavKey
@Serializable data class Detail(val id:String,val topicId:String?=null):NavKey
@Serializable data class Source(val chunkId:Long,val start:Int,val end:Int):NavKey
@Serializable data class Recall(val sessionId:String?=null):NavKey
@Serializable data object Capture:NavKey
@Serializable data class Settings(val page:String="home"):NavKey

@Composable fun AppNavigation(app:AppContainer) {
    val stack=rememberNavBackStack(Home)
    val capture by AudioCaptureService.state.collectAsStateWithLifecycle()
    val memory:MemoryViewModel=viewModel(factory=factory {MemoryViewModel(app)})
    val settings:SettingsViewModel=viewModel(factory=factory {SettingsViewModel(app)})
    val snackbar=remember {SnackbarHostState()}
    var modeSheet by remember {mutableStateOf(false)}
    var importSheet by remember {mutableStateOf(false)}
    var selectedMode by remember {mutableStateOf("lifelog")}
    val strategy by app.preferences.launchStrategy.collectAsStateWithLifecycle("default")
    val mode by app.preferences.recordingMode.collectAsStateWithLifecycle("lifelog")
    val back:()->Unit={if(stack.size>1) stack.removeAt(stack.lastIndex)}
    val source:(EvidenceRef)->Unit={ref->stack.add(Source(ref.chunkId,ref.start,ref.end))}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) {memory.command(AudioCaptureService.ACTION_START,selectedMode);stack.add(Capture)}
        else memory.message.value="麦克风权限未授予，可继续查看记忆或导入音频"
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri:Uri?->uri?.let {memory.import(it,selectedMode)} }
    fun start(selected:String) {selectedMode=selected
        if(ContextCompat.checkSelfPermission(app.context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) {
            memory.command(AudioCaptureService.ACTION_START,selected);if(stack.lastOrNull()!=Capture) stack.add(Capture)
        } else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val openCapture:()->Unit={if(capture.active) {if(stack.lastOrNull()!=Capture) stack.add(Capture)} else if(strategy=="prompt") modeSheet=true else start(mode)}
    Box(Modifier.fillMaxSize()) {
        NavDisplay(backStack=stack,onBack=back,
            entryDecorators=listOf(rememberSaveableStateHolderNavEntryDecorator(),rememberViewModelStoreNavEntryDecorator()),
            entryProvider=entryProvider {
            entry<Home> {
                var tab by rememberSaveable {mutableIntStateOf(0)}
                val today:TodayViewModel=viewModel(factory=factory {TodayViewModel(app)})
                val tasks:TasksViewModel=viewModel(factory=factory {TasksViewModel(app)})
                Messages(memory,snackbar);Messages(today,snackbar);Messages(tasks,snackbar)
                Page(listOf("记忆","今日","待办")[tab],actions={
                    if(tab==0) IconButton(onClick={importSheet=true}) {Icon(Icons.Outlined.FileUpload,"导入音频")}
                    IconButton(onClick={stack.add(Settings())}) {Icon(Icons.Outlined.Settings,"设置")}
                },bottom={
                    Column {
                        if(capture.active) Surface(onClick=openCapture,color=MaterialTheme.colorScheme.primaryContainer) {
                            Row(Modifier.fillMaxWidth().heightIn(min=60.dp).padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
                                Ceer(if(capture.phase==CapturePhase.PAUSED) CeerPose.PAUSED else CeerPose.LISTENING,Modifier.size(32.dp))
                                Text("${capture.modeLabel} · ${if(capture.phase==CapturePhase.PAUSED) "已暂停" else "录音中"} ${duration(capture.durationMs)}",Modifier.weight(1f).padding(start=12.dp))
                                IconButton(onClick={memory.command(if(capture.phase==CapturePhase.PAUSED) AudioCaptureService.ACTION_RESUME else AudioCaptureService.ACTION_PAUSE)}) {Icon(if(capture.phase==CapturePhase.PAUSED) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,"暂停或继续")}
                            }
                        }
                        NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp) {
                            listOf("记忆" to Icons.Outlined.GraphicEq,"今日" to Icons.Outlined.Today,"待办" to Icons.Outlined.CheckCircle).forEachIndexed { i,(label,icon)->
                                NavigationBarItem(tab==i,{tab=i},icon={Icon(icon,label)},label={Text(label)})
                            }
                        }
                    }
                }) { padding -> Box(Modifier.padding(padding).fillMaxSize()) {
                    when(tab) {0->MemoryScreen(memory,{id,topic->stack.add(Detail(id,topic))},{stack.add(Recall())},{importSheet=true},{stack.add(Settings("service"))})
                        1->TodayScreen(today,{id,topic->stack.add(Detail(id,topic))},source)
                        else->TasksScreen(tasks,source)}
                    FloatingActionButton(onClick=openCapture,modifier=Modifier.align(Alignment.BottomEnd).padding(20.dp),containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary) {
                        Icon(Icons.Default.Mic,if(capture.active) "展开录音" else "开始录音")
                    }
                } }
            }
            entry<Detail> { key ->
                val vm:DetailViewModel=viewModel(key="detail:${key.id}:${key.topicId}",factory=factory {DetailViewModel(app,key.id,key.topicId)})
                val player:PlaybackViewModel=viewModel(key="detail-player:${key.id}:${key.topicId}",factory=factory {PlaybackViewModel(app)})
                Messages(vm,snackbar)
                DetailScreen(vm,player,back,source,{stack.add(Recall(key.id))})
            }
            entry<Source> { key ->
                val vm:SourceViewModel=viewModel(key="source:${key.chunkId}",factory=factory {SourceViewModel(app,key.chunkId)})
                val player:PlaybackViewModel=viewModel(key="source-player:${key.chunkId}",factory=factory {PlaybackViewModel(app)})
                SourceScreen(vm,player,key.start,key.end,back)
            }
            entry<Recall> { key ->
                val vm:RecallViewModel=viewModel(key="recall:${key.sessionId}",factory=factory {RecallViewModel(app)})
                RecallScreen(vm,back,source,key.sessionId)
            }
            entry<Capture> {
                val snap by memory.snapshot.collectAsStateWithLifecycle()
                val transcribed=snap.transcripts.count {t->snap.chunks.any {it.id==t.chunkId && it.sessionId==capture.sessionId}}
                CaptureScreen(capture,back,{memory.command(it)},transcribed)
            }
            entry<Settings> { key ->Messages(settings,snackbar);SettingsScreen(settings,key.page,back,{stack.add(Settings(it))}) }
        })
        SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).padding(16.dp,16.dp,16.dp,100.dp))
    }
    if(modeSheet || importSheet) ModeSheet(importSheet,{modeSheet=false;importSheet=false}) { selected ->
        selectedMode=selected
        if(importSheet) {importSheet=false;picker.launch(arrayOf("audio/*","video/*"))}
        else {modeSheet=false;start(selected)}
    }
}
@Composable private fun Messages(vm:FeatureViewModel,host:SnackbarHostState) {
    LaunchedEffect(vm) {vm.message.collect {message -> if(message!=null) {host.showSnackbar(message);vm.message.value=null} } }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ModeSheet(import:Boolean,onDismiss:()->Unit,onSelect:(String)->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss) {Column(Modifier.padding(24.dp).padding(bottom=24.dp)) {
        Text(if(import) "如何整理这段音频" else "开始录音",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(16.dp))
        ActionRow("随身记忆","按话题形成记忆卡",Icons.Outlined.GraphicEq) {onSelect("lifelog")}
        ActionRow("会议记录","整次会话形成纪要",Icons.Outlined.Groups) {onSelect("meeting")}
    } }
}
