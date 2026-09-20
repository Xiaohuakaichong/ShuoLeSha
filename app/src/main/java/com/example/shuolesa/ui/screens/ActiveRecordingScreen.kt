package com.example.shuolesa.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.repository.AudioRepository
import com.example.shuolesa.service.AudioCaptureService
import com.example.shuolesa.theme.AppColor
import com.example.shuolesa.theme.CardElevated
import com.example.shuolesa.theme.DangerRed
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.SurfaceBorder
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.theme.TextSecondary
import com.example.shuolesa.theme.modeColor
import com.example.shuolesa.ui.components.PulsingDot
import com.example.shuolesa.ui.components.TerminalOutlineButton
import com.example.shuolesa.util.Formatters
import kotlinx.coroutines.delay

/**
 * 录音中进度台（v3.2）：大计时 + 模式徽章 + 角色色呼吸点 + 分片/上传/转写状态。
 * 系统返回 /「收起」= 收起 UI（moveTaskToBack），前台服务不停。
 * 状态绑定 liveSessionId 精确匹配已入库分片，不再用路径 contains。
 */
@Composable
fun ActiveRecordingScreen(
    repository: AudioRepository? = null,
    onMinimize: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var chunkIndex by remember { mutableStateOf(1) }
    var sessionId by remember { mutableStateOf("") }
    var previewExpanded by remember { mutableStateOf(false) }

    BackHandler { onMinimize() }

    LaunchedEffect(Unit) {
        while (true) {
            elapsedMs = AudioCaptureService.elapsedMs
            chunkIndex = AudioCaptureService.liveChunkIndex.coerceAtLeast(1)
            sessionId = AudioCaptureService.liveSessionId
            delay(200)
        }
    }

    val sessionRecords by produceState<List<AudioRecordEntity>>(initialValue = emptyList(), sessionId, repository) {
        if (repository == null || sessionId.isBlank()) {
            value = emptyList()
            return@produceState
        }
        // 精确匹配 sessionId（ChunkManager 写入的短 ID），避免 contains 误伤
        repository.observeAllRecords().collect { list ->
            value = list.filter { it.sessionId == sessionId }
                .sortedBy { it.chunkIndex }
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

    val uploadedCount = sessionRecords.count { it.status == AudioRecordEntity.STATUS_UPLOADED }
    val uploadingCount = sessionRecords.count { it.status == AudioRecordEntity.STATUS_UPLOADING }
    val pendingCount = sessionRecords.count { it.status == AudioRecordEntity.STATUS_PENDING }
    val failedCount = sessionRecords.count { it.status == AudioRecordEntity.STATUS_FAILED }
    val latestPreview = sessionRecords
        .asReversed()
        .firstNotNullOfOrNull { rec ->
            rec.transcription?.takeIf { it.isNotBlank() }
                ?: rec.summary?.takeIf { it.isNotBlank() }
        }

    val chunkStatusLine = when {
        sessionRecords.isEmpty() -> "当前第 $chunkIndex 片 · 采集中（满约 5 分钟落盘）"
        else -> "当前第 $chunkIndex 片 · 已入库 ${sessionRecords.size} 片"
    }
    val uploadLine = when {
        sessionRecords.isEmpty() -> "等待首个分片落盘后上传"
        failedCount > 0 -> "失败 $failedCount · 上传中 $uploadingCount · 完成 $uploadedCount / ${sessionRecords.size}"
        uploadingCount > 0 || pendingCount > 0 -> "上传中 $uploadingCount · 排队 $pendingCount · 完成 $uploadedCount / ${sessionRecords.size}"
        uploadedCount == sessionRecords.size -> "已完成已入库分片（$uploadedCount）"
        else -> "排队中"
    }
    val asrLine = when {
        sessionRecords.any { !it.transcription.isNullOrBlank() } ->
            "已有文稿（${sessionRecords.count { !it.transcription.isNullOrBlank() }} 片）"
        uploadingCount > 0 -> "云端处理中…"
        pendingCount > 0 -> "等待上传后 ASR"
        sessionRecords.isNotEmpty() && uploadedCount == sessionRecords.size -> "等待结构化结果"
        else -> "录制中暂无文稿"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColor.background),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "正在录音",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "返回将收起界面，录音在通知栏继续",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.pillRadius))
                        .background(CardElevated)
                        .clickable(onClick = onMinimize)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "收起",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "收起",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.6f))

            PulsingDot(size = 140, color = accent)

            Spacer(modifier = Modifier.height(Dimens.gapLg))

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

            Spacer(modifier = Modifier.height(Dimens.gapLg))

            // 状态区：分片 / 上传 / 转写
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.radiusXl))
                    .background(CardElevated)
                    .border(Dimens.borderThin, SurfaceBorder, RoundedCornerShape(Dimens.radiusXl))
                    .padding(Dimens.cardPaddingVCompact),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProgressLine(label = "采集", value = chunkStatusLine, accent = accent)
                ProgressLine(label = "上传", value = uploadLine, accent = accent)
                ProgressLine(label = "转写", value = asrLine, accent = accent)
            }

            Spacer(modifier = Modifier.height(Dimens.gapSm))

            // 可选文稿预览
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.chipRadius))
                    .clickable { previewExpanded = !previewExpanded }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "文稿预览",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    imageVector = if (previewExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = TextMuted,
                )
            }
            AnimatedVisibility(
                visible = previewExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(Dimens.radiusMd))
                        .background(CardElevated)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = latestPreview?.take(400)
                            ?: "录制中尚无可用文稿。中间分片上传完成后，转写会逐步出现在这里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (latestPreview.isNullOrBlank()) TextMuted else TextPrimary,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "完成后进入处理流程：转写 → 摘要 → 待办",
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

@Composable
private fun ProgressLine(
    label: String,
    value: String,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
