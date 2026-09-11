package com.example.shuolesa.ui.screens

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shuolesa.service.AudioCaptureService
import com.example.shuolesa.theme.BgDark
import com.example.shuolesa.theme.DangerRed
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.MintCyan
import com.example.shuolesa.theme.NeonGreen
import com.example.shuolesa.theme.PureBlack
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.ui.components.PageHeader
import com.example.shuolesa.ui.components.PulsingDot
import com.example.shuolesa.ui.components.TerminalOutlineButton
import com.example.shuolesa.util.Formatters
import kotlinx.coroutines.delay

/**
 * 录音中全屏页。
 */
@Composable
fun ActiveRecordingScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var elapsedMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            elapsedMs = AudioCaptureService.elapsedMs
            delay(200)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = tween(100),
        label = "buttonScale",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .padding(horizontal = Dimens.pagePaddingH, vertical = Dimens.pagePaddingV),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageHeader(
            title = "正在录音",
            subtitle = "环境音频实时采集 · 息屏与后台持续记录",
        )

        Spacer(modifier = Modifier.weight(1f))

        PulsingDot(size = 180)

        Spacer(modifier = Modifier.height(Dimens.gapXl))

        Text(
            text = Formatters.formatElapsed(elapsedMs),
            style = MaterialTheme.typography.displayLarge,
            color = MintCyan,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Dimens.gapMd))

        val isMeeting = AudioCaptureService.currentRecordingMode == "meeting"
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (isMeeting) MintCyan.copy(alpha = 0.15f) else NeonGreen.copy(alpha = 0.15f))
                .border(1.dp, if (isMeeting) MintCyan.copy(alpha = 0.4f) else NeonGreen.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(
                text = AudioCaptureService.currentRecordingModeTitle,
                style = MaterialTheme.typography.labelMedium,
                color = if (isMeeting) MintCyan else NeonGreen,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(Dimens.gapSm))

        Text(
            text = "正在以 ${AudioCaptureService.currentRecordingFormatDesc} 持续采集…",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "停止后将自动分段转写并进行 AI 智能提炼",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Dimens.gapMd))

        TerminalOutlineButton(
            text = "■  完成并停止录音",
            onClick = {
                val intent = Intent(context, AudioCaptureService::class.java).apply {
                    action = AudioCaptureService.ACTION_STOP
                }
                context.startService(intent)
            },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .scale(buttonScale),
            color = DangerRed,
            selected = true,
        )

        Spacer(modifier = Modifier.height(Dimens.gapXl))
    }
}
