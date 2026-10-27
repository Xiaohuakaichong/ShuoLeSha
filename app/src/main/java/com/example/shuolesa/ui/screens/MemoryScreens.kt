package com.example.shuolesa.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.shuolesa.data.db.*
import com.example.shuolesa.data.model.*
import com.example.shuolesa.domain.RecallFilter
import com.example.shuolesa.network.ProcessingReason
import com.example.shuolesa.ui.*
import com.example.shuolesa.ui.components.*
import com.example.shuolesa.ui.logic.*
import java.time.*

/*
 * v4.1-b screens ② 记忆首页 / ④ 今日 / 待办 / 搜索合一.
 * Each screen = a ViewModel wrapper + a stateless *Content composable (light/dark @Preview in V41Previews.kt).
 */

// ---------------- ② 记忆首页 ----------------
data class MemoryUi(val cards:List<MemoryCard>,val banner:Banner?,val blindFailed:Boolean,val hasAnyRecord:Boolean)

@Composable fun MemoryScreen(vm:MemoryViewModel,onOpen:(String,String?)->Unit,onSearch:()->Unit,onImport:()->Unit,onSettings:()->Unit,
    consentPending:Boolean=false,onConsent:()->Unit={},onFixAddress:()->Unit=onSettings,onQueue:()->Unit=onSettings,
    onBlindStart:()->Unit={},onEnableNotifications:()->Unit={}) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val blindFailedPending by vm.blindFailedPending.collectAsStateWithLifecycle(false)
    val blindCardOff by vm.homeBlindCardOff.collectAsStateWithLifecycle(false)
    val blindFailed=NoticePolicy.visible(NoticeKind.HOME_BLIND_FALLBACK,blindFailedPending,blindCardOff)
    var mode by rememberSaveable { mutableStateOf("all") }
    var rangeStart by rememberSaveable {mutableStateOf<Long?>(null)};var rangeEnd by rememberSaveable {mutableStateOf<Long?>(null)}
    var dates by remember {mutableStateOf(false)}
    val range=rangeStart?.let { DateRange(it,rangeEnd!!) }
    val cards=remember(snapshot,mode,rangeStart,rangeEnd) { snapshot.cards().filter { FilterLogic.matches(it.mode,it.time,mode,range) } }
    val banner=remember(snapshot,consentPending) { BannerLogic.pick(snapshot.sessions.map {
        BannerInput(it.id,it.processingState,ProcessingReason.code(it.error)?.name,ProcessingReason.message(it.error)) },consentPending) }
    MemoryContent(MemoryUi(cards,banner,blindFailed,snapshot.sessions.isNotEmpty()),mode,range,
        onMode={mode=it},onDates={dates=true},onClearFilters={mode="all";rangeStart=null;rangeEnd=null},
        onOpen=onOpen,onSearch=onSearch,onImport=onImport,
        onBanner={ when(banner?.action) {
            BannerAction.CONFIRM->onConsent(); BannerAction.RETRY->vm.retry(); BannerAction.FIX->onSettings()
            BannerAction.FIX_ADDRESS->onFixAddress(); BannerAction.VIEW->onQueue(); null->Unit } },
        onRetryAll={vm.retry()},onRetryOne={vm.retrySession(it)},
        onBlindStart={vm.clearBlindFailed();onBlindStart()},onEnableNotifications={vm.clearBlindFailed();onEnableNotifications()},
        onDismissBlindForever={vm.dismissBlindCardForever()})
    if(dates) DateFilterSheet({dates=false}) {start,end->rangeStart=start;rangeEnd=end;dates=false}
}

@Composable fun MemoryContent(ui:MemoryUi,mode:String,range:DateRange?,onMode:(String)->Unit,onDates:()->Unit,onClearFilters:()->Unit,
    onOpen:(String,String?)->Unit,onSearch:()->Unit,onImport:()->Unit,onBanner:()->Unit,onRetryAll:()->Unit,onRetryOne:(String)->Unit,
    onBlindStart:()->Unit,onEnableNotifications:()->Unit,onDismissBlindForever:()->Unit={}) {
    val zone=ZoneId.systemDefault()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp,8.dp,20.dp,148.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        // 搜索框 48dp，圆角 24dp
        item { Surface(onClick=onSearch,shape=CircleShape,color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
            modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
            Row(Modifier.padding(horizontal=18.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search,null,tint=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.width(12.dp))
                Text("搜索原话，或问问记忆",Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary) }
        } }
        // ③ 筛选：全部 / 随身 / 会议 + 日期 ▾
        item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterLogic.MODES.forEach { (key,label) -> AppChip(mode==key,{onMode(key)},label={Text(label)}) }
            AppChip(range!=null,onDates,label={Text(range?.let { FilterLogic.label(it,zone) } ?: "日期")},
                trailingIcon={Icon(Icons.Outlined.ArrowDropDown,null,Modifier.size(18.dp))})
        } }
        if(ui.blindFailed) item { Surface(color=MaterialTheme.colorScheme.errorContainer,shape=MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("刚才的快捷录音没有开始",style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.onErrorContainer)
                Text("后台启动被系统拦下，且通知已关闭，无法提醒你。开启通知后，下次可一点补录。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onErrorContainer)
                Row { TextButton(onClick=onDismissBlindForever) { Text("以后不提示") };TextButton(onClick=onEnableNotifications) { Text("开启通知") };Spacer(Modifier.weight(1f));Button(onClick=onBlindStart) { Text("现在录音") } }
            } } }
        ui.banner?.let { b -> item { HomeBanner(b,onBanner,if(b.kind==BannerKind.FAILED) onRetryAll else null) } }
        if(ui.cards.isEmpty()) item { EmptyState(if(!ui.hasAnyRecord) "把说过的话留在这里" else "没有符合筛选的记录",
            if(!ui.hasAnyRecord) "点右下角开始录音，或导入已有音频。转写后可以搜索、提问和回听。" else "换个模式或日期，看看其他记录。",
            action=if(!ui.hasAnyRecord) "导入音频" else "清除筛选",onAction={if(!ui.hasAnyRecord) onImport() else onClearFilters()}) }
        var previous=""
        ui.cards.forEach { card -> val date=dayHeader(card.time)
            if(date!=previous) { item(key="day:${card.key}") { SectionTitle(date) };previous=date }
            item(key=card.key) { MemoryCardView(card,{onRetryOne(card.sessionId)}) { onOpen(card.sessionId,card.topicId) } }
        }
    }
}

fun dayHeader(ms:Long):String { val d=Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    return when(d) { LocalDate.now()->"今天";LocalDate.now().minusDays(1)->"昨天";else->dateLabel(ms) } }

// ---------------- ④ 今日 · 复盘置顶 ----------------
data class TodayUi(val date:LocalDate,val isToday:Boolean,val digest:String?,val digestAt:Long?,val transcribed:Int,val total:Int,
    val newCount:Int,val running:Boolean,val refs:List<Pair<EvidenceRef,String>>,val cards:List<MemoryCard>,val totalMs:Long,val error:String?)

@Composable fun TodayScreen(vm:TodayViewModel,onOpen:(String,String?)->Unit,onSource:(EvidenceRef)->Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle();val date by vm.selectedDate.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val ui=remember(snapshot,date) {
        val zone=ZoneId.systemDefault();val start=date.atStartOfDay(zone).toInstant().toEpochMilli();val end=date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val chunks=snapshot.chunks.filter { it.createdAt>=start && it.createdAt<end && it.status!="EMPTY" }
        val transcripts=snapshot.transcripts.associateBy { it.chunkId }
        val ready=chunks.filter { it.id in transcripts }
        val digest=snapshot.digests.find { it.date==date.toString() }
        val refs=EvidenceRef.fromJson(digest?.sourcesJson).distinct()
        val jobs=snapshot.jobs.filter { it.kind=="DIGEST" && it.subjectId==date.toString() }
        val cards=snapshot.cards().filter { it.time>=start && it.time<end }
        TodayUi(date,date==LocalDate.now(),digest?.content,digest?.createdAt,ready.size,chunks.size,
            TodayLogic.newSinceDigest(ready.map { it.id },refs.map { it.chunkId },digest?.createdAt,transcripts.mapValues { it.value.createdAt }),
            jobs.any { it.state in listOf("RUNNING","PENDING") },
            refs.take(12).map { ref -> val c=snapshot.chunks.find { it.id==ref.chunkId }
                ref to (c?.let { "${clock(it.createdAt)} · ${if(it.recordingMode=="meeting") "会议" else "随身"}" } ?: "原话来源") },
            cards,cards.distinctBy { it.sessionId }.sumOf { it.duration },
            jobs.filter { it.state in listOf("FAILED","WAITING_CONFIG","WAITING_NETWORK") }.maxByOrNull { it.updatedAt }?.let { ProcessingReason.message(it.error) ?: statusLabel(it.state) })
    }
    TodayContent(ui,busy,{vm.change(it)},{vm.today()},{vm.generate()},onOpen,onSource)
}

@Composable fun TodayContent(ui:TodayUi,busy:Boolean,onShift:(Long)->Unit,onToday:()->Unit,onGenerate:()->Unit,onOpen:(String,String?)->Unit,onSource:(EvidenceRef)->Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp,8.dp,20.dp,148.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Surface(Modifier.fillMaxWidth(),shape=CircleShape,color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={onShift(-1)}) { Icon(Icons.Outlined.ChevronLeft,"前一天") }
                TextButton(onClick=onToday,modifier=Modifier.weight(1f)) { Text(TodayLogic.header(ui.date)+if(ui.isToday) "" else " ▾",style=MaterialTheme.typography.titleMedium) }
                IconButton(onClick={onShift(1)},enabled=!ui.isToday) { Icon(Icons.Outlined.ChevronRight,"后一天") }
            } } }
        // 复盘卡：主角
        item { Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
                Text("当天复盘",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                ui.digestAt?.let { Meta("${clock(it)} 生成") } }
            if(ui.digest!=null) Text(ui.digest,style=MaterialTheme.typography.bodyLarge)
            else Meta(if(ui.total==0) "这一天还没有记录。" else "还没有生成复盘。生成后会标出每句话的依据。")
            if(ui.total>0) { // 覆盖率降级为一行
                LinearProgressIndicator(progress={ ui.transcribed.toFloat()/ui.total },modifier=Modifier.fillMaxWidth().height(4.dp),
                    trackColor=MaterialTheme.colorScheme.primaryContainer,strokeCap=androidx.compose.ui.graphics.StrokeCap.Round)
                Meta("${ui.transcribed}/${ui.total} 片段已转写")
            }
            if(ui.digest!=null && ui.newCount>0) Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(start=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("有 ${ui.newCount} 段新原话还没算进来",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onPrimaryContainer)
                    TextButton(onClick=onGenerate,enabled=!busy && !ui.running) { Text(if(ui.running) "正在更新" else "更新复盘") } } }
            else if(ui.digest==null && ui.transcribed>0) Button(onClick=onGenerate,enabled=!busy && !ui.running,modifier=Modifier.fillMaxWidth()) { Text(if(ui.running) "正在生成复盘" else "生成当天复盘") }
            ui.error?.let { Meta(it) }
        } }
        if(ui.refs.isNotEmpty()) { item { SectionTitle("复盘依据 · ${ui.refs.size} 处原话") }
            items(ui.refs,key={"${it.first.chunkId}:${it.first.start}"}) { (ref,label) -> SourceCard(ref,onSource,label) } }
        if(ui.cards.isNotEmpty()) { item { SectionTitle("当天记录 · ${ui.cards.distinctBy { it.sessionId }.size} 条 · ${humanDuration(ui.totalMs)}") }
            items(ui.cards,key={it.key}) { card -> MemoryCardView(card) { onOpen(card.sessionId,card.topicId) } } }
    }
}
fun humanDuration(ms:Long):String { val m=ms/60000;return if(m>=60) "${m/60} 小时 ${m%60} 分" else "$m 分钟" }

// ---------------- 待办（候选角标在底栏） ----------------
@Composable fun TasksScreen(vm:TasksViewModel,onSource:(EvidenceRef)->Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var state by rememberSaveable { mutableStateOf(TaskState.CANDIDATE) }
    TasksContent(snapshot.tasks,state,busy,{state=it},{t,n->vm.transition(t,n)},onSource)
}
@Composable fun TasksContent(all:List<TaskEntity>,state:String,busy:Boolean,onState:(String)->Unit,onTransition:(TaskEntity,String)->Unit,onSource:(EvidenceRef)->Unit) {
    val tasks=all.filter { it.state==state }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp,8.dp,20.dp,148.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(TaskState.CANDIDATE to "候选",TaskState.ACTIVE to "待办",TaskState.DONE to "完成",TaskState.IGNORED to "忽略").forEach { (key,label) ->
                AppChip(state==key,{onState(key)},label={Text("$label ${all.count { it.state==key }}")}) }
        } }
        if(state==TaskState.CANDIDATE) item { Meta("从原话里提炼的候选，确认后才加入待办。可以先核对原话。") }
        if(tasks.isEmpty()) item { EmptyState(when(state) { TaskState.CANDIDATE->"暂无候选";TaskState.ACTIVE->"待办都处理好了";TaskState.DONE->"还没有完成的待办";else->"没有忽略的候选" },"录音整理出的待办会先出现在候选里。") }
        items(tasks,key={it.id}) { task -> Panel {
            Row(verticalAlignment=Alignment.Top) {
                if(state in listOf(TaskState.ACTIVE,TaskState.DONE)) Checkbox(state==TaskState.DONE,onCheckedChange={onTransition(task,if(it) TaskState.DONE else TaskState.ACTIVE)},enabled=!busy)
                Text(task.text,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
            }
            task.whenHint?.let { Meta("时间提示：$it") }
            val refs=EvidenceRef.fromJson(task.sourcesJson)
            if(task.sourceDeleted) Meta("来源记录已删除，已确认的待办仍保留")
            else if(refs.isEmpty()) Meta("历史待办 · 来源待核对")
            else TextButton(onClick={onSource(refs.first())},contentPadding=PaddingValues(0.dp)) { Icon(Icons.Outlined.Link,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("核对原话") }
            if(state==TaskState.CANDIDATE) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={onTransition(task,TaskState.ACTIVE)},enabled=!busy) { Text("确认待办") }
                TextButton(onClick={onTransition(task,TaskState.IGNORED)},enabled=!busy) { Text("忽略") }
            } else if(state==TaskState.IGNORED) TextButton(onClick={onTransition(task,TaskState.CANDIDATE)},enabled=!busy) { Text("恢复为候选") }
        } }
    }
}

// ---------------- 搜索合一 ----------------
/**
 * One field. 回车 = 搜原话 (local, ranked). Above results: "让 AI 回答" (needs outbound consent + service).
 * The AI only answers from cited 原话; no evidence -> "未找到可核对的来源" (no free answer).
 */
@Composable fun RecallScreen(vm:RecallViewModel,onBack:()->Unit,onSource:(EvidenceRef)->Unit,sessionId:String?=null) {
    val state by vm.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") };var mode by rememberSaveable { mutableStateOf("all") }
    var rangeStart by rememberSaveable {mutableStateOf<Long?>(null)};var rangeEnd by rememberSaveable {mutableStateOf<Long?>(null)};var dates by remember {mutableStateOf(false)}
    fun filter()=RecallFilter(rangeStart ?: 0,rangeEnd ?: Long.MAX_VALUE,mode)
    RecallContent(state,query,{query=it},mode,rangeStart?.let { DateRange(it,rangeEnd!!) },sessionId,onBack,onSource,
        onMode={mode=it;vm.clear()},onDates={dates=true},
        onSearch={ if(query.isNotBlank()) vm.search(query,filter(),false,sessionId) },
        onAsk={ if(query.isNotBlank()) vm.search(query,filter(),true,sessionId) })
    if(dates) DateFilterSheet({dates=false}) {start,end->rangeStart=start;rangeEnd=end;dates=false;vm.clear()}
}

@Composable fun RecallContent(state:RecallState,query:String,onQuery:(String)->Unit,mode:String,range:DateRange?,sessionId:String?,
    onBack:()->Unit,onSource:(EvidenceRef)->Unit,onMode:(String)->Unit,onDates:()->Unit,onSearch:()->Unit,onAsk:()->Unit) {
    val zone=ZoneId.systemDefault()
    Page(if(sessionId==null) "搜索" else "问这次记录",onBack) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { OutlinedTextField(query,onQuery,Modifier.fillMaxWidth(),placeholder={Text("搜索原话，或问问记忆")},singleLine=true,
                leadingIcon={Icon(Icons.Outlined.Search,null)},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                keyboardActions=KeyboardActions(onSearch={onSearch()}),shape=CircleShape) }
            if(sessionId==null) item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterLogic.MODES.forEach { (key,label)->AppChip(mode==key,{onMode(key)},label={Text(label)}) }
                AppChip(range!=null,onDates,label={Text(range?.let { FilterLogic.label(it,zone) } ?: "日期")})
            } }
            if(query.isNotBlank() && !state.loading) item { Surface(onClick=onAsk,color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome,null,tint=MaterialTheme.colorScheme.onPrimaryContainer);Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text("让 AI 回答「${query.take(20)}」",style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("只根据你的原话回答，并给出可核对的来源",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onPrimaryContainer) } } } }
            if(state.loading) item { Panel { Row(verticalAlignment=Alignment.CenterVertically) { Ceer(CeerPose.THINKING);Text("正在查找并核对原话",Modifier.padding(start=12.dp)) };LinearProgressIndicator(Modifier.fillMaxWidth()) } }
            state.error?.let { error -> item { Panel { Text(error,color=MaterialTheme.colorScheme.error);Meta("按回车可以先在本机搜原话。") } } }
            state.answer?.let { answer -> item { Panel { SectionTitle("AI 回答 · 依据原话");Text(answer.answer,style=MaterialTheme.typography.bodyLarge)
                Meta(if(answer.sources.isEmpty()) "没有可核对的原话，不作回答" else "依据 ${answer.sources.size} 处原话，点下方核对") } }
                items(answer.sources) { SourceCard(it,onSource) }
            }
            val ranked=SearchRanking.rank(state.query,state.results.map { SearchHit(it.chunkId,it.text,it.createdAt) })
            val byId=state.results.associateBy { it.chunkId }
            if(ranked.isNotEmpty()) item { SectionTitle("原话 · ${ranked.size} 处") }
            items(ranked,key={it.chunkId}) { hit -> val r=byId.getValue(hit.chunkId);val win=SearchRanking.snippet(r.text,state.query)
                if(!win.isEmpty()) SourceCard(EvidenceRef(r.chunkId,win.first,win.last+1,r.text.substring(win.first,win.last+1)),onSource,
                    "${dateLabel(r.createdAt)} ${clock(r.createdAt)} · ${if(r.recordingMode=="meeting") "会议" else "随身"}${if(r.provenance=="LEGACY") " · 历史原话" else ""}") }
            if(state.searched && !state.loading && state.results.isEmpty() && state.answer==null && state.error==null) item { EmptyState("没找到这句话","试试更短的关键词，或放宽日期和模式。") }
            if(!state.searched) item { EmptyState("从原话里找","输入后按回车搜原话；也可以让 AI 根据原话回答。",CeerPose.IDLE) }
        }
    }
}
