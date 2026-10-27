package com.example.shuolesa.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.shuolesa.audio.CapturePhase
import com.example.shuolesa.audio.CaptureState
import com.example.shuolesa.theme.LocalBrandColors
import com.example.shuolesa.ui.duration
import com.example.shuolesa.ui.logic.BadgeLogic

/**
 * v5.0 floating dock: three tabs in one capsule and the record button beside it. While recording the button becomes a
 * red pill with the running time and a pause key, and the tab labels fold away so both fit on a 360 dp screen.
 */
@Composable fun HomeDock(tab:Int,onTab:(Int)->Unit,candidates:Int,capture:CaptureState,onRecord:()->Unit,onPauseResume:()->Unit,modifier:Modifier=Modifier) {
    val tabs=listOf<Pair<String,ImageVector>>("记忆" to Icons.Outlined.GraphicEq,"今日" to Icons.Outlined.Today,"待办" to Icons.Outlined.CheckCircle)
    Row(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=16.dp,vertical=12.dp),
        horizontalArrangement=Arrangement.spacedBy(12.dp,Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically) {
        Surface(shape=CircleShape,color=MaterialTheme.colorScheme.surface,shadowElevation=10.dp,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.padding(6.dp),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                tabs.forEachIndexed { i,(label,icon) -> DockItem(label,icon,tab==i,!capture.active,if(i==2) candidates else 0) { onTab(i) } }
            }
        }
        RecordDock(capture,onRecord,onPauseResume)
    }
}

@Composable private fun DockItem(label:String,icon:ImageVector,selected:Boolean,showLabel:Boolean,badge:Int,onClick:()->Unit) {
    val bg by animateColorAsState(if(selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,label="dock")
    val tint=if(selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.height(48.dp).clip(CircleShape).background(bg).selectable(selected,onClick=onClick,role=Role.Tab).padding(horizontal=14.dp),
        verticalAlignment=Alignment.CenterVertically) {
        if(badge>0) BadgedBox(badge={Badge { Text(BadgeLogic.text(badge).orEmpty()) }}) { Icon(icon,label,tint=tint) } else Icon(icon,label,tint=tint)
        AnimatedVisibility(selected && showLabel,enter=expandHorizontally()+fadeIn(),exit=shrinkHorizontally()+fadeOut()) {
            Text(label,Modifier.padding(start=8.dp),style=MaterialTheme.typography.labelLarge,color=tint)
        }
    }
}

@Composable private fun RecordDock(capture:CaptureState,onRecord:()->Unit,onPauseResume:()->Unit) {
    val brand=LocalBrandColors.current
    if(!capture.active) {
        Surface(onClick=onRecord,shape=CircleShape,color=MaterialTheme.colorScheme.primary,shadowElevation=10.dp,modifier=Modifier.size(60.dp)) {
            Box(contentAlignment=Alignment.Center) { Icon(Icons.Default.Mic,"开始录音",Modifier.size(28.dp),tint=MaterialTheme.colorScheme.onPrimary) }
        }
        return
    }
    val paused=capture.phase==CapturePhase.PAUSED
    val blink by rememberInfiniteTransition(label="rec").animateFloat(1f,0.25f,infiniteRepeatable(tween(900),RepeatMode.Reverse),label="dot")
    Surface(onClick=onRecord,shape=CircleShape,color=brand.record,shadowElevation=10.dp,modifier=Modifier.height(60.dp).semantics { contentDescription="展开录音" }) {
        Row(Modifier.padding(start=18.dp,end=6.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).alpha(if(paused) 0.5f else blink).clip(CircleShape).background(brand.onRecord))
            Text(duration(capture.durationMs),Modifier.padding(start=10.dp,end=6.dp),style=MaterialTheme.typography.titleSmall.copy(fontFeatureSettings="tnum"),color=brand.onRecord)
            IconButton(onClick=onPauseResume) {
                Icon(if(paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,if(paused) "继续录音" else "暂停录音",tint=brand.onRecord) }
        }
    }
}
