package com.example.shuolesa.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

enum class CeerPose(val label:String) { IDLE("侧耳"),LISTENING("正在听"),THINKING("正在整理"),SPEAKING("正在回听"),PAUSED("已暂停"),SLEEP("暂无记忆"),SUCCESS("已保存"),ERROR("需要处理") }
/** v5.0: the mascot takes the brand colour (jade body, light eyes); callers on a coloured disc pass their own [body] / [eye]. */
@Composable fun Ceer(pose:CeerPose,modifier:Modifier=Modifier,body:Color=MaterialTheme.colorScheme.primary,eye:Color=MaterialTheme.colorScheme.onPrimary) {
    val active=pose in listOf(CeerPose.LISTENING,CeerPose.THINKING,CeerPose.SPEAKING)
    val wave=if(active) {
        val motion=rememberInfiniteTransition(label="ceer")
        val value by motion.animateFloat(0f,1f,infiniteRepeatable(tween(1800),RepeatMode.Reverse),label="eyes")
        value
    } else 0f
    Canvas(modifier.size(48.dp).semantics { contentDescription=pose.label }) {
        scale(size.width/100,size.height/100,pivot=Offset.Zero) {
            val path=Path().apply {
                moveTo(50f,12f);cubicTo(28f,12f,12f,28f,12f,48f);cubicTo(12f,64f,22f,76f,36f,81f)
                lineTo(29f,94f);lineTo(52f,85f);cubicTo(73f,84f,88f,68f,88f,48f);cubicTo(88f,27f,72f,12f,50f,12f);close()
            }
            drawPath(path,body)
            val dx=if(pose==CeerPose.THINKING) (wave-.5f)*5 else 0f
            val height=when(pose) { CeerPose.PAUSED,CeerPose.SLEEP->5f;CeerPose.SUCCESS->11f;else->21f }
            val bob=if(active) wave*1.8f else 0f
            listOf(34f,58f).forEachIndexed { i,x ->
                val h=if(pose==CeerPose.ERROR && i==1) 11f else height
                drawRoundRect(eye,Offset(x+dx,47.5f-h/2+bob),Size(9f,h),CornerRadius(4.5f,4.5f))
            }
        }
    }
}
