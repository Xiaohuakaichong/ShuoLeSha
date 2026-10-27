package com.example.shuolesa.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.shuolesa.data.model.EvidenceRef
import com.example.shuolesa.ui.*
import com.example.shuolesa.ui.logic.*

/*
 * v5.0 「纸与墨」 shared pieces: warm paper background, white cards with a hairline, tonal icon tiles, pill controls.
 * Screens only compose these, so a visual change lives here and in theme/.
 */

/** Sub-page frame: pinned bar that tints once content scrolls under it; back is a round tonal button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Page(title:String,onBack:(()->Unit)?=null,actions:@Composable RowScope.()->Unit={},bottom:@Composable ()->Unit={},content:@Composable (PaddingValues)->Unit) {
    val behavior=TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(Modifier.nestedScroll(behavior.nestedScrollConnection),containerColor=MaterialTheme.colorScheme.background,topBar={ TopAppBar(
        title={Text(title,maxLines=1,overflow=TextOverflow.Ellipsis)},
        navigationIcon={onBack?.let { RoundIconButton(Icons.AutoMirrored.Outlined.ArrowBack,"返回",it,Modifier.padding(start=8.dp)) }},
        actions=actions,scrollBehavior=behavior,
        colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background,
            scrolledContainerColor=MaterialTheme.colorScheme.surfaceContainerLow)) },bottomBar=bottom,content=content)
}
/** Round tonal icon button: back, import, settings. */
@Composable fun RoundIconButton(icon:ImageVector,description:String,onClick:()->Unit,modifier:Modifier=Modifier,size:Dp=40.dp) {
    FilledTonalIconButton(onClick,modifier.size(size),colors=IconButtonDefaults.filledTonalIconButtonColors(
        containerColor=MaterialTheme.colorScheme.surfaceContainerHigh,contentColor=MaterialTheme.colorScheme.onSurface)) {
        Icon(icon,description,Modifier.size(size*0.5f)) }
}
/** Big title for the three home tabs. */
@Composable fun LargeHeader(title:String,subtitle:String?=null,modifier:Modifier=Modifier,actions:@Composable RowScope.()->Unit={}) {
    Row(modifier.fillMaxWidth().padding(start=20.dp,end=16.dp,top=12.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title,style=MaterialTheme.typography.headlineMedium)
            subtitle?.let { Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically,content=actions)
    }
}
@Composable fun Panel(modifier:Modifier=Modifier,onClick:(()->Unit)?=null,color:Color=MaterialTheme.colorScheme.surface,padding:Dp=20.dp,content:@Composable ColumnScope.()->Unit) {
    val inner:@Composable ()->Unit={ Column(Modifier.padding(padding),verticalArrangement=Arrangement.spacedBy(12.dp),content=content) }
    val border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
    if(onClick!=null) Surface(onClick=onClick,modifier=modifier,shape=MaterialTheme.shapes.large,color=color,border=border,content=inner)
    else Surface(modifier=modifier,shape=MaterialTheme.shapes.large,color=color,border=border,content=inner)
}
/** Settings-style card: rows run edge to edge, separated by [GroupDivider]. */
@Composable fun GroupCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) { Column(content=content) }
}
@Composable fun GroupDivider() { HorizontalDivider(Modifier.padding(start=72.dp),color=MaterialTheme.colorScheme.outlineVariant) }
/** Rounded-square tile that holds an icon; the app's one way to show a leading icon. */
@Composable fun IconTile(icon:ImageVector,modifier:Modifier=Modifier,container:Color=MaterialTheme.colorScheme.primaryContainer,
    tint:Color=MaterialTheme.colorScheme.onPrimaryContainer,size:Dp=40.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(size*0.32f)).background(container),contentAlignment=Alignment.Center) {
        Icon(icon,null,Modifier.size(size*0.55f),tint=tint) }
}
@Composable fun EmptyState(title:String,description:String,pose:CeerPose=CeerPose.SLEEP,action:String?=null,onAction:()->Unit={}) {
    Column(Modifier.fillMaxWidth().padding(horizontal=32.dp,vertical=40.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Ceer(pose,Modifier.size(120.dp),interactive=true)
        Text(title,style=MaterialTheme.typography.titleMedium)
        Text(description,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        action?.let { FilledTonalButton(onClick=onAction) { Text(it) } }
    }
}
@Composable fun Meta(text:String,modifier:Modifier=Modifier) { Text(text,modifier,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable fun SectionTitle(text:String,action:String?=null,onAction:()->Unit={}) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(text,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
        action?.let { TextButton(onClick=onAction) { Text(it) } }
    }
}
/** Quote card: the sentence itself, with a jade rule on the left like a pull quote. */
@Composable fun SourceCard(ref:EvidenceRef,onOpen:(EvidenceRef)->Unit,label:String="原话来源") {
    Surface(onClick={onOpen(ref)},modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary.copy(alpha=0.65f)))
            Column(Modifier.weight(1f).padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { Meta(label,Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight,"核对原话",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("“${ref.quote}”",style=MaterialTheme.typography.bodyMedium,maxLines=4,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}
/** v4.1 ②/⑧: card = mode tile · time · duration + status pill; 失败 cards get a single-item 重试. */
@Composable fun MemoryCardView(card:MemoryCard,onRetry:(()->Unit)?=null,onOpen:()->Unit) {
    val status=UiStatus.of(card.status,card.status)
    val meeting=card.mode=="meeting"
    val scheme=MaterialTheme.colorScheme
    Panel(Modifier.fillMaxWidth(),onOpen,padding=16.dp) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            IconTile(if(meeting) Icons.Outlined.Groups else Icons.Outlined.GraphicEq,size=36.dp,
                container=if(meeting) scheme.tertiaryContainer else scheme.primaryContainer,tint=if(meeting) scheme.onTertiaryContainer else scheme.onPrimaryContainer)
            Column(Modifier.weight(1f)) {
                Text("${clock(card.time)} · ${if(meeting) "会议" else "随身"}",style=MaterialTheme.typography.labelLarge)
                Meta(duration(card.duration))
            }
            StatusPill(status)
        }
        Text(card.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
        // v4.2: cards still being processed show how many 片段 already have 原话 (转写 7/9 段).
        val inProgress=card.total>0 && card.transcribed<card.total &&
            status in listOf(UiStatus.PROCESSING,UiStatus.WAITING_NETWORK,UiStatus.WAITING_CONFIG)
        if(card.summary.isNotBlank()) Text(card.summary,style=MaterialTheme.typography.bodyMedium,maxLines=3,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
        else if(inProgress) Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            LinearProgressIndicator(progress={ card.transcribed.toFloat()/card.total },modifier=Modifier.fillMaxWidth().height(4.dp),
                color=scheme.primary,trackColor=scheme.primaryContainer,strokeCap=androidx.compose.ui.graphics.StrokeCap.Round)
            Meta("转写 ${card.transcribed}/${card.total} 段 · 全部转好后整理")
        }
        else Meta(when {status==UiStatus.EMPTY || (status==UiStatus.FAILED && card.duration==0L)->"没有录到可用音频，可重新录音"
            status==UiStatus.RECORDING->"保存好的片段会逐步转写，结束后统一整理"
            status==UiStatus.FAILED->"处理失败，原音已保存"
            else->"原音已保存，转写完成后可搜索原话"})
        if(card.candidates>0 || card.legacy || (status==UiStatus.FAILED && onRetry!=null)) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(card.candidates>0) Surface(color=scheme.tertiaryContainer,shape=CircleShape) {
                Text("${card.candidates} 项待确认",Modifier.padding(horizontal=10.dp,vertical=3.dp),style=MaterialTheme.typography.labelMedium,color=scheme.onTertiaryContainer) }
            if(card.legacy) Meta("历史原话（旧版整理）")
            Spacer(Modifier.weight(1f))
            if(status==UiStatus.FAILED && onRetry!=null) FilledTonalButton(onClick=onRetry,contentPadding=PaddingValues(horizontal=16.dp),modifier=Modifier.height(36.dp)) { Text("重试") }
        }
    }
}

/** ⑧ 胶囊：处理中/已整理 = 蓝/绿，等网络/等配置 = 黄，失败 = 红。带一个状态点，12sp。 */
@Composable fun StatusPill(status:UiStatus) {
    val c=com.example.shuolesa.theme.LocalRegionColors.current
    val (bg,fg)=when(status) {
        UiStatus.DONE->c.okBg to c.okText
        UiStatus.PROCESSING,UiStatus.RECORDING->c.runBg to c.runText
        UiStatus.WAITING_NETWORK,UiStatus.WAITING_CONFIG->c.warnBg to c.warnText
        UiStatus.FAILED,UiStatus.EMPTY->MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(color=bg,shape=CircleShape) {
        Row(Modifier.padding(horizontal=10.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
            Text(status.label,style=MaterialTheme.typography.labelMedium,color=fg)
        }
    }
}

/** Status banner: never dismissible (v4.1 decision); it disappears once BannerLogic.pick no longer returns it. */
@Composable fun HomeBanner(banner:Banner,onAction:()->Unit,onRetryAll:(()->Unit)?=null) {
    val c=com.example.shuolesa.theme.LocalRegionColors.current
    val (bg,fg)=when(banner.kind) {
        BannerKind.FAILED->MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        BannerKind.CONSENT->c.runBg to c.runText
        else->c.warnBg to c.warnText
    }
    Surface(Modifier.fillMaxWidth(),color=bg,shape=MaterialTheme.shapes.large) {
        Column(Modifier.padding(start=18.dp,end=8.dp,top=14.dp,bottom=6.dp)) {
            Text(banner.tag,style=MaterialTheme.typography.labelMedium,color=fg.copy(alpha=0.8f))
            Text(banner.title,style=MaterialTheme.typography.titleSmall,color=fg,modifier=Modifier.padding(top=2.dp))
            Text(banner.body,style=MaterialTheme.typography.bodySmall,color=fg,modifier=Modifier.padding(top=2.dp,end=10.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                if(onRetryAll!=null && banner.count>1) TextButton(onClick=onRetryAll) { Text("全部重试",color=fg) }
                TextButton(onClick=onAction) { Text(banner.actionLabel,color=fg,fontWeight=FontWeight.SemiBold) }
            }
        }
    }
}
@Composable fun ActionRow(title:String,subtitle:String?=null,icon:ImageVector?=null,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).clickable(onClick=onClick).padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        icon?.let { IconTile(it,Modifier.padding(end=16.dp)) }
        Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.bodyLarge);subtitle?.let { Meta(it) } }
        Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
/** A filter / choice chip: a pill that turns jade-tinted when selected. */
@Composable fun AppChip(selected:Boolean,onClick:()->Unit,label:@Composable ()->Unit,leadingIcon:(@Composable ()->Unit)?=null,trailingIcon:(@Composable ()->Unit)?=null) {
    val scheme=MaterialTheme.colorScheme
    FilterChip(selected,onClick,label,shape=CircleShape,leadingIcon=leadingIcon,trailingIcon=trailingIcon,
        colors=FilterChipDefaults.filterChipColors(containerColor=scheme.surface,labelColor=scheme.onSurfaceVariant,iconColor=scheme.onSurfaceVariant,
            selectedContainerColor=scheme.primaryContainer,selectedLabelColor=scheme.onPrimaryContainer,selectedLeadingIconColor=scheme.onPrimaryContainer,
            selectedTrailingIconColor=scheme.onPrimaryContainer),
        border=FilterChipDefaults.filterChipBorder(true,selected,borderColor=scheme.outlineVariant,selectedBorderColor=Color.Transparent))
}
/** Two or three tabs in one pill; the selected one lifts onto a white chip. */
@Composable fun SegmentedTabs(options:List<String>,selected:Int,onSelect:(Int)->Unit,modifier:Modifier=Modifier) {
    Surface(modifier,shape=CircleShape,color=MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(4.dp)) {
            options.forEachIndexed { i,label -> val on=i==selected
                val bg by animateColorAsState(if(on) MaterialTheme.colorScheme.surface else Color.Transparent,label="tab")
                Box(Modifier.weight(1f).heightIn(min=36.dp).clip(CircleShape).background(bg).selectable(on,onClick={onSelect(i)},role=Role.Tab),contentAlignment=Alignment.Center) {
                    Text(label,style=MaterialTheme.typography.labelLarge,color=if(on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
/** A selectable row: radio on the left, label (+ optional hint) on the right. */
@Composable fun OptionRow(label:String,selected:Boolean,hint:String?=null,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clip(MaterialTheme.shapes.medium).selectable(selected,onClick=onClick,role=Role.RadioButton)
        .padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
        RadioButton(selected,onClick=null,Modifier.padding(horizontal=12.dp))
        Column(Modifier.weight(1f).padding(vertical=8.dp)) { Text(label,style=MaterialTheme.typography.bodyLarge);hint?.let { Meta(it) } }
    }
}
