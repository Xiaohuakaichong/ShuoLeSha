package com.example.shuolesa.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.shuolesa.audio.*
import com.example.shuolesa.data.db.*
import com.example.shuolesa.data.model.*
import com.example.shuolesa.domain.RecallFilter
import com.example.shuolesa.service.AudioCaptureService
import com.example.shuolesa.ui.*
import com.example.shuolesa.ui.components.*
import java.time.*

@Composable fun MemoryScreen(vm:MemoryViewModel,onOpen:(String,String?)->Unit,onSearch:()->Unit,onImport:()->Unit,onSettings:()->Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf("all") }
    var dayOnly by rememberSaveable { mutableStateOf(false) }
    var rangeStart by rememberSaveable {mutableStateOf<Long?>(null)};var rangeEnd by rememberSaveable {mutableStateOf<Long?>(null)}
    var dates by remember {mutableStateOf(false)}
    val cards=remember(snapshot,mode,dayOnly,rangeStart,rangeEnd) { snapshot.cards().filter { (mode=="all" || it.mode==mode) &&
        (rangeStart==null || (it.time>=rangeStart!! && it.time<rangeEnd!!)) &&
        (!dayOnly || Instant.ofEpochMilli(it.time).atZone(ZoneId.systemDefault()).toLocalDate()==LocalDate.now()) } }
    val waiting=snapshot.sessions.count { it.processingState in listOf("FAILED","WAITING_CONFIG","WAITING_NETWORK") }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp,12.dp,20.dp,96.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Panel(Modifier.fillMaxWidth(),onSearch) {
            Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.Search,null,tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp));Text("找一句话，或问问记忆",style=MaterialTheme.typography.bodyLarge) }
        } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("all" to "全部","lifelog" to "随身","meeting" to "会议").forEach { (key,label) -> FilterChip(mode==key,{mode=key},label={Text(label)}) }
            FilterChip(dayOnly,{dayOnly=!dayOnly;rangeStart=null;rangeEnd=null},label={Text("仅今天")})
            FilterChip(rangeStart!=null,{dates=true},label={Text(if(rangeStart==null) "日期" else "${dateLabel(rangeStart!!)}–${dateLabel(rangeEnd!!-1)}")})
        } }
        if(waiting>0) item { Panel { Row(verticalAlignment=Alignment.CenterVertically) {
            Ceer(CeerPose.ERROR);Column(Modifier.weight(1f).padding(start=8.dp)) { Text("$waiting 段记忆等待处理",style=MaterialTheme.typography.titleSmall);Meta("原音保存在本机，可以稍后继续") } }
            Row { TextButton(onClick={vm.retry()}) { Text("重试") };TextButton(onClick=onSettings) { Text("检查配置") } }
        } }
        if(cards.isEmpty()) item { EmptyState(if(snapshot.sessions.isEmpty()) "把说过的话留在这里" else "没有符合筛选的记忆",
            if(snapshot.sessions.isEmpty()) "点右下角开始录音，或导入已有音频。转写后可以搜索、提问和回听。" else "切换模式或日期，查看其他记录。",
            action=if(snapshot.sessions.isEmpty()) "导入音频" else "清除筛选",onAction={if(snapshot.sessions.isEmpty()) onImport() else {mode="all";dayOnly=false;rangeStart=null;rangeEnd=null}}) }
        var previous=""
        cards.forEach { card -> val date=dateLabel(card.time)
            if(date!=previous) { item(key="day:${card.key}") { SectionTitle(date) };previous=date }
            item(key=card.key) { MemoryCardView(card) { onOpen(card.sessionId,card.topicId) } }
        }
    }
    if(dates) DateFilterSheet({dates=false}) {start,end->rangeStart=start;rangeEnd=end;dayOnly=false;dates=false}
}

@Composable fun TodayScreen(vm:TodayViewModel,onOpen:(String,String?)->Unit,onSource:(EvidenceRef)->Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle();val date by vm.selectedDate.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val zone=ZoneId.systemDefault();val start=date.atStartOfDay(zone).toInstant().toEpochMilli();val end=date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val chunks=snapshot.chunks.filter { it.createdAt>=start && it.createdAt<end && it.status!="EMPTY" }
    val ready=chunks.filter { chunk -> snapshot.transcripts.any { it.chunkId==chunk.id } }
    val digest=snapshot.digests.find { it.date==date.toString() }
    val refs=EvidenceRef.fromJson(digest?.sourcesJson)
    val changed=digest!=null && ready.any { chunk -> refs.none { it.chunkId==chunk.id } }
    val jobs=snapshot.jobs.filter { it.kind=="DIGEST" && it.subjectId==date.toString() }
    val running=jobs.any { it.state in listOf("RUNNING","PENDING") }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp,12.dp,20.dp,96.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={vm.change(-1)}) { Icon(Icons.Outlined.ChevronLeft,"前一天") }
            Text(if(date==LocalDate.now()) "今天 · $date" else date.toString(),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
            IconButton(onClick={vm.change(1)},enabled=date<LocalDate.now()) { Icon(Icons.Outlined.ChevronRight,"后一天") }
        } }
        item { Panel { SectionTitle("原稿覆盖")
            Text("${ready.size} / ${chunks.size} 段已转写",style=MaterialTheme.typography.titleLarge)
            Meta("${duration(chunks.sumOf { it.durationMs })} 原音 · ${chunks.map { it.sessionId }.distinct().size} 次记录")
            if(ready.size<chunks.size) Meta("还有片段等待转写，当前复盘仅使用已保存的原稿。")
            Button(onClick={vm.generate()},enabled=ready.isNotEmpty() && !busy && !running,modifier=Modifier.fillMaxWidth()) {
                Text(if(running) "正在生成复盘" else if(digest==null) "生成当天复盘" else if(changed) "有新原稿，更新复盘" else "重新生成复盘") }
            jobs.filter { it.state in listOf("FAILED","WAITING_CONFIG","WAITING_NETWORK") }.maxByOrNull { it.updatedAt }?.let { Meta(it.error ?: statusLabel(it.state)) }
        } }
        if(digest!=null) {
            item { Panel { Row(verticalAlignment=Alignment.CenterVertically) { Ceer(CeerPose.SUCCESS);Text("当天复盘",Modifier.padding(start=12.dp),style=MaterialTheme.typography.titleMedium) }
                Meta("生成于 ${clock(digest.createdAt)} · ${refs.map { it.chunkId }.distinct().size} 段可核对来源")
                Text(digest.content,style=MaterialTheme.typography.bodyLarge)
                if(refs.isEmpty()) Meta("历史复盘未保存引用，请通过下方记录核对。")
            } }
            items(refs.distinct().take(12)) { SourceCard(it,onSource) }
        } else if(chunks.isEmpty()) item { EmptyState("这一天还没有记录","选择其他日期，或开始一段新的录音。") }
        if(chunks.isNotEmpty()) item { SectionTitle("当天记录") }
        items(snapshot.cards().filter { it.time>=start && it.time<end },key={it.key}) { card -> MemoryCardView(card) { onOpen(card.sessionId,card.topicId) } }
    }
}

@Composable fun TasksScreen(vm:TasksViewModel,onSource:(EvidenceRef)->Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var state by rememberSaveable { mutableStateOf(TaskState.CANDIDATE) }
    val tasks=snapshot.tasks.filter { it.state==state }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp,12.dp,20.dp,96.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(TaskState.CANDIDATE to "候选",TaskState.ACTIVE to "待办",TaskState.DONE to "完成",TaskState.IGNORED to "忽略").forEach { (key,label) ->
                FilterChip(state==key,{state=key},label={Text("$label ${snapshot.tasks.count { it.state==key }}")}) }
        } }
        if(state==TaskState.CANDIDATE) item { Meta("根据原稿提炼，确认后才加入待办。可以先核对来源。") }
        if(tasks.isEmpty()) item { EmptyState(when(state) { TaskState.CANDIDATE->"暂无候选事项";TaskState.ACTIVE->"待办都处理好了";TaskState.DONE->"还没有完成的事项";else->"没有忽略的事项" },"录音整理出的行动项会出现在候选列表。") }
        items(tasks,key={it.id}) { task -> Panel {
            Row(verticalAlignment=Alignment.Top) {
                if(state in listOf(TaskState.ACTIVE,TaskState.DONE)) Checkbox(state==TaskState.DONE,onCheckedChange={vm.transition(task,if(it) TaskState.DONE else TaskState.ACTIVE)},enabled=!busy)
                Text(task.text,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
            }
            task.whenHint?.let { Meta("时间提示：$it") }
            val refs=EvidenceRef.fromJson(task.sourcesJson)
            if(task.sourceDeleted) Meta("来源记录已删除，已确认的待办仍保留")
            else if(refs.isEmpty()) Meta("历史待办 · 来源待核对")
            else TextButton(onClick={onSource(refs.first())},contentPadding=PaddingValues(0.dp)) { Icon(Icons.Outlined.Link,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("核对原话") }
            if(state==TaskState.CANDIDATE) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={vm.transition(task,TaskState.ACTIVE)},enabled=!busy) { Text("确认待办") }
                TextButton(onClick={vm.transition(task,TaskState.IGNORED)},enabled=!busy) { Text("忽略") }
            } else if(state==TaskState.IGNORED) TextButton(onClick={vm.transition(task,TaskState.CANDIDATE)},enabled=!busy) { Text("恢复为候选") }
        } }
    }
}

@Composable fun RecallScreen(vm:RecallViewModel,onBack:()->Unit,onSource:(EvidenceRef)->Unit,sessionId:String?=null) {
    val state by vm.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") };var mode by rememberSaveable { mutableStateOf("all") };var today by rememberSaveable { mutableStateOf(false) }
    var rangeStart by rememberSaveable {mutableStateOf<Long?>(null)};var rangeEnd by rememberSaveable {mutableStateOf<Long?>(null)};var dates by remember {mutableStateOf(false)}
    fun filter():RecallFilter { val zone=ZoneId.systemDefault();val day=LocalDate.now();return RecallFilter(
        rangeStart ?: if(today) day.atStartOfDay(zone).toInstant().toEpochMilli() else 0,
        rangeEnd ?: if(today) day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() else Long.MAX_VALUE,mode) }
    Page(if(sessionId==null) "找回记忆" else "问这次记录",onBack) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item { OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("输入关键词或问题")},minLines=1,maxLines=3,leadingIcon={Icon(Icons.Outlined.Search,null)}) }
            if(sessionId==null) item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("all" to "全部","lifelog" to "随身","meeting" to "会议").forEach { (key,label)->FilterChip(mode==key,{mode=key;vm.clear()},label={Text(label)}) }
                FilterChip(today,{today=!today;rangeStart=null;rangeEnd=null;vm.clear()},label={Text("仅今天")})
                FilterChip(rangeStart!=null,{dates=true},label={Text(if(rangeStart==null) "日期" else "${dateLabel(rangeStart!!)}–${dateLabel(rangeEnd!!-1)}")})
            } }
            item { Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick={vm.search(query,filter(),false,sessionId)},enabled=query.isNotBlank() && !state.loading,modifier=Modifier.weight(1f)) { Text("搜索原话") }
                Button(onClick={vm.search(query,filter(),true,sessionId)},enabled=query.isNotBlank() && !state.loading,modifier=Modifier.weight(1f)) { Text("问问记忆") }
            } }
            if(state.loading) item { Panel { Row(verticalAlignment=Alignment.CenterVertically) { Ceer(CeerPose.THINKING);Text("正在查找与核对来源",Modifier.padding(start=12.dp)) };LinearProgressIndicator(Modifier.fillMaxWidth()) } }
            state.error?.let { error -> item { Panel { Text(error,color=MaterialTheme.colorScheme.error);Meta("可先用关键词搜索本机原稿。") } } }
            state.answer?.let { answer -> item { Panel { SectionTitle("记忆的回答");Text(answer.answer,style=MaterialTheme.typography.bodyLarge)
                Meta(if(answer.sources.isEmpty()) "没有可核对的来源" else "依据 ${answer.sources.size} 处原话，点下方引用核对") } }
                items(answer.sources) { SourceCard(it,onSource) }
            }
            if(state.results.isNotEmpty()) item { SectionTitle("原稿匹配 · ${state.results.size}") }
            items(state.results,key={it.chunkId}) { result ->
                val at=result.text.indexOf(state.query,ignoreCase=true).coerceAtLeast(0);val start=(at-60).coerceAtLeast(0);val end=minOf(result.text.length,start+260)
                if(end>start) SourceCard(EvidenceRef(result.chunkId,start,end,result.text.substring(start,end)),onSource,
                    "${dateLabel(result.createdAt)} ${clock(result.createdAt)} · ${if(result.provenance=="LEGACY") "历史整理稿" else "转写原稿"}")
            }
            if(state.searched && !state.loading && state.results.isEmpty() && state.answer==null && state.error==null) item { EmptyState("没找到这句话","试试更短的关键词，或放宽日期和模式筛选。") }
            if(!state.searched) item { EmptyState("从原话里找答案","例如：上次讨论的上线时间是什么？\n搜索直接查询本机原稿；提问需要已配置的整理服务。",CeerPose.IDLE) }
        }
    }
    if(dates) DateFilterSheet({dates=false}) {start,end->rangeStart=start;rangeEnd=end;today=false;dates=false;vm.clear()}
}

@Composable fun DetailScreen(vm:DetailViewModel,player:PlaybackViewModel,onBack:()->Unit,onSource:(EvidenceRef)->Unit,onAsk:()->Unit) {
    DisposableEffect(player) {onDispose {player.deactivate()}}
    val state by vm.detail.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle()
    var raw by rememberSaveable { mutableStateOf(false) };var menu by remember { mutableStateOf(false) };var confirm by remember { mutableStateOf<String?>(null) }
    var chunkId by rememberSaveable { mutableLongStateOf(-1L) }
    val chunk=state.chunks.find { it.id==chunkId } ?: state.chunks.firstOrNull()
    LaunchedEffect(chunk?.id,state.session?.sourcePath) { if(chunk!=null) player.prepare(state.session?.sourcePath ?: chunk.filePath,
        if(state.session?.sourcePath!=null) chunk.sourceStartMs ?: 0 else 0) }
    Page(state.topic?.title ?: state.note?.title ?: state.session?.title ?: "记录详情",onBack,actions={
        IconButton(onClick={menu=true}) { Icon(Icons.Outlined.MoreVert,"记录操作") }
        DropdownMenu(menu,{menu=false}) {
            DropdownMenuItem(text={Text("重新整理")},onClick={menu=false;vm.regenerate()},enabled=!busy)
            if(state.topic!=null) DropdownMenuItem(text={Text("移除这张主题卡")},onClick={menu=false;confirm="hide"})
            DropdownMenuItem(text={Text("清理原音")},onClick={menu=false;confirm="audio"})
            DropdownMenuItem(text={Text("删除整次记录")},onClick={menu=false;confirm="session"})
        }
    },bottom={ if(state.session!=null) AudioControls(player,if(state.session?.sourcePath!=null) "音频原件 · 从当前片段起点回听" else "原音 · 片段起点回听") }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if(state.session==null) item { EmptyState("来源记录已删除","已确认的待办会保留在待办页。") }
            else {
                item { Meta("${dateLabel(state.session!!.startedAt)} ${clock(state.session!!.startedAt)} · ${duration(state.session!!.durationMs)} · ${statusLabel(state.session!!.processingState)}") }
                item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { FilterChip(!raw,{raw=false},label={Text("概览")});FilterChip(raw,{raw=true},label={Text("原稿")});Spacer(Modifier.weight(1f));TextButton(onClick=onAsk) { Text("问这次记录") } } }
                if(raw) {
                    if(state.sources.isEmpty()) item { EmptyState("原稿尚未就绪","原音已经保存。等待转写完成，或检查服务配置。",CeerPose.THINKING) }
                    state.sources.forEach { source ->
                        item(key="head:${source.chunkId}") { SectionTitle("${clock(source.createdAt)} · ${if(source.provenance=="LEGACY") "历史整理稿" else "转写原稿"}","回听") {chunkId=source.chunkId} }
                        items(source.text.chunked(1600)) { paragraph -> Text(paragraph,style=MaterialTheme.typography.bodyLarge) }
                    }
                } else {
                    val body=state.topic?.summary ?: state.note?.summary
                    if(body!=null) item { Panel { SectionTitle("记忆要点");Text(body,style=MaterialTheme.typography.bodyLarge) } }
                    else item { Panel { Text("原音已保存",style=MaterialTheme.typography.titleMedium);Meta(state.session?.error ?: "转写和整理完成后，记忆要点会出现在这里。")
                        TextButton(onClick={vm.retry()}) { Text("重试处理") } } }
                    val refs=EvidenceRef.fromJson(state.topic?.sourcesJson ?: state.note?.sourcesJson)
                    if(refs.isNotEmpty()) item { SectionTitle("核对来源") }
                    items(refs.distinct()) { SourceCard(it,onSource) }
                    if(state.tasks.isNotEmpty()) item { Panel { SectionTitle("行动项");state.tasks.forEach { task -> Text("${if(task.state==TaskState.CANDIDATE) "待确认" else if(task.state==TaskState.DONE) "已完成" else "已确认"} · ${task.text}",style=MaterialTheme.typography.bodyMedium) };Meta("在待办页确认、忽略或标记完成。") } }
                    if(state.sources.any { it.provenance=="LEGACY" }) item { Meta("历史文本可能经过旧版 AI 整理，未标记为原始 ASR。需要时可以回听原音核对。") }
                }
                if(state.chunks.size>1) item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { state.chunks.forEachIndexed { index,c -> FilterChip(chunk?.id==c.id,{chunkId=c.id},label={Text("片段 ${index+1}")}) } } }
            }
        }
    }
    confirm?.let { kind -> AlertDialog(onDismissRequest={confirm=null},title={Text(when(kind){"hide"->"移除主题卡";"audio"->"清理原音";else->"删除整次记录"})},
        text={Text(when(kind){"hide"->"仅隐藏这张卡。原稿、原音和待办仍保留。";"audio"->"本机原音会被删除，原稿和待办仍保留。清理后无法回听。";else->"删除此次记录的原音、原稿和候选待办。已确认待办保留，并标记来源已删除。此操作无法撤销。"})},
        confirmButton={TextButton(onClick={confirm=null;when(kind){"hide"->{vm.hideTopic();onBack()};"audio"->vm.cleanAudio();else->vm.delete(onBack)}}) {Text("确认")}},dismissButton={TextButton(onClick={confirm=null}) {Text("取消")}}) }
}

@Composable fun SourceScreen(vm:SourceViewModel,player:PlaybackViewModel,start:Int,end:Int,onBack:()->Unit) {
    DisposableEffect(player) {onDispose {player.deactivate()}}
    val source by vm.source.collectAsStateWithLifecycle()
    val paragraphs=source.text.chunked(1400)
    val list=rememberLazyListState()
    LaunchedEffect(source.loading) { if(!source.loading && paragraphs.isNotEmpty()) list.scrollToItem((start/1400+1).coerceIn(0,paragraphs.size)) }
    LaunchedEffect(source.chunk) { source.chunk?.let { chunk -> player.prepare(source.session?.sourcePath ?: chunk.filePath,
        if(source.session?.sourcePath!=null) chunk.sourceStartMs ?: 0 else 0) } }
    Page("核对原话",onBack,bottom={ if(source.chunk!=null) AudioControls(player,"没有逐字音频时间，回听从该片段起点开始") }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(),state=list,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item { if(source.provenance=="LEGACY") Meta("历史整理稿 · 可能经过旧版 AI 改写，请回听核对") else Meta("转写原稿 · 引用处已高亮") }
            if(!source.loading && source.text.isBlank()) item { EmptyState("文字来源不可用","这处来源可能已经删除，或原稿尚未保存。") }
            itemsIndexed(paragraphs) { index,text ->
                val offset=index*1400;val left=(start-offset).coerceIn(0,text.length);val right=(end-offset).coerceIn(0,text.length)
                val highlight=MaterialTheme.colorScheme.primaryContainer
                Text(buildAnnotatedString { append(text);if(right>left) addStyle(SpanStyle(background=highlight,fontWeight=FontWeight.Medium),left,right) },style=MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable fun CaptureScreen(state:CaptureState,onBack:()->Unit,onCommand:(String)->Unit,transcribed:Int=0) {
    var stop by remember { mutableStateOf(false) }
    val phase=state.phase;val samples=remember { mutableStateListOf<Float>() }
    LaunchedEffect(state.level,state.durationMs) { if(phase==CapturePhase.RECORDING) { samples.add(state.level);if(samples.size>48) samples.removeAt(0) } }
    Page("${state.modeLabel}录音",onBack,actions={TextButton(onClick=onBack) {Text("收起")}}) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            val pose=when(phase){CapturePhase.RECORDING->CeerPose.LISTENING;CapturePhase.PAUSED->CeerPose.PAUSED;CapturePhase.ERROR->CeerPose.ERROR;CapturePhase.IDLE->CeerPose.SUCCESS;else->CeerPose.THINKING}
            Ceer(pose,Modifier.size(100.dp));Spacer(Modifier.height(24.dp))
            Text(when(phase){CapturePhase.STARTING->"准备录音";CapturePhase.RECORDING->"正在听";CapturePhase.PAUSED->"已暂停";CapturePhase.STOPPING->"正在保存";CapturePhase.ERROR->"录音已中断";else->"原音已保存"},style=MaterialTheme.typography.titleLarge)
            Text(duration(state.durationMs),Modifier.padding(vertical=16.dp),style=MaterialTheme.typography.headlineLarge)
            val ink=MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxWidth().height(72.dp)) { val step=size.width/48
                samples.forEachIndexed { index,level -> val amplitude=(level*size.height*3).coerceIn(2f,size.height)
                    val x=size.width-(samples.size-index)*step;drawLine(ink,Offset(x,(size.height-amplitude)/2),Offset(x,(size.height+amplitude)/2),3.dp.toPx()) }
            }
            Spacer(Modifier.height(20.dp));Meta("${state.savedChunks} 个片段已保存 · $transcribed 个已转写")
            Meta(if(state.mode=="lifelog") "封存的片段会逐步转写，停止后统一整理。" else "停止录音后再转写并整理会议。")
            state.error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(top=16.dp)) }
            Spacer(Modifier.height(32.dp))
            if(state.active) Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick={onCommand(if(phase==CapturePhase.PAUSED) AudioCaptureService.ACTION_RESUME else AudioCaptureService.ACTION_PAUSE)},enabled=phase in listOf(CapturePhase.RECORDING,CapturePhase.PAUSED)) { Icon(if(phase==CapturePhase.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,null);Spacer(Modifier.width(8.dp));Text(if(phase==CapturePhase.PAUSED) "继续" else "暂停") }
                Button(onClick={stop=true},enabled=phase!=CapturePhase.STOPPING) { Icon(Icons.Default.Stop,null);Spacer(Modifier.width(8.dp));Text("停止并保存") }
            } else Button(onClick=onBack) { Text("回到记忆") }
            Spacer(Modifier.height(20.dp));if(state.active) Meta("收起或返回会继续保留录音状态。")
        }
    }
    if(stop) AlertDialog(onDismissRequest={stop=false},title={Text("结束这次录音")},text={Text("保存已录内容并开始整理。")},confirmButton={TextButton(onClick={stop=false;onCommand(AudioCaptureService.ACTION_STOP)}) {Text("停止并保存")}},dismissButton={TextButton(onClick={stop=false}) {Text("继续录音")}})
}
