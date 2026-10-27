package com.example.shuolesa.ui.screens

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.shuolesa.audio.*
import com.example.shuolesa.data.db.*
import com.example.shuolesa.data.model.*
import com.example.shuolesa.network.ProcessingReason
import com.example.shuolesa.service.AudioCaptureService
import com.example.shuolesa.ui.*
import com.example.shuolesa.ui.components.*
import com.example.shuolesa.ui.logic.*

/*
 * v4.1-b ⑥ 记录详情（合并来源页）: 要点 / 原话 tabs + bottom player with 片段 chips.
 * ⑥-A / ⑥-B is chosen per 片段 by SegmentPlayback.view(text, segmentsJson). Quote entry ("核对原话" from 复盘/搜索/
 * 待办) opens this same page on the 原话 tab with the quoted sentence highlighted (Source route is merged here).
 * v4.2 (design: 说了啥 4.2 界面): all 片段 read as one transcript (TranscriptLayout), speaker turns with fixed colours and a
 * speaker filter, playback continues into the next 片段, 1×/1.5×/2×, capture marks inline, 待办 confirmed on the 要点 tab.
 */

data class SegmentUi(val chunkId:Long,val index:Int,val header:String,val text:String,val view:SegmentView,val base:Long,val provenance:String)
data class Focus(val chunkId:Long,val start:Int,val end:Int)

@Composable fun DetailScreen(vm:DetailViewModel,player:PlaybackViewModel,onBack:()->Unit,onSource:(EvidenceRef)->Unit,onAsk:()->Unit,focus:Focus?=null) {
    DisposableEffect(player) {onDispose {player.deactivate()}}
    val state by vm.detail.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle()
    val playback by player.state.collectAsStateWithLifecycle()
    val capture by AudioCaptureService.state.collectAsStateWithLifecycle()
    var rawTab by rememberSaveable { mutableStateOf(focus!=null) }
    var menu by remember { mutableStateOf(false) };var confirm by remember { mutableStateOf<String?>(null) }
    var askPause by remember { mutableStateOf<(()->Unit)?>(null) }
    var chunkId by rememberSaveable { mutableLongStateOf(focus?.chunkId ?: -1L) }
    var follow by rememberSaveable { mutableStateOf(true) }
    var speakerFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val imported=state.session?.sourcePath!=null
    // v4.2: speaker names of this recording (display only) + rename dialog target (key, default label, current name)
    val names=remember(state.session?.speakerNamesJson) { SpeakerNames.parse(state.session?.speakerNamesJson) }
    var renaming by remember { mutableStateOf<Triple<String,String,String>?>(null) }
    val context=LocalContext.current
    val segments=remember(state) { state.chunks.mapIndexed { i,c -> val src=state.sources.find { it.chunkId==c.id }
        SegmentUi(c.id,i,"片段 ${i+1} · ${clock(c.createdAt)}–${clock(c.createdAt+c.durationMs)}",src?.text.orEmpty(),
            SegmentPlayback.view(src?.text.orEmpty(),state.segments[c.id]),if(imported) c.sourceStartMs ?: 0 else 0,src?.provenance ?: "ASR") } }
    val transcript=remember(segments) { segments.map { TranscriptSegment(it.chunkId,it.index,it.header,it.text,it.view,it.base) } }
    val marks=remember(state.marks,state.chunks) { MarkLayout.toChunks(state.marks,state.chunks.map { it.id to it.durationMs }) }
    val speakers=remember(transcript) { TranscriptLayout.speakers(transcript) }
    val slots=remember(transcript) { TranscriptLayout.slots(transcript) }
    val share=remember(transcript) { TranscriptLayout.share(transcript) }
    val filter=speakerFilter?.takeIf { f -> speakers.any { it.key==f } }
    val rows=remember(transcript,marks,filter) { TranscriptLayout.rows(transcript,marks,filter) }
    val segment=segments.find { it.chunkId==chunkId } ?: segments.firstOrNull()
    val chunk=state.chunks.find { it.id==segment?.chunkId }
    // Recorded 片段 are separate files; an import is one file with offsets, so its playing 片段 follows the position.
    val playable=state.session?.sourcePath ?: chunk?.filePath
    val active=if(imported && playback.ready) segments.lastOrNull { it.base<=playback.position } ?: segment else segment
    val current=active?.let { s -> SegmentPlayback.current(s.view,playback.position-s.base)?.let { s.chunkId to it } }.takeIf { playback.ready }
    // Where the next prepare starts, and whether it keeps playing (next 片段, or a tap in another 片段).
    var autoplay by remember { mutableStateOf(focus!=null) }
    var pendingSeek by remember { mutableStateOf<Long?>(null) }
    var continuePlay by remember { mutableStateOf(false) }
    var pauseForPlay by remember { mutableStateOf(false) }
    LaunchedEffect(playable) {
        val seg=segment ?: return@LaunchedEffect // a 片段 whose audio was cleaned prepares null -> "这个片段没有原音"
        // quote entry: A -> play from 1 s before the cited sentence; B -> prepared at 片段 start
        val quoteSentence=focus?.takeIf { autoplay && it.chunkId==seg.chunkId }?.let { SegmentPlayback.sentenceForQuote(seg.view,seg.text,it.start) }
        val at=pendingSeek ?: if(quoteSentence!=null) SegmentPlayback.seekFor(seg.view,quoteSentence,seg.base,1000) else seg.base
        val play=continuePlay || (autoplay && seg.view.mode==SegmentMode.A_TIMESTAMPS && quoteSentence!=null && !capture.active)
        val pause=pauseForPlay
        pendingSeek=null;continuePlay=false;pauseForPlay=false;autoplay=false
        player.prepare(playable,at)
        if(play) player.awaitReadyAndPlay(pause)
    }
    // v4.2: a recorded 片段 that plays to its end continues with the next one (Otter-style continuous playback).
    var seenCompletions by remember { mutableIntStateOf(playback.completions) }
    LaunchedEffect(playback.completions) {
        if(playback.completions>seenCompletions) {
            seenCompletions=playback.completions
            if(!imported) segments.getOrNull((segment?.index ?: -1)+1)?.let { next -> pendingSeek=next.base;continuePlay=true;chunkId=next.chunkId }
        }
    }
    fun guarded(action:()->Unit) { if(capture.active && capture.phase!=CapturePhase.PAUSED) askPause=action else action() }
    /** Plays 片段 [seg] from [at] (position in its playable file). */
    fun playFrom(seg:SegmentUi,at:Long) { guarded {
        if(imported || seg.chunkId==segment?.chunkId) { chunkId=seg.chunkId;player.seekAndPlay(at,pauseRecording=capture.active) }
        else { pendingSeek=at;continuePlay=true;pauseForPlay=capture.active;chunkId=seg.chunkId }
    } }
    /** Moves the player to 片段 [seg]; keeps playing if it was. */
    fun select(seg:SegmentUi) {
        if(imported || seg.chunkId==segment?.chunkId) { chunkId=seg.chunkId;player.seek(seg.base);return }
        pendingSeek=seg.base;continuePlay=playback.playing;chunkId=seg.chunkId
    }
    val title=state.topic?.title ?: state.note?.title ?: state.session?.title ?: "记录详情"
    /** v4.2 导出原话: plain text with times, speaker names and marks, handed to the system share sheet. */
    fun exportTranscript() {
        val s=state.session ?: return
        val text=TranscriptExport.text(title,"${dateLabel(s.startedAt)} ${clock(s.startedAt)} · ${if(s.mode=="meeting") "会议" else "随身"} · ${humanDuration(s.durationMs)}",
            segments.map { ExportSegment(it.chunkId,it.header,it.view,marks[it.chunkId].orEmpty()) },names)
        runCatching { context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
            .setType("text/plain").putExtra(android.content.Intent.EXTRA_SUBJECT,title).putExtra(android.content.Intent.EXTRA_TEXT,text),"导出原话")) }
    }
    Page(title,onBack,actions={
        IconButton(onClick={menu=true}) { Icon(Icons.Outlined.MoreVert,"记录操作") }
        DropdownMenu(menu,{menu=false}) {
            DropdownMenuItem(text={Text("导出原话")},onClick={menu=false;exportTranscript()},enabled=state.sources.any { it.text.isNotBlank() })
            DropdownMenuItem(text={Text("重新整理")},onClick={menu=false;vm.regenerate()},enabled=!busy)
            if(state.topic!=null) DropdownMenuItem(text={Text("移除这张要点卡")},onClick={menu=false;confirm="hide"})
            DropdownMenuItem(text={Text("清理原音")},onClick={menu=false;confirm="audio"})
            DropdownMenuItem(text={Text("删除整条记录")},onClick={menu=false;confirm="session"})
        }
    },bottom={ if(state.session!=null && active!=null) DetailPlayerBar(playback,active.index,segments.size,
            clock(state.chunks.find { it.id==active.chunkId }?.createdAt ?: state.session!!.startedAt),
            active.view.mode==SegmentMode.A_TIMESTAMPS,follow,{follow=it},{player.cycleSpeed()},
            segments.getOrNull(active.index-1)?.let { p -> { select(p) } },segments.getOrNull(active.index+1)?.let { n -> { select(n) } },
            onToggle={ if(playback.playing) player.toggle() else guarded { player.toggle(pauseRecording=capture.active) } },
            onSeek={player.seek(it)}) }) { padding ->
        DetailContent(Modifier.padding(padding),state,rawTab,{rawTab=it},segments,rows,speakers,slots,share,filter,{speakerFilter=it},names,
            current,follow && playback.playing,focus,{ ref -> TranscriptLayout.refLabel(transcript,names,ref) },
            onSource=onSource,onAsk=onAsk,onRetry={vm.retry()},
            onPlaySentence={ id,i -> segments.find { it.chunkId==id }?.let { s -> playFrom(s,SegmentPlayback.seekFor(s.view,i,s.base)) } },
            onPlaySegment={ id -> segments.find { it.chunkId==id }?.let { s -> playFrom(s,s.base) } },
            onPlayMark={ id,at -> segments.find { it.chunkId==id }?.let { s -> playFrom(s,s.base+MarkLayout.seekFor(at)) } },
            onSpeaker={ key,label -> renaming=Triple(key,label,names[key].orEmpty()) },
            onTask={ task,next -> vm.transition(task,next) })
    }
    renaming?.let { (key,label,currentName) ->
        var name by remember(key) { mutableStateOf(currentName) }
        AlertDialog(onDismissRequest={renaming=null},title={Text("给说话人起名")},
            text={ Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name,{name=it.take(SpeakerNames.MAX_NAME)},singleLine=true,label={Text("名字")},placeholder={Text(label)})
                Meta("只改这条记录里的显示名，保存在本机。说话人是按声音自动区分的，可能分错，请以原音为准。留空恢复默认。") } },
            confirmButton={TextButton(onClick={renaming=null;vm.renameSpeaker(key,name)}) { Text("保存") }},
            dismissButton={TextButton(onClick={renaming=null}) { Text("取消") }})
    }
    askPause?.let { action -> AlertDialog(onDismissRequest={askPause=null},title={Text("暂停录音并回听")},text={Text("回听会先暂停当前录音，避免把播放声再次录进去。回听结束后可以手动继续录音。")},
        confirmButton={TextButton(onClick={askPause=null;action()}) { Text("暂停并回听") }},dismissButton={TextButton(onClick={askPause=null}) { Text("取消") }}) }
    confirm?.let { kind -> AlertDialog(onDismissRequest={confirm=null},title={Text(when(kind){"hide"->"移除要点卡";"audio"->"清理原音";else->"删除整条记录"})},
        text={Text(when(kind){"hide"->"只隐藏这张卡。原话、原音和待办都保留。";"audio"->"本机原音会被删除，原话和待办保留。清理后无法回听。";else->"删除这条记录的原音、原话和候选待办。已确认的待办保留，并标记来源已删除。无法撤销。"})},
        confirmButton={TextButton(onClick={confirm=null;when(kind){"hide"->{vm.hideTopic();onBack()};"audio"->vm.cleanAudio();else->vm.delete(onBack)}}) {Text("确认")}},dismissButton={TextButton(onClick={confirm=null}) {Text("取消")}}) }
}

/** v4.2: speaker colours (text, tint) by slot; lighter on the dark theme so names stay readable on both. */
@Composable fun speakerColors(slot:Int):Pair<Color,Color> {
    val dark=MaterialTheme.colorScheme.background.luminance()<0.5f
    val light=listOf(0xFF0E7C86 to 0xFFE0F2F3,0xFFB45309 to 0xFFFBEBD7,0xFF6D4ACF to 0xFFECE6FA,0xFF2F7D32 to 0xFFE3F1E4,0xFFB4235E to 0xFFF8E1EA,0xFF4B5563 to 0xFFE7E9EC)
    val night=listOf(0xFF5CC8D0 to 0xFF173A3D,0xFFF0A35A to 0xFF3D2A14,0xFFB39DF5 to 0xFF2C2547,0xFF7FCF83 to 0xFF1E3520,0xFFF08DB4 to 0xFF40192A,0xFFAEB6C2 to 0xFF2A2E35)
    val (fg,bg)=(if(dark) night else light)[slot.mod(light.size)]
    return Color(fg) to Color(bg)
}
/** Avatar text: the first character of a name, or the number of 说话人 N. */
fun avatarText(label:String)=label.removePrefix("说话人").trim().take(1).ifBlank { "?" }

@Composable fun DetailContent(modifier:Modifier,state:DetailState,rawTab:Boolean,onTab:(Boolean)->Unit,
    segments:List<SegmentUi>,rows:List<TranscriptRow>,speakers:List<SpeakerOption>,slots:Map<String,Int>,share:List<Pair<SpeakerOption,Float>>,
    filter:String?,onFilter:(String?)->Unit,names:Map<String,String>,playing:Pair<Long,Int>?,follow:Boolean,focus:Focus?,
    refLabel:(EvidenceRef)->String,onSource:(EvidenceRef)->Unit,onAsk:()->Unit,onRetry:()->Unit,
    onPlaySentence:(Long,Int)->Unit,onPlaySegment:(Long)->Unit,onPlayMark:(Long,Long)->Unit,
    onSpeaker:(String,String)->Unit,onTask:(TaskEntity,String)->Unit) {
    val list=rememberLazyListState()
    val policy=remember { FollowPolicy() }
    val byId=remember(segments) { segments.associateBy { it.chunkId } }
    val segmentScoped=remember(segments,speakers) { speakers.isEmpty() && segments.any { s -> s.view.sentences.any { it.speaker!=null && !it.sessionScoped } } }
    val headerItems=2+(if(rawTab && speakers.isNotEmpty()) 1 else 0)+(if(rawTab && segmentScoped) 1 else 0)
    val quote=remember(focus,segments) { focus?.let { f -> byId[f.chunkId]?.let { s -> SegmentPlayback.sentenceForQuote(s.view,s.text,f.start)?.let { f.chunkId to it } } } }
    var quoteShown by remember { mutableStateOf(false) }
    LaunchedEffect(list.isScrollInProgress) { if(list.isScrollInProgress) policy.onUserScroll(System.currentTimeMillis()) }
    LaunchedEffect(playing,follow,rawTab) { val p=playing ?: return@LaunchedEffect
        if(rawTab && policy.shouldFollow(System.currentTimeMillis(),follow)) { val at=TranscriptLayout.rowOf(rows,p.first,p.second);if(at>=0) list.animateScrollToItem(at+headerItems) } }
    LaunchedEffect(quote,rawTab,rows.size) { val q=quote ?: return@LaunchedEffect
        if(rawTab && !quoteShown && rows.isNotEmpty()) { val at=TranscriptLayout.rowOf(rows,q.first,q.second);if(at>=0) { list.scrollToItem(at+headerItems);quoteShown=true } } }
    LazyColumn(modifier.fillMaxSize(),state=list,contentPadding=PaddingValues(20.dp,8.dp,20.dp,28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(state.loading) { item { LinearProgressIndicator(Modifier.fillMaxWidth()) };return@LazyColumn }
        val session=state.session
        if(session==null) { item { EmptyState("来源记录已删除","已确认的待办保留在待办页。") };return@LazyColumn }
        item { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Meta("${dateLabel(session.startedAt)} ${clock(session.startedAt)} · ${if(session.mode=="meeting") "会议" else "随身"} · ${humanDuration(session.durationMs)}${if(speakers.isNotEmpty()) " · ${speakers.size} 人" else ""}",Modifier.weight(1f,fill=false))
            StatusPill(UiStatus.of(session.captureState,session.processingState)) } }
        item { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            SegmentedTabs(listOf("要点","原话"),if(rawTab) 1 else 0,{ onTab(it==1) },Modifier.weight(1f))
            FilledTonalButton(onClick=onAsk,contentPadding=PaddingValues(horizontal=14.dp),modifier=Modifier.height(44.dp)) {
                Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("问问") } } }
        if(!rawTab) {
            val body=state.topic?.summary ?: state.note?.summary
            if(body!=null) item { Panel { SectionTitle("要点 · AI 生成");Text(body,style=MaterialTheme.typography.bodyLarge)
                if(share.isNotEmpty()) { Spacer(Modifier.height(4.dp));Meta("发言占比");SpeakerShareBar(share,names) } } }
            else item { Panel { Text("原音已保存",style=MaterialTheme.typography.titleMedium);Meta(ProcessingReason.message(session.error) ?: "转写和整理完成后，要点会出现在这里。")
                TextButton(onClick=onRetry) { Text("重试") } } }
            val candidates=state.tasks.count { it.state==TaskState.CANDIDATE }
            if(state.tasks.isNotEmpty()) item { SectionTitle(if(candidates>0) "待办 · $candidates 项待确认" else "待办") }
            items(state.tasks,key={ "task:"+it.id }) { task -> DetailTaskCard(task,refLabel,onSource,onTask) }
            val refs=EvidenceRef.fromJson(state.topic?.sourcesJson ?: state.note?.sourcesJson).distinct()
            if(refs.isNotEmpty()) item { SectionTitle("核对原话") }
            items(refs) { SourceCard(it,onSource,refLabel(it)) }
        } else {
            if(speakers.isNotEmpty()) item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                AppChip(filter==null,{onFilter(null)},label={Text("全部")})
                speakers.forEach { o -> val label=SpeakerNames.label(names,o.key,o.speaker) ?: "说话人 ${o.speaker}"
                    val dot=speakerColors(o.slot).first
                    AppChip(filter==o.key,{onFilter(if(filter==o.key) null else o.key)},label={Text(label)},
                        leadingIcon={ Box(Modifier.size(10.dp).clip(CircleShape).background(dot)) }) }
            } }
            if(segmentScoped) item { Meta("随身录音每 5 分钟分一段，说话人编号只在各自片段内有效：不同片段的「说话人 1」可能不是同一个人。会议录音结束后整场一起转写，编号前后一致。") }
            if(rows.isEmpty()) item { EmptyState("原话还没转写好","原音已经保存。等转写完成，或检查服务配置。",CeerPose.THINKING) }
            items(rows,key={ it.key }) { row -> when(row) {
                is TranscriptRow.Divider -> SegmentDivider(row) { onPlaySegment(row.chunkId) }
                is TranscriptRow.Pending -> Meta("这个片段还没转写好，原音已保存。")
                is TranscriptRow.Mark -> MarkChip(row.atMs) { onPlayMark(row.chunkId,row.atMs) }
                is TranscriptRow.Turn -> {
                    val label=SpeakerNames.label(names,row.speakerKey,row.speaker)
                    val key=row.speakerKey
                    TurnBlock(row,label,key?.let { slots[it] },playing?.takeIf { it.first==row.chunkId }?.second,
                        quote?.takeIf { it.first==row.chunkId }?.second,focus?.takeIf { it.chunkId==row.chunkId },byId[row.chunkId]?.text.orEmpty(),
                        onSpeaker=if(key!=null) { { onSpeaker(key,"说话人 ${row.speaker}") } } else null) { i -> onPlaySentence(row.chunkId,i) }
                }
            } }
            if(segments.any { it.provenance=="LEGACY" }) item { Meta("历史原话可能经过旧版 AI 整理，需要时请回听原音核对。") }
        }
    }
}

/** v4.2: consecutive sentences of one speaker: avatar, name (tap to rename), start time, then the sentences (tap to play). */
@Composable fun TurnBlock(turn:TranscriptRow.Turn,label:String?,slot:Int?,playingIndex:Int?,quotedIndex:Int?,focus:Focus?,fullText:String,
    onSpeaker:(()->Unit)?=null,onPlay:(Int)->Unit) {
    if(label==null) { // no speaker: one row per timed sentence, or the untimed 片段 as plain sentences
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            turn.sentences.forEachIndexed { k,s -> val i=turn.first+k
                SentenceRow(s,turn.timed,i==playingIndex,i==quotedIndex,focus?.takeIf { i==quotedIndex },fullText) { onPlay(i) } }
        }
        return
    }
    val (fg,bg)=speakerColors(slot ?: 0)
    val playingHere=playingIndex!=null && turn.contains(playingIndex)
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(bg),contentAlignment=Alignment.Center) {
            Text(avatarText(label),style=MaterialTheme.typography.labelMedium,color=fg) }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(label,style=MaterialTheme.typography.labelLarge,color=fg,maxLines=1,overflow=TextOverflow.Ellipsis,
                    modifier=if(onSpeaker!=null) Modifier.clickable(onClickLabel="给说话人起名",onClick=onSpeaker) else Modifier)
                if(turn.timed) Text((if(playingHere) "▶ " else "")+SegmentPlayback.timeLabel(turn.startMs),fontSize=12.sp,
                    color=if(playingHere) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            turn.sentences.forEachIndexed { k,s -> val i=turn.first+k
                val playing=i==playingIndex;val quoted=i==quotedIndex
                val tint=when { playing->MaterialTheme.colorScheme.primaryContainer; quoted->MaterialTheme.colorScheme.secondaryContainer; else->Color.Transparent }
                Text(buildAnnotatedString { append(s.text)
                        if(quoted && focus!=null) { val at=fullText.indexOf(s.text);val l=(focus.start-at).coerceIn(0,s.text.length);val r=(focus.end-at).coerceIn(0,s.text.length)
                            if(at>=0 && r>l) addStyle(SpanStyle(fontWeight=FontWeight.SemiBold),l,r) } },
                    style=MaterialTheme.typography.bodyLarge,fontWeight=if(playing) FontWeight.SemiBold else null,
                    modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(tint)
                        .then(if(turn.timed) Modifier.clickable(onClickLabel="从这里回听") { onPlay(i) }
                            .semantics { contentDescription="${SegmentPlayback.timeLabel(s.startMs)}，${s.text.take(12)}…，双击从这里回听" } else Modifier)
                        .padding(horizontal=4.dp,vertical=2.dp))
            }
        }
    }
}

/** 片段 boundary in the continuous 原话; untimed 片段 offer 从头回听. */
@Composable fun SegmentDivider(row:TranscriptRow.Divider,onPlayStart:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(top=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(Modifier.weight(1f))
        Text(row.header,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f))
        if(!row.timed) TextButton(onClick=onPlayStart,contentPadding=PaddingValues(horizontal=8.dp)) {
            Icon(Icons.Default.PlayArrow,null,Modifier.size(16.dp));Text("从头回听",style=MaterialTheme.typography.labelMedium) }
    }
}

/** A capture mark; plays from 5 s before it. */
@Composable fun MarkChip(atMs:Long,onPlay:()->Unit) {
    val c=com.example.shuolesa.theme.LocalRegionColors.current
    Row(Modifier.padding(start=38.dp)) {
        Surface(onClick=onPlay,color=c.warnBg,shape=androidx.compose.foundation.shape.CircleShape) {
            Row(Modifier.heightIn(min=32.dp).padding(horizontal=10.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.Flag,null,Modifier.size(14.dp),tint=c.warnText)
                Text("你标记的重点 · ${SegmentPlayback.timeLabel(atMs)}",style=MaterialTheme.typography.labelMedium,color=c.warnText) }
        }
    }
}

/** 发言占比: one stacked bar + a legend line. */
@Composable fun SpeakerShareBar(share:List<Pair<SpeakerOption,Float>>,names:Map<String,String>) {
    val visible=share.filter { it.second>0f }
    Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
        visible.forEach { (o,f) -> val color=speakerColors(o.slot).first;Box(Modifier.weight(f).fillMaxHeight().background(color)) } }
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        visible.forEach { (o,f) -> val color=speakerColors(o.slot).first
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Text("${SpeakerNames.label(names,o.key,o.speaker) ?: "说话人 ${o.speaker}"} ${(f*100).roundToInt()}%",style=MaterialTheme.typography.labelMedium) } }
    }
}

/** 要点页的待办：候选就地确认或忽略（钉钉 AI 听记式待办卡），已确认的可直接勾完成。 */
@Composable fun DetailTaskCard(task:TaskEntity,refLabel:(EvidenceRef)->String,onSource:(EvidenceRef)->Unit,onTask:(TaskEntity,String)->Unit) {
    val refs=remember(task.sourcesJson) { EvidenceRef.fromJson(task.sourcesJson) }
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            if(task.state==TaskState.ACTIVE || task.state==TaskState.DONE)
                Checkbox(task.state==TaskState.DONE,onCheckedChange={ onTask(task,if(it) TaskState.DONE else TaskState.ACTIVE) })
            Text(task.text,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
        }
        val label=when(task.state) { TaskState.ACTIVE->"已确认";TaskState.DONE->"已完成";TaskState.IGNORED->"已忽略";else->"待确认" }
        Meta(listOfNotNull(label,refs.firstOrNull()?.let(refLabel),task.whenHint?.let { "时间提示：$it" }).joinToString(" · "))
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(task.state==TaskState.CANDIDATE) { Button(onClick={onTask(task,TaskState.ACTIVE)}) { Text("确认待办") }
                TextButton(onClick={onTask(task,TaskState.IGNORED)}) { Text("忽略") } }
            else if(task.state==TaskState.IGNORED) TextButton(onClick={onTask(task,TaskState.CANDIDATE)}) { Text("恢复为候选") }
            Spacer(Modifier.weight(1f))
            refs.firstOrNull()?.let { r -> TextButton(onClick={onSource(r)}) { Text("核对原话") } }
        }
    }
}

/** ⑥-A: 时间列 12sp（48dp 宽）+ 句子；当前句高亮；被引用句用引用底色。⑥-B: 句子不可点。 */
@Composable fun SentenceRow(s:TimedSentence,tappable:Boolean,playing:Boolean,quoted:Boolean,focus:Focus?,fullText:String,
    speaker:String?=null,onSpeaker:(()->Unit)?=null,onTap:()->Unit) {
    val bg=when { playing->MaterialTheme.colorScheme.primaryContainer; quoted->MaterialTheme.colorScheme.secondaryContainer; else->MaterialTheme.colorScheme.background }
    val label=SegmentPlayback.timeLabel(s.startMs)
    Surface(color=bg,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()
        .then(if(tappable) Modifier.clickable(onClickLabel="从这里回听",onClick=onTap).semantics { contentDescription="$label，${s.text.take(12)}…，双击从这里回听" } else Modifier)) {
        Row(Modifier.padding(horizontal=12.dp,vertical=8.dp)) {
            if(tappable) Text((if(playing) "▶" else "")+label,Modifier.width(48.dp),fontSize=12.sp,color=if(playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                if(speaker!=null) Text(speaker,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.tertiary,
                    modifier=(if(onSpeaker!=null) Modifier.clickable(onClickLabel="给说话人起名",onClick=onSpeaker) else Modifier).padding(bottom=2.dp))
                if(quoted) Text("被引用",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                Text(buildAnnotatedString { append(s.text)
                    if(focus!=null) { val at=fullText.indexOf(s.text);val l=(focus.start-at).coerceIn(0,s.text.length);val r=(focus.end-at).coerceIn(0,s.text.length)
                        if(at>=0 && r>l) addStyle(SpanStyle(fontWeight=FontWeight.SemiBold),l,r) } },
                    style=MaterialTheme.typography.bodyLarge,fontWeight=if(playing) FontWeight.SemiBold else null)
            }
        }
    }
}

/**
 * v4.2 底部播放器: 片段 i / N + 倍速 + 跟随; progress; 上一段 · −10 s · 播放 · +10 s · 下一段.
 * Replaces the 片段 chip row: with the continuous 原话 the 片段 is chosen by tapping its text, prev/next only step.
 */
@Composable fun DetailPlayerBar(player:PlaybackState,index:Int,count:Int,startLabel:String,timed:Boolean,follow:Boolean,
    onFollow:(Boolean)->Unit,onSpeed:()->Unit,onPrev:(()->Unit)?,onNext:(()->Unit)?,onToggle:()->Unit,onSeek:(Long)->Unit) {
    Surface(shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp),color=MaterialTheme.colorScheme.surface,shadowElevation=12.dp,
        border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=20.dp,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("片段 ${index+1} / $count · $startLabel",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                if(player.ready) AppChip(player.speed!=1f,onSpeed,label={Text(speedLabel(player.speed))})
                if(timed) AppChip(follow,{onFollow(!follow)},label={Text("跟随")},leadingIcon=if(follow) {{Icon(Icons.Default.Check,null,Modifier.size(16.dp))}} else null)
            }
            when { player.loading->LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical=12.dp))
                !player.ready->Meta(player.error ?: "这个片段没有原音")
                else->{
                    Slider(value=player.position.toFloat().coerceAtMost(player.duration.toFloat()),onValueChange={onSeek(it.toLong())},valueRange=0f..maxOf(1,player.duration).toFloat())
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Meta(duration(player.position));Meta(duration(player.duration)) }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick={onPrev?.invoke()},enabled=onPrev!=null) { Icon(Icons.Default.SkipPrevious,"上一段") }
                        IconButton(onClick={onSeek(player.position-10000)}) { Icon(Icons.Default.Replay10,"后退 10 秒") }
                        FilledIconButton(onClick=onToggle,modifier=Modifier.size(60.dp)) { Icon(if(player.playing) Icons.Default.Pause else Icons.Default.PlayArrow,if(player.playing) "暂停回听" else "回听原音") }
                        IconButton(onClick={onSeek(player.position+10000)}) { Icon(Icons.Default.Forward10,"前进 10 秒") }
                        IconButton(onClick={onNext?.invoke()},enabled=onNext!=null) { Icon(Icons.Default.SkipNext,"下一段") }
                    }
                }
            }
            if(!timed) Meta("这一段没有逐句时间，从片段开头回听")
        }
    }
}
fun speedLabel(speed:Float)=when(speed) { 1f->"1×";1.5f->"1.5×";2f->"2×";else->"${speed}×" }

/// ---------------- ⑤ 录音页：深色舞台，一个主按钮 ----------------
@Composable fun CaptureScreen(state:CaptureState,onBack:()->Unit,onCommand:(String)->Unit,transcribed:Int=0,onStopped:()->Unit={},recent:String?=null) {
    val samples=remember { mutableStateListOf<Float>() }
    LaunchedEffect(state.level,state.durationMs) { if(state.phase==CapturePhase.RECORDING) { samples.add(state.level);if(samples.size>48) samples.removeAt(0) } }
    CaptureContent(state,samples,transcribed,onBack,
        onPauseResume={onCommand(if(state.phase==CapturePhase.PAUSED) AudioCaptureService.ACTION_RESUME else AudioCaptureService.ACTION_PAUSE)},
        onStop={onCommand(AudioCaptureService.ACTION_STOP);onStopped()},
        recent=recent,onMark={onCommand(AudioCaptureService.ACTION_MARK)})
}

/**
 * v5.0: the recording page is always dark, whatever the app theme: a quiet stage to leave running on a table. A jade orb
 * breathes with the voice level; 标记 · 停止 · 暂停 sit in one row with stop in the middle, as on a hardware recorder.
 * v4.2: [recent] = the latest 原话 of this recording (随身 transcribes while recording); [onMark] = 标记刚才这句.
 */
@Composable fun CaptureContent(state:CaptureState,samples:List<Float>,transcribed:Int,onBack:()->Unit,onPauseResume:()->Unit,onStop:()->Unit,
    recent:String?=null,onMark:()->Unit={}) {
    com.example.shuolesa.theme.ShuoLeSaTheme(darkTheme=true) {
        StageSystemBars()
        val brand=com.example.shuolesa.theme.LocalBrandColors.current
        val phase=state.phase;val paused=phase==CapturePhase.PAUSED;val recording=phase==CapturePhase.RECORDING
        val primary=MaterialTheme.colorScheme.primary
        Box(Modifier.fillMaxSize().background(brand.stage)) {
          Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top=4.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onBack) { Icon(Icons.Outlined.KeyboardArrowDown,"收起",tint=brand.onStage) }
                Text("${state.modeLabel}录音",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,color=brand.onStage,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.size(48.dp))
            }
            // The middle is centred and scrolls on short screens, so the buttons below always stay on screen.
            Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
              Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally) {
                val pose=when(phase){CapturePhase.RECORDING->CeerPose.LISTENING;CapturePhase.PAUSED->CeerPose.PAUSED;CapturePhase.ERROR->CeerPose.ERROR;CapturePhase.IDLE->CeerPose.SUCCESS;else->CeerPose.THINKING}
                CaptureOrb(pose,state.level,recording,small=recent!=null || state.marks.isNotEmpty())
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(when { recording->brand.record;paused->brand.onStageMuted;else->Color.Transparent }))
                    Text(when(phase){CapturePhase.STARTING->"准备录音";CapturePhase.RECORDING->"正在听";CapturePhase.PAUSED->"已暂停";CapturePhase.STOPPING->"正在保存";CapturePhase.ERROR->"录音已中断";else->"原音已保存"},
                        style=MaterialTheme.typography.titleMedium,color=brand.onStage) }
                Text(duration(state.durationMs),Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.displayMedium,color=brand.onStage)
                val ink=if(paused) brand.onStageMuted.copy(alpha=0.5f) else primary // 暂停态：波形变灰
                Canvas(Modifier.fillMaxWidth().height(48.dp)) { val step=size.width/48
                    samples.forEachIndexed { index,level -> val amplitude=(level*size.height*3).coerceIn(4f,size.height)
                        val x=size.width-(samples.size-index)*step
                        drawLine(ink,Offset(x,(size.height-amplitude)/2),Offset(x,(size.height+amplitude)/2),3.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round) } }
                Spacer(Modifier.height(12.dp))
                Text("已保存 ${state.savedChunks} 个片段 · 已转写 $transcribed 个",style=MaterialTheme.typography.bodySmall,color=brand.onStageMuted)
                Text("原音保存在手机里，收起后继续录",style=MaterialTheme.typography.bodySmall,color=brand.onStageMuted)
                state.error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(top=12.dp)) }
                recent?.let { text -> Spacer(Modifier.height(16.dp))
                    Surface(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large,color=brand.stageRaised) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text("最近转写",style=MaterialTheme.typography.labelMedium,color=brand.onStageMuted)
                            Text("……$text",style=MaterialTheme.typography.bodyMedium,color=brand.onStage,maxLines=3,overflow=TextOverflow.Ellipsis) } } }
                if(state.marks.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top=12.dp).horizontalScroll(rememberScrollState()),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("已标记 ${state.marks.size} 处",style=MaterialTheme.typography.labelMedium,color=brand.onStageMuted)
                    val c=com.example.shuolesa.theme.LocalRegionColors.current
                    state.marks.takeLast(6).forEach { at -> Surface(color=c.warnBg,shape=CircleShape) {
                        Text(SegmentPlayback.timeLabel(at),Modifier.padding(horizontal=10.dp,vertical=3.dp),style=MaterialTheme.typography.labelMedium,color=c.warnText) } }
                }
              }
            }
            if(state.active) Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=24.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.Top) {
                StageButton(Icons.Outlined.Flag,"标记",phase==CapturePhase.RECORDING || paused,onMark)
                StopButton(phase!=CapturePhase.STOPPING,onStop)
                StageButton(if(paused) Icons.Default.PlayArrow else Icons.Default.Pause,if(paused) "继续" else "暂停",phase==CapturePhase.RECORDING || paused,onPauseResume)
            } else Button(onClick=onBack,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).padding(bottom=8.dp)) { Text("回到记忆") }
          }
        }
    }
}

@Composable private fun CaptureOrb(pose:CeerPose,level:Float,live:Boolean,small:Boolean) {
    val primary=MaterialTheme.colorScheme.primary
    val brand=com.example.shuolesa.theme.LocalBrandColors.current
    val smooth by androidx.compose.animation.core.animateFloatAsState((level*4f).coerceIn(0f,1f),androidx.compose.animation.core.tween(140),label="level")
    val breathe=if(live) {
        val value by androidx.compose.animation.core.rememberInfiniteTransition(label="breathe").animateFloat(0f,1f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2200),androidx.compose.animation.core.RepeatMode.Reverse),label="b")
        value
    } else 0f
    val box=if(small) 168.dp else 232.dp
    Box(Modifier.size(box),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // The widest ring (2.5 × base) is exactly the box, so the glow never clips or reaches the text below.
            val base=this.size.minDimension*0.2f
            if(live) { drawCircle(primary.copy(alpha=0.10f+0.06f*breathe),base*(1.95f+0.45f*smooth+0.1f*breathe));drawCircle(primary.copy(alpha=0.16f),base*(1.7f+0.25f*smooth)) }
            else drawCircle(primary.copy(alpha=0.08f),base*1.8f)
            drawCircle(primary,base*1.5f)
        }
        // v5.1: always the black-ink spirit, on the mint orb; the glow replaces its own ripple
        Ceer(pose,Modifier.size(box*0.66f),ink=brand.stage,paper=brand.onStage,accent=primary,energy=smooth,rings=false,interactive=true)
    }
}
@Composable private fun StageButton(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,enabled:Boolean,onClick:()->Unit) {
    val brand=com.example.shuolesa.theme.LocalBrandColors.current
    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Surface(onClick=onClick,enabled=enabled,shape=CircleShape,color=brand.stageRaised,modifier=Modifier.size(64.dp)) {
            Box(contentAlignment=Alignment.Center) { Icon(icon,label,Modifier.size(28.dp),tint=if(enabled) brand.onStage else brand.onStageMuted.copy(alpha=0.4f)) } }
        Text(label,style=MaterialTheme.typography.labelMedium,color=brand.onStageMuted)
    }
}
@Composable private fun StopButton(enabled:Boolean,onClick:()->Unit) {
    val brand=com.example.shuolesa.theme.LocalBrandColors.current
    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Surface(onClick=onClick,enabled=enabled,shape=CircleShape,color=brand.record,modifier=Modifier.size(84.dp)) {
            Box(contentAlignment=Alignment.Center) { Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(brand.onRecord)) } }
        Text("停止并整理",style=MaterialTheme.typography.labelMedium,color=brand.onStageMuted)
    }
}
/** The stage is dark under any app theme, so the status / navigation bar icons go light while it is on screen. */
@Composable private fun StageSystemBars() {
    val view=androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) {
        var ctx=view.context;var activity:android.app.Activity?=null
        while(ctx is android.content.ContextWrapper) { if(ctx is android.app.Activity) { activity=ctx;break };ctx=ctx.baseContext }
        val controller=activity?.window?.let { androidx.core.view.WindowCompat.getInsetsController(it,view) }
        val status=controller?.isAppearanceLightStatusBars;val navigation=controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars=false;controller?.isAppearanceLightNavigationBars=false
        onDispose { status?.let { controller.isAppearanceLightStatusBars=it };navigation?.let { controller.isAppearanceLightNavigationBars=it } }
    }
}
