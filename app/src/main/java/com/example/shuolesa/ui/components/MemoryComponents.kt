package com.example.shuolesa.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.shuolesa.data.model.EvidenceRef
import com.example.shuolesa.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Page(title:String,onBack:(()->Unit)?=null,actions:@Composable RowScope.()->Unit={},bottom:@Composable ()->Unit={},content:@Composable (PaddingValues)->Unit) {
    Scaffold(containerColor=MaterialTheme.colorScheme.background,topBar={ TopAppBar(title={Text(title)},
        navigationIcon={onBack?.let { IconButton(onClick=it) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"返回") } }},
        actions=actions,colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background)) },bottomBar=bottom,content=content)
}
@Composable fun Panel(modifier:Modifier=Modifier,onClick:(()->Unit)?=null,content:@Composable ColumnScope.()->Unit) {
    val shape=RoundedCornerShape(20.dp)
    Surface(modifier=modifier.then(if(onClick!=null) Modifier.clickable(onClick=onClick) else Modifier),shape=shape,
        color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
@Composable fun EmptyState(title:String,description:String,pose:CeerPose=CeerPose.SLEEP,action:String?=null,onAction:()->Unit={}) {
    Column(Modifier.fillMaxWidth().padding(horizontal=32.dp,vertical=44.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Ceer(pose,Modifier.size(72.dp));Text(title,style=MaterialTheme.typography.titleMedium)
        Text(description,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        action?.let { TextButton(onClick=onAction) { Text(it) } }
    }
}
@Composable fun Meta(text:String,modifier:Modifier=Modifier) { Text(text,modifier,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable fun SectionTitle(text:String,action:String?=null,onAction:()->Unit={}) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(text,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
        action?.let { TextButton(onClick=onAction) { Text(it) } }
    }
}
@Composable fun SourceCard(ref:EvidenceRef,onOpen:(EvidenceRef)->Unit,label:String="原稿来源") {
    Panel(Modifier.fillMaxWidth(),{onOpen(ref)}) {
        Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.Link,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp));Meta(label);Spacer(Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,"查看原稿",Modifier.size(20.dp)) }
        Text("“${ref.quote}”",style=MaterialTheme.typography.bodyMedium,maxLines=4,overflow=TextOverflow.Ellipsis)
    }
}
@Composable fun MemoryCardView(card:MemoryCard,onOpen:()->Unit) {
    Panel(Modifier.fillMaxWidth(),onOpen) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Meta("${clock(card.time)} · ${if(card.mode=="meeting") "会议" else "随身"} · ${duration(card.duration)}",Modifier.weight(1f))
            if(card.status!="READY") Meta(statusLabel(card.status))
        }
        Text(card.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
        if(card.summary.isNotBlank()) Text(card.summary,style=MaterialTheme.typography.bodyMedium,maxLines=3,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
        else Meta(when {card.status=="EMPTY" || (card.status=="FAILED" && card.duration==0L)->"没有录到可用音频，可重新录音"
            card.status in listOf("STARTING","RECORDING","PAUSED","STOPPING")->"封存的片段会逐步转写，结束后统一整理"
            else->"原音已保存，转写完成后可搜索原话"})
        if(card.candidates>0 || card.legacy) Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            if(card.candidates>0) Text("${card.candidates} 项候选待办",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            if(card.legacy) Meta("历史整理稿")
        }
    }
}
@Composable fun ActionRow(title:String,subtitle:String?=null,icon:ImageVector?=null,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).clickable(onClick=onClick).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        icon?.let { Icon(it,null,Modifier.padding(end=16.dp).size(24.dp)) }
        Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.bodyLarge);subtitle?.let { Meta(it) } }
        Icon(Icons.Outlined.ChevronRight,null)
    }
}
