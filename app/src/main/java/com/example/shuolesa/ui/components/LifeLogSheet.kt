package com.example.shuolesa.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.model.ActionItemModel
import com.example.shuolesa.data.model.LifeLogResult
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.repository.AudioRepository
import com.example.shuolesa.network.ApiService
import com.example.shuolesa.theme.BgDark
import com.example.shuolesa.theme.CardDark
import com.example.shuolesa.theme.CardElevated
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.ElectricBlue
import com.example.shuolesa.theme.MintCyan
import com.example.shuolesa.theme.NeonGreen
import com.example.shuolesa.theme.PureBlack
import com.example.shuolesa.theme.SurfaceBorder
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.theme.TextSecondary
import com.example.shuolesa.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 现代全天 LifeLog 汇总与生活手记面板：
 * 筛选指定日期录音（闲聊、会议、自语、灵感），调用大模型聚合生成生活手记、闲聊温情记录与全天待办。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeLogSheet(
    repository: AudioRepository,
    prefs: AppPreferences,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedDateMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val dateStr = remember(selectedDateMs) { Formatters.formatDateOnly(selectedDateMs) }
    val isToday = remember(selectedDateMs) {
        Formatters.formatDateOnly(selectedDateMs) == Formatters.formatDateOnly(System.currentTimeMillis())
    }

    var dayRecords by remember { mutableStateOf<List<AudioRecordEntity>>(emptyList()) }
    var existingLifeLog by remember { mutableStateOf<AudioRecordEntity?>(null) }
    var parsedResult by remember { mutableStateOf<LifeLogResult?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // 加载指定日期的录音与 LifeLog
    val reloadDayData = {
        scope.launch(Dispatchers.IO) {
            val startOfDay = Formatters.getStartOfDay(selectedDateMs)
            val endOfDay = Formatters.getEndOfDay(selectedDateMs)
            val records = repository.getRecordsBetween(startOfDay, endOfDay)

            val lifeLogRecord = repository.getRecordBySessionId("lifelog_$dateStr")
                ?: records.find { it.tags.orEmpty().contains("LifeLog") }

            val pureRecords = records.filter { !it.tags.orEmpty().contains("LifeLog") }

            withContext(Dispatchers.Main) {
                dayRecords = pureRecords
                existingLifeLog = lifeLogRecord
                parsedResult = if (lifeLogRecord != null) {
                    val raw = lifeLogRecord.agentResult ?: lifeLogRecord.summary ?: ""
                    LifeLogResult.fromJson(raw).copy(
                        title = lifeLogRecord.title?.removePrefix("🌿 ")?.trim() ?: "全天生活手记",
                        summary = lifeLogRecord.summary ?: "",
                        unifiedActionItems = ActionItemModel.fromJsonString(lifeLogRecord.actionItems),
                    )
                } else null
            }
        }
    }

    LaunchedEffect(selectedDateMs) {
        reloadDayData()
    }

    // 生成 LifeLog 触发器
    val generateLifeLog = {
        if (dayRecords.isEmpty()) {
            Toast.makeText(context, "$dateStr 没有录音记录，无需汇总", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch(Dispatchers.IO) {
                isGenerating = true
                loadError = null
                try {
                    val baseUrl = prefs.getBaseUrlSync()
                    val apiKey = prefs.getApiKeySync()
                    val llmModel = prefs.getLlmModelSync()

                    // 构建全天声音碎片上下文
                    val recordsContext = buildString {
                        dayRecords.forEachIndexed { idx, r ->
                            appendLine("【录音 ${idx + 1}】")
                            appendLine("- 时间：${Formatters.formatListDate(r.createdAt)}")
                            appendLine("- 标题：${r.title ?: "随手录音"}")
                            if (!r.summary.isNullOrBlank()) {
                                appendLine("- 要点摘要：${r.summary}")
                            }
                            if (!r.actionItems.isNullOrBlank() && r.actionItems != "[]") {
                                appendLine("- 待办事项：${r.actionItems}")
                            }
                            if (!r.transcription.isNullOrBlank()) {
                                appendLine("- 文字稿片段：${r.transcription.take(600)}")
                            }
                            appendLine()
                        }
                    }

                    val apiService = ApiService()
                    val res = apiService.generateLifeLogSummary(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        llmModel = llmModel,
                        dateStr = dateStr,
                        recordsContext = recordsContext,
                    )

                    val result = res.getOrThrow()
                    val markdown = result.toMarkdown(dateStr)
                    val totalDuration = dayRecords.sumOf { it.durationMs }

                    val startOfDay = Formatters.getStartOfDay(selectedDateMs)
                    val endOfDay = Formatters.getEndOfDay(selectedDateMs)

                    if (existingLifeLog != null) {
                        repository.markProcessedStructured(
                            id = existingLifeLog!!.id,
                            title = "🌿 " + result.title.removePrefix("🌿 ").trim(),
                            summary = result.summary,
                            actionItems = ActionItemModel.toJsonString(result.unifiedActionItems),
                            tags = "[\"LifeLog\", \"每日复盘\"]",
                            transcription = markdown,
                            agentResult = result.rawJson,
                        )
                    } else {
                        val newRecord = AudioRecordEntity(
                            sessionId = "lifelog_$dateStr",
                            chunkIndex = 0,
                            filePath = "",
                            durationMs = totalDuration,
                            fileSizeBytes = 0L,
                            createdAt = endOfDay.coerceAtMost(System.currentTimeMillis()),
                            status = AudioRecordEntity.STATUS_UPLOADED,
                            title = "🌿 " + result.title.removePrefix("🌿 ").trim(),
                            summary = result.summary,
                            actionItems = ActionItemModel.toJsonString(result.unifiedActionItems),
                            tags = "[\"LifeLog\", \"每日复盘\"]",
                            transcription = markdown,
                            agentResult = result.rawJson,
                        )
                        repository.insertRecord(newRecord)
                    }

                    withContext(Dispatchers.Main) {
                        parsedResult = result
                        reloadDayData()
                        Toast.makeText(context, "🌿 $dateStr LifeLog 已生成！", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        loadError = "生成失败: ${e.message}"
                        Toast.makeText(context, loadError, Toast.LENGTH_LONG).show()
                    }
                } finally {
                    isGenerating = false
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgDark,
        dragHandle = null,
        modifier = modifier.fillMaxHeight(0.92f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.pagePaddingH, vertical = Dimens.gapMd),
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "🌿 全天 LifeLog 复盘",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MintCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MintCyan,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = TextMuted,
                    )
                }
            }

            // Date Switcher Row (今天 / 昨天)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.gapSm),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val yesterdayMs = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                val isYesterday = remember(selectedDateMs) {
                    Formatters.formatDateOnly(selectedDateMs) == Formatters.formatDateOnly(yesterdayMs)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isToday) MintCyan.copy(alpha = 0.2f) else CardDark)
                        .border(1.dp, if (isToday) MintCyan else SurfaceBorder, RoundedCornerShape(8.dp))
                        .clickable { selectedDateMs = System.currentTimeMillis() }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "今天",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isToday) MintCyan else TextSecondary,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isYesterday) MintCyan.copy(alpha = 0.2f) else CardDark)
                        .border(1.dp, if (isYesterday) MintCyan else SurfaceBorder, RoundedCornerShape(8.dp))
                        .clickable { selectedDateMs = yesterdayMs }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "昨天",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isYesterday) MintCyan else TextSecondary,
                        fontWeight = if (isYesterday) FontWeight.Bold else FontWeight.Normal,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (parsedResult != null) {
                    IconButton(
                        onClick = {
                            val md = parsedResult!!.toMarkdown(dateStr)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("LifeLog", md))
                            Toast.makeText(context, "已复制 LifeLog Markdown", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制 Markdown",
                            tint = MintCyan,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Audio pool summary banner
                TerminalCard {
                    val totalMins = dayRecords.sumOf { it.durationMs } / 60000
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "今日声音碎片：${dayRecords.size} 段",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "累计记录 ${totalMins} 分钟 · 涵盖会议、朋友闲聊与随心记",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                            )
                        }

                        if (existingLifeLog != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NeonGreen.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "已复盘",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonGreen,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimens.gapSm))

                    // Action button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(MintCyan, ElectricBlue),
                                ),
                            )
                            .clickable(enabled = !isGenerating && dayRecords.isNotEmpty()) {
                                generateLifeLog()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = PureBlack,
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = "正在全天多维复盘中...",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = PureBlack,
                                    fontWeight = FontWeight.Bold,
                                )
                            } else {
                                Icon(
                                    imageVector = if (existingLifeLog != null) Icons.Default.Refresh else Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = PureBlack,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = if (existingLifeLog != null) "重新生成此日 LifeLog" else "一键生成全天 LifeLog",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = PureBlack,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                if (dayRecords.isEmpty()) {
                    Spacer(modifier = Modifier.height(Dimens.gapXl))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "该日期暂未记录任何声音，快去随手记录生活吧！",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                        )
                    }
                }

                // If LifeLog Result is available
                parsedResult?.let { log ->
                    Spacer(modifier = Modifier.height(Dimens.gapMd))

                    // 1. Title & Daily Quote
                    TerminalCard {
                        Text(
                            text = log.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        if (!log.dailyQuote.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(Dimens.gapSm))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ElectricBlue.copy(alpha = 0.1f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FormatQuote,
                                    contentDescription = null,
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = log.dailyQuote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ElectricBlue,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Dimens.gapSm))
                        Text(
                            text = log.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            lineHeight = 22.sp,
                        )
                    }

                    // 2. Timeline Highlights
                    if (log.timelineHighlights.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Dimens.gapMd))
                        TerminalCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timeline,
                                    contentDescription = null,
                                    tint = MintCyan,
                                    modifier = Modifier.size(16.dp),
                                )
                                SectionLabel("生活轨迹时间轴")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                log.timelineHighlights.forEach { h ->
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MintCyan.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = h.timePeriod,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MintCyan,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            if (h.title.isNotBlank()) {
                                                Text(
                                                    text = h.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = TextPrimary,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            }
                                            Text(
                                                text = h.content,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary,
                                                lineHeight = 18.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Social & Casual Chats (闲聊温情亮点)
                    if (!log.socialAndChats.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(Dimens.gapMd))
                        TerminalCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(16.dp),
                                )
                                SectionLabel("朋友与闲聊温情亮点")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = log.socialAndChats,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                lineHeight = 22.sp,
                            )
                        }
                    }

                    // 4. Work & Decisions (工作决议)
                    if (!log.workAndDecisions.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(Dimens.gapMd))
                        TerminalCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WorkOutline,
                                    contentDescription = null,
                                    tint = NeonGreen,
                                    modifier = Modifier.size(16.dp),
                                )
                                SectionLabel("工作推进与关键决策")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = log.workAndDecisions,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                lineHeight = 22.sp,
                            )
                        }
                    }

                    // 5. Unified Action Items (全天聚合待办)
                    if (log.unifiedActionItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Dimens.gapMd))
                        val completedCount = log.unifiedActionItems.count { it.isDone }
                        TerminalCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SectionLabel("全天聚合待办 ($completedCount/${log.unifiedActionItems.size})")
                                IconButton(
                                    onClick = {
                                        val text = log.unifiedActionItems.joinToString("\n") { (if (it.isDone) "☑ " else "☐ ") + it.text }
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("待办", text))
                                        Toast.makeText(context, "已复制聚合待办清单", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "复制待办",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            log.unifiedActionItems.forEachIndexed { idx, item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val updated = log.unifiedActionItems.mapIndexed { i, itm ->
                                                if (i == idx) itm.copy(isDone = !itm.isDone) else itm
                                            }
                                            parsedResult = log.copy(unifiedActionItems = updated)
                                            existingLifeLog?.let { el ->
                                                scope.launch(Dispatchers.IO) {
                                                    repository.updateActionItems(el.id, ActionItemModel.toJsonString(updated))
                                                }
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = if (item.isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (item.isDone) NeonGreen else MintCyan,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = item.text,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                                        ),
                                        color = if (item.isDone) TextMuted else TextPrimary,
                                        lineHeight = 20.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.gapXl))
            }
        }
    }
}
