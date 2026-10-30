package com.example.shuolesa.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.shuolesa.network.ProcessingReason
import com.example.shuolesa.ui.clock
import com.example.shuolesa.ui.duration
import com.example.shuolesa.ui.logic.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * v5.2 处理进度: headline + percentage, one row per step (解码 · 转写 · 分段整理 · 汇总), how long the current step has
 * been running, what to do when it is stuck, and an optional per-片段 list.
 */
@Composable fun ProcessingCard(p:ProcessingProgress,modifier:Modifier=Modifier,onRetry:()->Unit={},onFixService:()->Unit={},onConsent:()->Unit={}) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val now by produceState(System.currentTimeMillis(),p.runningSince) {
        while(p.runningSince!=null) { value=System.currentTimeMillis();delay(1_000) }
    }
    val fraction by animateFloatAsState(p.fraction,label="progress")
    val scheme=MaterialTheme.colorScheme
    Panel(modifier.fillMaxWidth().animateContentSize()) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            val pose=when { p.failed->CeerPose.ERROR;p.reason!=null->CeerPose.PAUSED;p.recording->CeerPose.LISTENING;p.runningSince!=null->CeerPose.THINKING;else->CeerPose.IDLE }
            Ceer(pose,Modifier.size(52.dp))
            Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                Text(p.headline,style=MaterialTheme.typography.titleSmall)
                val since=p.runningSince?.let { "已进行 ${duration((now-it).coerceAtLeast(0))}" }
                (p.detail ?: since)?.let { Meta(it) }
            }
            Text("${(p.fraction*100).roundToInt()}%",style=MaterialTheme.typography.titleMedium,color=if(p.reason!=null) scheme.onSurfaceVariant else scheme.primary)
        }
        LinearProgressIndicator(progress={ fraction },modifier=Modifier.fillMaxWidth().height(6.dp),
            color=if(p.failed) scheme.error else if(p.reason!=null) scheme.tertiary else scheme.primary,
            trackColor=scheme.surfaceContainerHigh,strokeCap=StrokeCap.Round,gapSize=0.dp,drawStopIndicator={})
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { p.steps.forEach { StepRow(it) } }
        when {
            p.reason==ProcessingReason.AWAITING_CONSENT -> Button(onClick=onConsent) { Text("去确认数据外发") }
            ProcessingReason.needsServiceFix(p.reason) -> Button(onClick=onFixService) { Text("去设置转写服务") }
            p.failed -> FilledTonalButton(onClick=onRetry) { Text("重试") }
            p.reason==ProcessingReason.NETWORK || p.reason==ProcessingReason.RATE_LIMITED -> Meta("联网后会自动继续，原音都在手机里")
        }
        if(p.segments.size>1) {
            TextButton(onClick={ expanded=!expanded },contentPadding=PaddingValues(0.dp)) {
                Text(if(expanded) "收起逐段进度" else "逐段进度 · ${p.segments.size} 段")
                Icon(if(expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null,Modifier.size(18.dp))
            }
            if(expanded) Column(verticalArrangement=Arrangement.spacedBy(6.dp)) { p.segments.forEach { SegmentRow(it) } }
        }
    }
}

@Composable private fun StepRow(step:ProgressStep) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        StateIcon(step.state,18.dp)
        Text(step.title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,
            color=if(step.state==StepState.WAITING) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        Meta(if(step.total>1) "${step.done}/${step.total}" else stateLabel(step.state))
    }
}

@Composable private fun SegmentRow(s:SegmentProgress) {
    val span=if(s.imported) "${duration(s.start)}–${duration(s.end)}" else "${clock(s.start)}–${clock(s.end)}"
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("片段 ${s.index+1}",style=MaterialTheme.typography.labelLarge)
        Meta(span,Modifier.weight(1f))
        MiniState("转写",s.transcribe);MiniState("整理",s.organise)
    }
}

@Composable private fun MiniState(label:String,state:StepState) {
    val scheme=MaterialTheme.colorScheme
    val (bg,fg)=when(state) {
        StepState.DONE->scheme.primaryContainer to scheme.onPrimaryContainer
        StepState.RUNNING->scheme.secondaryContainer to scheme.onSecondaryContainer
        StepState.BLOCKED->scheme.tertiaryContainer to scheme.onTertiaryContainer
        StepState.FAILED->scheme.errorContainer to scheme.onErrorContainer
        StepState.WAITING->scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
    Surface(color=bg,shape=CircleShape) {
        Row(Modifier.padding(horizontal=8.dp,vertical=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            StateIcon(state,12.dp,fg);Text(label,style=MaterialTheme.typography.labelSmall,color=fg)
        }
    }
}

@Composable private fun StateIcon(state:StepState,size:androidx.compose.ui.unit.Dp,tint:Color?=null) {
    val scheme=MaterialTheme.colorScheme
    when(state) {
        StepState.RUNNING -> CircularProgressIndicator(Modifier.size(size),strokeWidth=2.dp,color=tint ?: scheme.primary)
        StepState.DONE -> Icon(Icons.Outlined.CheckCircle,null,Modifier.size(size),tint=tint ?: scheme.primary)
        StepState.BLOCKED -> Icon(Icons.Outlined.PauseCircle,null,Modifier.size(size),tint=tint ?: scheme.tertiary)
        StepState.FAILED -> Icon(Icons.Outlined.ErrorOutline,null,Modifier.size(size),tint=tint ?: scheme.error)
        StepState.WAITING -> Icon(Icons.Outlined.RadioButtonUnchecked,null,Modifier.size(size),tint=tint ?: scheme.outline)
    }
}

fun stateLabel(state:StepState)=when(state) { StepState.DONE->"完成";StepState.RUNNING->"进行中";StepState.BLOCKED->"已暂停";StepState.FAILED->"失败";StepState.WAITING->"等待" }
