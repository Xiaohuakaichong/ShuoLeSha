package com.example.shuolesa.ui.screens

import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.shuolesa.service.AudioCaptureService
import com.example.shuolesa.theme.DangerRed
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.modeColor
import com.example.shuolesa.ui.components.PageHeader
import com.example.shuolesa.ui.components.PulsingDot
import com.example.shuolesa.ui.components.TerminalOutlineButton
import com.example.shuolesa.util.Formatters
import kotlinx.coroutines.delay

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
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "buttonScale",
    )

    val isMeeting = AudioCaptureService.currentRecordingMode == "meeting"
    val accent = modeColor(isMeeting)
    val modeLabel = if (isMeeting) "会议" else "随身"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(com.example.shuolesa.theme.AppColor.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = Dimens.pagePaddingH, vertical = Dimens.pagePaddingV),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        PageHeader(
            title = "正在录音",
            subtitle = "息屏与后台都会继续记",
        )

        Spacer(modifier = Modifier.weight(1f))

        PulsingDot(size = 180, color = accent)

        Spacer(modifier = Modifier.height(Dimens.gapXl))

        Text(
            text = Formatters.formatElapsed(elapsedMs),
            style = MaterialTheme.typography.displayLarge,
            color = accent,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Dimens.gapMd))

        Text(
            text = modeLabel,
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.pillRadius))
                .background(accent.copy(alpha = 0.15f))
                .border(Dimens.borderThin, accent.copy(alpha = 0.4f), RoundedCornerShape(Dimens.pillRadius))
                .padding(horizontal = Dimens.gapMd, vertical = Dimens.gapXs),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(Dimens.gapSm))

        Text(
            text = AudioCaptureService.currentRecordingFormatDesc,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "停止后会自动转写并提炼纪要",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Dimens.gapMd))

        TerminalOutlineButton(
            text = "完成并停止",
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
            interactionSource = interactionSource,
        )

        Spacer(modifier = Modifier.height(Dimens.gapXl))
        }
    }
}
