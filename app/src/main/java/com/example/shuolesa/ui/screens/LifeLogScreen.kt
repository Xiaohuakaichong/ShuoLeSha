package com.example.shuolesa.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.model.ActionItemModel
import com.example.shuolesa.data.model.LifeLogResult
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.repository.AudioRepository
import com.example.shuolesa.network.ApiService
import com.example.shuolesa.theme.AccentOn
import com.example.shuolesa.theme.BgDark
import com.example.shuolesa.theme.CardElevated
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.ModeCasual
import com.example.shuolesa.theme.ModeDigest
import com.example.shuolesa.theme.ModeMeeting
import com.example.shuolesa.theme.SurfaceBorder
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.theme.TextSecondary
import com.example.shuolesa.ui.components.FilterChip
import com.example.shuolesa.ui.components.PageHeader
import com.example.shuolesa.ui.components.SectionLabel
import com.example.shuolesa.ui.components.TerminalCard
import com.example.shuolesa.ui.components.TimelineItem
import com.example.shuolesa.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 今日复盘：按日汇总录音，生成全天手记。
 */
@Composable
fun LifeLogScreen(
    repository: AudioRepository,
    prefs: AppPreferences,
    onOpenSettings: () -> Unit = {},
    onRecordClick: (AudioRecordEntity) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedDateMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val dateStr = remember(selectedDateMs) { Formatters.formatDateOnly(selectedDateMs) }

    var dayRecords by remember { mutableStateOf<List<AudioRecordEntity>>(emptyList()) }
    var existingLifeLog by remember { mutableStateOf<AudioRecordEntity?>(null) }
    var parsedResult by remember { mutableStateOf<LifeLogResult?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    fun reloadDayData() {
        scope.launch(Dispatchers.IO) {
            val startOfDay = Formatters.getStartOfDay(selectedDateMs)
            val endOfDay = Formatters.getEndOfDay(selectedDateMs)
            val records = repository.getRecordsBetween(startOfDay, endOfDay)
                .filter { !it.isDailyLifeLogSummary() }
            val lifeLogRecord = repository.getRecordBySessionId("lifelog_$dateStr")

            withContext(Dispatchers.Main) {
                dayRecords = records
                existingLifeLog = lifeLogRecord
                parsedResult = if (lifeLogRecord != null && !lifeLogRecord.agentResult.isNullOrBlank()) {
                    LifeLogResult.fromJson(lifeLogRecord.agentResult)
                } else null
            }
        }
    }

    LaunchedEffect(selectedDateMs) {
        reloadDayData()
    }

    val triggerGenerateLifeLog = {
        if (!isGenerating) {
            isGenerating = true
            scope.launch(Dispatchers.IO) {
                try {
                    val baseUrl = prefs.baseUrl.first()
                    val apiKey = prefs.apiKey.first()
                    val llmModel = prefs.llmModel.first()

                    val recordsContext = buildString {
                        dayRecords.sortedBy { it.createdAt }.forEachIndexed { idx, r ->
                            val time = Formatters.formatTimeOnly(r.createdAt)
                            val title = r.title ?: "片段 ${idx + 1}"
                            val dur = Formatters.formatDuration(r.durationMs)
                            appendLine("【片段 ${idx + 1}】时间：$time | 时长：$dur | 标题：$title")
                            if (!r.summary.isNullOrBlank()) {
                                appendLine("- AI要点：${r.summary}")
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
                    val endOfDay = Formatters.getEndOfDay(selectedDateMs)
                    val cleanTitle = result.title.removePrefix("🌿 ").trim()

                    if (existingLifeLog != null) {
                        repository.markProcessedStructured(
                            id = existingLifeLog!!.id,
                            title = cleanTitle,
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
                            title = cleanTitle,
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
                        Toast.makeText(context, "$dateStr 复盘已生成", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "生成失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isGenerating = false
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.pagePaddingH, vertical = Dimens.pagePaddingV)
            .verticalScroll(rememberScrollState()),
    ) {
        PageHeader(
            title = "今日",
            subtitle = "全天复盘",
            onSettings = onOpenSettings,
            trailing = {
                if (parsedResult != null) {
                    IconButton(
                        onClick = {
                            val md = parsedResult!!.toMarkdown(dateStr)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("今日复盘", md))
                            Toast.makeText(context, "已复制 Markdown", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制 Markdown",
                            tint = ModeDigest,
                        )
                    }
                }
            },
        )

        Spacer(modifier = Modifier.height(Dimens.gapSm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dates = listOf(
                "今天" to System.currentTimeMillis(),
                "昨天" to System.currentTimeMillis() - 24 * 60 * 60 * 1000L,
                "前天" to System.currentTimeMillis() - 48 * 60 * 60 * 1000L,
            )
            dates.forEach { (label, timeMs) ->
                val isSelected = Formatters.formatDateOnly(selectedDateMs) == Formatters.formatDateOnly(timeMs)
                FilterChip(
                    label = label,
                    selected = isSelected,
                    onClick = { if (!isGenerating) selectedDateMs = timeMs },
                    accent = ModeDigest,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelSmall,
                color = ModeDigest,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(Dimens.gapMd))

        TerminalCard(borderColor = if (parsedResult != null) ModeDigest.copy(alpha = 0.35f) else SurfaceBorder) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (parsedResult != null) "复盘已生成" else "已记录 ${dayRecords.size} 段",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (dayRecords.isEmpty()) {
                            "这一天还没有录音"
                        } else {
                            "合计 ${Formatters.formatDuration(dayRecords.sumOf { it.durationMs })}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
                if (dayRecords.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.fieldRadius))
                            .background(if (isGenerating) CardElevated else ModeDigest)
                            .clickable(enabled = !isGenerating, onClick = triggerGenerateLifeLog)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.gapXs),
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = ModeDigest,
                                    strokeWidth = 2.dp,
                                )
                            }
                            Text(
                                text = when {
                                    isGenerating -> "提炼中"
                                    parsedResult != null -> "重新生成"
                                    else -> "生成复盘"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isGenerating) TextSecondary else AccentOn,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.gapMd))

        if (dayRecords.isNotEmpty()) {
            SectionLabel("当天片段 (${dayRecords.size})")
            Spacer(modifier = Modifier.height(Dimens.gapSm))
            dayRecords.sortedBy { it.createdAt }.forEach { record ->
                TimelineItem(
                    record = record,
                    onClick = { onRecordClick(record) },
                )
                Spacer(modifier = Modifier.height(Dimens.listGap))
            }
        }

        val dayTasks = remember(dayRecords) {
            dayRecords.sortedByDescending { it.createdAt }.flatMap { r ->
                val items = ActionItemModel.fromJsonString(r.actionItems)
                items.mapIndexed { idx, item ->
                    TaskEntry(
                        recordId = r.id,
                        recordTitle = r.title?.ifBlank { null } ?: if (r.isMeeting()) "会议录音" else "随身记录",
                        recordDate = r.createdAt,
                        isLifeLog = false,
                        isMeeting = r.isMeeting(),
                        itemIndex = idx,
                        item = item,
                        allItemsInRecord = items,
                    )
                }
            }
        }

        val result = parsedResult
        if (result != null) {
            TerminalCard(
                borderColor = ModeDigest.copy(alpha = 0.3f),
                backgroundColor = ModeDigest.copy(alpha = 0.08f),
            ) {
                Text(
                    text = result.title.removePrefix("🌿 ").trim(),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                if (!result.dailyQuote.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Dimens.gapSm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm)) {
                        Icon(
                            imageVector = Icons.Outlined.FormatQuote,
                            contentDescription = null,
                            tint = ModeDigest,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = result.dailyQuote,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ModeDigest,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
            }

            if (result.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(Dimens.gapMd))
                DigestSection(
                    title = "综述",
                    icon = Icons.Outlined.AutoAwesome,
                    accent = ModeDigest,
                    body = result.summary,
                )
            }
            if (!result.socialAndChats.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Dimens.gapMd))
                DigestSection(
                    title = "闲聊与社交",
                    icon = Icons.Outlined.ChatBubbleOutline,
                    accent = ModeCasual,
                    body = result.socialAndChats,
                )
            }
            if (!result.workAndDecisions.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Dimens.gapMd))
                DigestSection(
                    title = "工作与决议",
                    icon = Icons.Outlined.WorkOutline,
                    accent = ModeMeeting,
                    body = result.workAndDecisions,
                )
            }
            if (result.timelineHighlights.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimens.gapMd))
                TerminalCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Timeline,
                            contentDescription = null,
                            tint = ModeDigest,
                            modifier = Modifier.size(16.dp),
                        )
                        SectionLabel("关键时刻")
                    }
                    Spacer(modifier = Modifier.height(Dimens.gapSm))
                    result.timelineHighlights.forEach { item ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Dimens.chipRadius))
                                    .background(CardElevated)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = item.timePeriod,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ModeDigest,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                text = if (item.title.isNotBlank()) "${item.title}：${item.content}" else item.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        } else {
            TerminalCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.gapLg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = ModeDigest.copy(alpha = 0.7f),
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        text = if (dayRecords.isEmpty()) "这一天还没有声音" else "已有 ${dayRecords.size} 段记录",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (dayRecords.isEmpty()) {
                            "录完一天后，可以在这里生成复盘。"
                        } else {
                            "把当天的闲聊、会议和待办收成一篇手记。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (dayTasks.isNotEmpty()) {
            val doneCount = dayTasks.count { it.item.isDone }
            val totalCount = dayTasks.size
            Spacer(modifier = Modifier.height(Dimens.gapMd))
            TerminalCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("当日待办 ($doneCount/$totalCount)")
                    IconButton(
                        onClick = {
                            val text = dayTasks.filter { !it.item.isDone }.joinToString("\n") {
                                "- [ ] ${it.item.text}"
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("今日待办", text))
                            Toast.makeText(context, "已复制待办", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制待办",
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.gapSm))
                dayTasks.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.chipRadius))
                            .clickable {
                                val updatedItems = entry.allItemsInRecord.toMutableList()
                                if (entry.itemIndex in updatedItems.indices) {
                                    val current = updatedItems[entry.itemIndex]
                                    updatedItems[entry.itemIndex] = current.copy(isDone = !current.isDone)
                                    scope.launch(Dispatchers.IO) {
                                        repository.updateActionItems(
                                            entry.recordId,
                                            ActionItemModel.toJsonString(updatedItems),
                                        )
                                        reloadDayData()
                                    }
                                }
                            }
                            .padding(vertical = 5.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                    ) {
                        Icon(
                            imageVector = if (entry.item.isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (entry.item.isDone) ModeDigest else TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = entry.item.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (entry.item.isDone) TextMuted else TextPrimary,
                            textDecoration = if (entry.item.isDone) TextDecoration.LineThrough else TextDecoration.None,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.gapXl))
    }
}

@Composable
private fun DigestSection(
    title: String,
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    body: String,
) {
    TerminalCard(borderColor = accent.copy(alpha = 0.28f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
            SectionLabel(title, color = accent)
        }
        Spacer(modifier = Modifier.height(Dimens.gapSm))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
        )
    }
}
