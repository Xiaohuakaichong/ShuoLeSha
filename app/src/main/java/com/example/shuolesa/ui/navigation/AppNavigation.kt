package com.example.shuolesa.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import com.example.shuolesa.AppContainer
import com.example.shuolesa.audio.CapturePhase
import com.example.shuolesa.data.model.EvidenceRef
import com.example.shuolesa.data.model.TaskState
import com.example.shuolesa.service.AudioCaptureService
import com.example.shuolesa.theme.LocalBrandColors
import com.example.shuolesa.ui.*
import com.example.shuolesa.ui.components.*
import com.example.shuolesa.ui.screens.*
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import kotlinx.serialization.Serializable
import android.app.Activity
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import com.example.shuolesa.data.prefs.AiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable data object Home:NavKey
@Serializable data class Detail(val id:String,val topicId:String?=null,val focusChunk:Long?=null,val focusStart:Int=0,val focusEnd:Int=0):NavKey
@Serializable data class Source(val chunkId:Long,val start:Int,val end:Int):NavKey
@Serializable data class Recall(val sessionId:String?=null):NavKey
@Serializable data object Capture:NavKey
@Serializable data class Settings(val page:String="home",val focusAddress:Boolean=false):NavKey
@Serializable data object OutboundConsent:NavKey

/**
 * v5.0: one calm cross-fade for every screen change. Pushing, the back button and the edge-swipe back gesture all use it,
 * so swiping back fades the page out like the button does, instead of Navigation3's default of shrinking it away.
 */
private val ScreenFade=ContentTransform(fadeIn(tween(220,easing=FastOutSlowInEasing)),fadeOut(tween(180,easing=FastOutSlowInEasing)))

@Composable fun AppNavigation(app:AppContainer) {
    // v4.1-a: first-run / upgrade consent gate. Nothing below (recording, network, permission
    // requests) is reachable before privacy consent.
    val privacy:Boolean? by app.preferences.privacyConsented.collectAsStateWithLifecycle(initialValue=null)
    val onboarded:Boolean? by app.preferences.onboardingDone.collectAsStateWithLifecycle(initialValue=null)
    var openServiceAfterOnboarding by rememberSaveable { mutableStateOf(false) }
    if(privacy==null || onboarded==null) return
    if(privacy!=true || onboarded!=true) { Onboarding(app) { openServiceAfterOnboarding=true };return }
    MainNavigation(app,openServiceAfterOnboarding) { openServiceAfterOnboarding=false }
}

@Composable private fun MainNavigation(app:AppContainer,openService:Boolean,onServiceOpened:()->Unit) {
    val stack=rememberNavBackStack(Home)
    LaunchedEffect(openService) { if(openService) { stack.add(Settings("service"));onServiceOpened() } }
    val outboundConsented by app.preferences.outboundConsented.collectAsStateWithLifecycle(initialValue=true)
    val capture by AudioCaptureService.state.collectAsStateWithLifecycle()
    val memory:MemoryViewModel=viewModel(factory=factory {MemoryViewModel(app)})
    val settings:SettingsViewModel=viewModel(factory=factory {SettingsViewModel(app)})
    val snackbar=remember {SnackbarHostState()}
    // v4.1-a.1: one-shot home message, e.g. "已同意，点录音即可开始" after consenting from a shortcut.
    val homeMessage by app.homeMessage.collectAsStateWithLifecycle()
    LaunchedEffect(homeMessage) { homeMessage?.let { app.homeMessage.value=null;snackbar.showSnackbar(it) } }
    // v4.1-a.3: tile started recording while the app was open -> stay on this screen, top recording bar
    // (as on ⑤) + Snackbar "已开始录音" with "查看" opening ⑤.
    val startedNotice by app.recordingStartedNotice.collectAsStateWithLifecycle()
    LaunchedEffect(startedNotice) { if(startedNotice) { app.recordingStartedNotice.value=false
        if(snackbar.showSnackbar("已开始录音",actionLabel="查看",duration=SnackbarDuration.Short)==SnackbarResult.ActionPerformed && stack.lastOrNull()!=Capture) stack.add(Capture) } }
    // v5.0: the 快捷条 widget's 搜索 button.
    val searchRequested by app.requestedSearch.collectAsStateWithLifecycle()
    LaunchedEffect(searchRequested) { if(searchRequested) { app.requestedSearch.value=false;if(stack.lastOrNull() !is Recall) stack.add(Recall()) } }
    var modeSheet by remember {mutableStateOf(false)}
    var importSheet by remember {mutableStateOf(false)}
    var selectedMode by remember {mutableStateOf("lifelog")}
    val strategy by app.preferences.launchStrategy.collectAsStateWithLifecycle("default")
    val mode by app.preferences.recordingMode.collectAsStateWithLifecycle("lifelog")
    val back:()->Unit={if(stack.size>1) stack.removeAt(stack.lastIndex)}
    // v4.1-b ⑥: 来源页并入详情页。引用 -> 找到片段所属记录 -> 详情「原话」tab 并定位到被引用句。
    val source:(EvidenceRef)->Unit={ref-> val sid=memory.snapshot.value.chunks.find { it.id==ref.chunkId }?.sessionId
        if(sid!=null) stack.add(Detail(sid,null,ref.chunkId,ref.start,ref.end)) else stack.add(Source(ref.chunkId,ref.start,ref.end))}
    val requestedTab by app.requestedTab.collectAsStateWithLifecycle()
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
    Column(Modifier.fillMaxSize()) {
    // v4.1-a.3: top recording bar on every screen except ⑤ itself and Home (Home keeps its dock).
    val showTopBar=capture.active && stack.lastOrNull()!=Capture && stack.lastOrNull()!=Home
    if(showTopBar) {
        val brand=LocalBrandColors.current
        Surface(onClick={stack.add(Capture)},color=brand.record) {
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).heightIn(min=44.dp).padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(brand.onRecord))
                Text("${capture.modeLabel} · ${if(capture.phase==CapturePhase.PAUSED) "已暂停" else "录音中"} ${duration(capture.durationMs)}",
                    Modifier.weight(1f).padding(start=10.dp),style=MaterialTheme.typography.labelLarge,color=brand.onRecord)
                Text("查看",style=MaterialTheme.typography.labelLarge,color=brand.onRecord)
            }
        }
    }
    Box(Modifier.fillMaxSize().then(if(showTopBar) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)) {
        NavDisplay(backStack=stack,onBack=back,
            transitionSpec={ ScreenFade },popTransitionSpec={ ScreenFade },predictivePopTransitionSpec={ _ -> ScreenFade },
            entryDecorators=listOf(rememberSaveableStateHolderNavEntryDecorator(),rememberViewModelStoreNavEntryDecorator()),
            entryProvider=entryProvider {
            entry<Home> {
                var tab by rememberSaveable {mutableIntStateOf(0)}
                // v4.1-b: launcher shortcut「今天」/ organised notification -> tab only, never records.
                LaunchedEffect(requestedTab) { requestedTab?.let { tab=it;app.requestedTab.value=null } }
                val today:TodayViewModel=viewModel(factory=factory {TodayViewModel(app)})
                val tasks:TasksViewModel=viewModel(factory=factory {TasksViewModel(app)})
                Messages(memory,snackbar);Messages(today,snackbar);Messages(tasks,snackbar)
                val snap by memory.snapshot.collectAsStateWithLifecycle()
                val candidates=snap.tasks.count { it.state==TaskState.CANDIDATE }
                val title=listOf("记忆","今日","待办")[tab]
                val subtitle=when(tab) {
                    0->LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE",Locale.CHINA))
                    1->"回顾今天说过的话"
                    else->"${snap.tasks.count { it.state==TaskState.ACTIVE }} 项待办 · $candidates 项待确认"
                }
                val bg=MaterialTheme.colorScheme.background
                Box(Modifier.fillMaxSize().background(bg)) {
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        LargeHeader(title,subtitle) {
                            if(tab==0) RoundIconButton(Icons.Outlined.FileUpload,"导入音频",{importSheet=true},size=44.dp)
                            RoundIconButton(Icons.Outlined.Settings,"设置",{stack.add(Settings())},size=44.dp)
                        }
                        Crossfade(tab,Modifier.weight(1f).fillMaxWidth(),label="tab") { current ->
                            val consentPending=!outboundConsented && snap.jobs.any { it.kind!="IMPORT" && it.state!="COMPLETE" }
                            when(current) {0->MemoryScreen(memory,{id,topic->stack.add(Detail(id,topic))},{stack.add(Recall())},{importSheet=true},{stack.add(Settings("service"))},
                                    consentPending,{stack.add(OutboundConsent)},{stack.add(Settings("service",focusAddress=true))},
                                    onQueue={stack.add(Settings("data"))},onBlindStart={start("lifelog")},
                                    onEnableNotifications={ runCatching { app.context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,app.context.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } })
                                1->TodayScreen(today,{id,topic->stack.add(Detail(id,topic))},source)
                                else->TasksScreen(tasks,source)}
                        }
                    }
                    // Lists scroll under the dock; this fade keeps the last row from colliding with it.
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color.Transparent,bg))))
                    HomeDock(tab,{tab=it},candidates,capture,openCapture,
                        {memory.command(if(capture.phase==CapturePhase.PAUSED) AudioCaptureService.ACTION_RESUME else AudioCaptureService.ACTION_PAUSE)},
                        Modifier.align(Alignment.BottomCenter))
                }
            }
            entry<Detail> { key ->
                val vm:DetailViewModel=viewModel(key="detail:${key.id}:${key.topicId}",factory=factory {DetailViewModel(app,key.id,key.topicId)})
                val player:PlaybackViewModel=viewModel(key="detail-player:${key.id}:${key.topicId}",factory=factory {PlaybackViewModel(app)})
                Messages(vm,snackbar)
                DetailScreen(vm,player,back,source,{stack.add(Recall(key.id))},key.focusChunk?.let { Focus(it,key.focusStart,key.focusEnd) })
            }
            entry<Source> { key ->
                val vm:SourceViewModel=viewModel(key="source:${key.chunkId}",factory=factory {SourceViewModel(app,key.chunkId)})
                // fallback only (chunk not in snapshot yet): resolve and replace with Detail.
                val src by vm.source.collectAsStateWithLifecycle()
                LaunchedEffect(src.session) { src.session?.let { s -> stack.removeAt(stack.lastIndex);stack.add(Detail(s.id,null,key.chunkId,key.start,key.end)) } }
                if(!src.loading && src.session==null) Page("原话来源",back) { p -> Box(Modifier.padding(p)) { EmptyState("来源记录已删除","已确认的待办保留在待办页。") } }
            }
            entry<Recall> { key ->
                val vm:RecallViewModel=viewModel(key="recall:${key.sessionId}",factory=factory {RecallViewModel(app)})
                RecallScreen(vm,back,source,key.sessionId)
            }
            entry<Capture> {
                val snap by memory.snapshot.collectAsStateWithLifecycle()
                // v4.1.1: `capture` changes every 200 ms while recording; recompute only when the data or the
                // session changes, with a set lookup instead of a transcripts × chunks scan.
                val transcribed=remember(snap,capture.sessionId) {
                    val ids=snap.chunks.filter { it.sessionId==capture.sessionId }.mapTo(HashSet()) { it.id }
                    snap.transcripts.count { it.chunkId in ids }
                }
                // v4.2: the latest 原话 of this recording (随身 transcribes 片段 while recording), re-read when one lands.
                val recent by produceState<String?>(null,capture.sessionId,transcribed) {
                    val id=capture.sessionId ?: return@produceState
                    value=runCatching { app.repository.dao.sourcesForSession(id).lastOrNull { it.text.isNotBlank() }?.text }.getOrNull()
                        ?.replace('\n',' ')?.trim()?.takeLast(80)
                }
                // v4.1-b ⑤: no confirm dialog; stop -> back home + Snackbar.
                CaptureScreen(capture,back,{memory.command(it)},transcribed,onStopped={ app.homeMessage.value="已停止，正在整理";stack.clear();stack.add(Home) },recent=recent)
            }
            entry<Settings> { key ->Messages(settings,snackbar);SettingsScreen(settings,key.page,back,{stack.add(Settings(it))},{stack.add(OutboundConsent)},key.focusAddress) }
            entry<OutboundConsent> {
                val config by produceState<AiSettings?>(null) { value=withContext(Dispatchers.IO) { app.preferences.aiSettings() } }
                // v5.0: no service chosen yet -> there is no address to confirm; send the user to pick one first.
                config?.let { c ->
                    if(c.baseUrl.isBlank()) LaunchedEffect(Unit) { stack.removeAt(stack.lastIndex);stack.add(Settings("service")) }
                    // v4.3: consent covers 转写 and, when separate, the 整理 service (AiSettings.outboundTarget)
                    else OutboundConsentScreen(0,1,c.provider,c.baseUrl,onAgree={
                        app.scope.launch { app.preferences.setOutboundConsent(c.outboundTarget);app.processing.wakeConfigWaiting(true);app.processing.requestProcessing() }
                        back()
                    },onLater=back,onBack=back,llmProviderId=c.llmProvider.takeIf { c.split },llmBaseUrl=c.llmBaseUrl.takeIf { c.split })
                }
            }
        })
        SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).padding(16.dp,16.dp,16.dp,100.dp)) { data -> Snackbar(data,shape=RoundedCornerShape(16.dp)) }
    }
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
    ModalBottomSheet(onDismissRequest=onDismiss) {Column(Modifier.padding(horizontal=20.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(if(import) "如何整理这段音频" else "开始录音",Modifier.padding(horizontal=4.dp),style=MaterialTheme.typography.titleLarge)
        GroupCard {
            ActionRow("随身记忆","按话题形成记忆卡",Icons.Outlined.GraphicEq) {onSelect("lifelog")}
            GroupDivider()
            ActionRow("会议记录","整次会话形成会议要点",Icons.Outlined.Groups) {onSelect("meeting")}
        }
    } }
}

/** v5.0 onboarding order. A fresh install: 隐私 → 连接服务 → 数据外发（针对选中的服务）→ 权限. An upgrade: 隐私 → 数据外发. */
private enum class Step { PRIVACY, CONNECT, OUTBOUND, PERMISSION }

/**
 * v4.1-a onboarding. Upgrade from V4 (existing records, or a key saved for the current provider)
 * or re-consent after revocation: 2 steps (隐私同意 + 数据外发). Fresh install: 4 steps. Permissions are only
 * requested when the user taps. v5.0: nothing is preselected; data-outbound is confirmed for the service just chosen,
 * and skipped when none was (there is nowhere to send data to).
 */
@Composable private fun Onboarding(app:AppContainer,onConfigureService:()->Unit) {
    val context=LocalContext.current
    val fromShortcut by app.shortcutConsent.collectAsStateWithLifecycle()
    var upgrade by remember { mutableStateOf<Boolean?>(null) }
    var step by rememberSaveable { mutableIntStateOf(-1) }
    var config by remember { mutableStateOf<AiSettings?>(null) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) {
        app.awaitReady()
        config=app.preferences.aiSettings()
        upgrade=app.preferences.onboardingDone.first() || app.repository.dao.observeSessions().first().isNotEmpty() ||
            app.preferences.apiKey.first().isNotBlank()
        if(step<0) step=if(app.preferences.privacyConsented.first()) 1 else 0
    } }
    val isUpgrade=upgrade ?: return
    val c=config ?: return
    if(step<0) return
    val steps=if(isUpgrade) listOf(Step.PRIVACY,Step.OUTBOUND) else Step.entries
    val total=steps.size
    fun goto(target:Step) { step=steps.indexOf(target) }
    fun finish() { app.scope.launch { app.preferences.setOnboardingDone(true) } }
    var refresh by remember { mutableIntStateOf(0) }
    val scope=rememberCoroutineScope()
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    when(steps.getOrElse(step) { Step.PRIVACY }) {
        Step.PRIVACY->PrivacyConsentScreen(0,total,onAgree={
            if(fromShortcut) {
                // v4.1-a.1 (PRD a.1): from tile / volume keys -> consent, back to home, never auto-record.
                // Data outbound stays unconfirmed; home shows 等确认 and Settings › 隐私 offers it.
                app.shortcutConsent.value=false
                app.homeMessage.value="已同意，点录音即可开始"
                app.scope.launch { app.preferences.setOnboardingDone(true);app.preferences.setPrivacyConsent(true) }
            } else { app.scope.launch { app.preferences.setPrivacyConsent(true) };step=1 }
        },onExit={ app.shortcutConsent.value=false;(context as? Activity)?.finish() },fromShortcut=fromShortcut)
        Step.CONNECT->ConnectServiceStep(steps.indexOf(Step.CONNECT),total,
            onSaveKey={ preset,key -> scope.launch {
                config=withContext(Dispatchers.IO) { app.preferences.applyPreset(preset);app.preferences.setApiKey(key);app.preferences.aiSettings() }
                goto(Step.OUTBOUND) } },
            onConfigure={ onConfigureService();goto(Step.PERMISSION) },onSkip={ goto(Step.PERMISSION) })
        Step.OUTBOUND->
            if(c.baseUrl.isBlank()) LaunchedEffect(Unit) { if(isUpgrade) finish() else goto(Step.PERMISSION) }
            else OutboundConsentScreen(steps.indexOf(Step.OUTBOUND),total,c.provider,c.baseUrl,llmProviderId=c.llmProvider.takeIf { c.split },llmBaseUrl=c.llmBaseUrl.takeIf { c.split },onAgree={
                app.scope.launch { app.preferences.setOutboundConsent(c.outboundTarget);app.processing.wakeConfigWaiting(true) }
                if(isUpgrade) finish() else goto(Step.PERMISSION)
            },onLater={ if(isUpgrade) finish() else goto(Step.PERMISSION) })
        Step.PERMISSION->key(refresh) {
            val mic=ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
            val needNotify=Build.VERSION.SDK_INT>=33
            val notify=!needNotify || ContextCompat.checkSelfPermission(context,"android.permission.POST_NOTIFICATIONS")==PackageManager.PERMISSION_GRANTED
            PermissionStep(steps.indexOf(Step.PERMISSION),total,mic,needNotify,notify,onRequest={
                permissions.launch(buildList { add(Manifest.permission.RECORD_AUDIO);if(needNotify) add("android.permission.POST_NOTIFICATIONS") }.toTypedArray())
            },onDone={ finish() })
        }
    }
}
