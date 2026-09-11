package com.example.shuolesa.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WorkOutline
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
import com.example.shuolesa.ui.components.PageHeader
import com.example.shuolesa.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 独立一级主屏：LifeLog 每日生活手记与全天复盘
 */
@Composable
fun LifeLogScreen(
    repository: AudioRepository,
    prefs: AppPreferences,
    modifier: Modifier = Modifier,
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

    fun reloadDayData() {
        scope.launch(Dispatchers.IO) {
            val startOfDay = Formatters.getStartOfDay(selectedDateMs)
            val endOfDay = Formatters.getEndOfDay(selectedDateMs)
            val records = repository.getRecordsBetween(startOfDay, endOfDay)
                .filter { it.sessionId != "lifelog_$dateStr" && !it.tags.orEmpty().contains("LifeLog") }
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
            .background(BgDark)
            .padding(horizontal = Dimens.pagePaddingH)
            .verticalScroll(rememberScrollState()),
    ) {
        PageHeader(
            title = "生活手记",
            subtitle = "全天生活复盘 · 闲聊温情与决议",
            trailing = {
                if (parsedResult != null) {
                    IconButton(
                        onClick = {
                            val md = parsedResult!!.toMarkdown(dateStr)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("LifeLog", md))
                            Toast.makeText(context, "已复制 LifeLog Markdown", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制 Markdown",
                            tint = MintCyan,
                        )
                    }
                }
            },
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Date selection bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val yesterdayMs = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            val dayBeforeMs = System.currentTimeMillis() - 48 * 60 * 60 * 1000L

            val dates = listOf(
                "今天" to System.currentTimeMillis(),
                "昨天" to yesterdayMs,
                "前天" to dayBeforeMs,
            )

            dates.forEach { (label, timeMs) ->
                val isSelected = Formatters.formatDateOnly(selectedDateMs) == Formatters.formatDateOnly(timeMs)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MintCyan.copy(alpha = 0.2f) else CardDark)
                        .border(1.dp, if (isSelected) MintCyan else SurfaceBorder, RoundedCornerShape(8.dp))
                        .clickable { selectedDateMs = timeMs }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MintCyan else TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MintCyan.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MintCyan,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Day status summary banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardDark)
                .border(1.dp, if (parsedResult != null) NeonGreen.copy(alpha = 0.35f) else SurfaceBorder, RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (parsedResult != null) NeonGreen.copy(alpha = 0.15f) else MintCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (parsedResult != null) NeonGreen else MintCyan,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column {
                        Text(
                            text = if (parsedResult != null) "✨ 今日手记已生成" else "🎙️ 已记录 ${dayRecords.size} 段声音",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (dayRecords.isEmpty()) "该日期暂未记录音频片段" else "总计时长：${Formatters.formatDuration(dayRecords.sumOf { it.durationMs })}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (dayRecords.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isGenerating) CardElevated else NeonGreen)
                            .clickable(enabled = !isGenerating, onClick = triggerGenerateLifeLog)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = MintCyan,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = if (parsedResult != null) Icons.Default.Refresh else Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = PureBlack,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            Text(
                                text = if (isGenerating) "提炼中..." else if (parsedResult != null) "重新复盘" else "生成手记",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isGenerating) TextSecondary else PureBlack,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (parsedResult != null) {
            val result = parsedResult!!

            // Title & Daily Quote Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(NeonGreen.copy(alpha = 0.12f), CardDark)
                        )
                    )
                    .border(1.dp, NeonGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Column {
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!result.dailyQuote.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatQuote,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = result.dailyQuote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NeonGreen,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Summary narrative
            if (result.summary.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDark)
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MintCyan,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "心境与生活综述",
                                style = MaterialTheme.typography.titleSmall,
                                color = MintCyan,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = result.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            lineHeight = 20.sp,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Casual Chats & Social Highlights Card
            if (!result.socialAndChats.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDark)
                        .border(1.dp, ElectricBlue.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    Column {
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
                            Text(
                                text = "👥 闲聊与社交亮点 · 人情温度",
                                style = MaterialTheme.typography.titleSmall,
                                color = ElectricBlue,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = result.socialAndChats,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            lineHeight = 20.sp,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Work Decisions & Resolutions Card
            if (!result.workAndDecisions.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDark)
                        .border(1.dp, MintCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkOutline,
                                contentDescription = null,
                                tint = MintCyan,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "💼 工作决策与会议决议",
                                style = MaterialTheme.typography.titleSmall,
                                color = MintCyan,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = result.workAndDecisions,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            lineHeight = 20.sp,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Timeline Highlights
            if (result.timelineHighlights.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDark)
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "🕒 全天关键时刻轨迹",
                                style = MaterialTheme.typography.titleSmall,
                                color = NeonGreen,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        result.timelineHighlights.forEach { item ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(CardElevated)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = item.timePeriod,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonGreen,
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
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Unified Action Items (Interactive Checkbox)
            if (result.unifiedActionItems.isNotEmpty()) {
                val doneCount = result.unifiedActionItems.count { it.isDone }
                val totalCount = result.unifiedActionItems.size

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDark)
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = NeonGreen,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "全天统一行动清单 ($doneCount/$totalCount)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            IconButton(
                                onClick = {
                                    val text = result.unifiedActionItems.joinToString("\n") {
                                        "${if (it.isDone) "[x]" else "[ ]"} ${it.text}"
                                    }
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("LifeLog Todos", text))
                                    Toast.makeText(context, "已复制待办清单", Toast.LENGTH_SHORT).show()
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

                        Spacer(modifier = Modifier.height(8.dp))

                        result.unifiedActionItems.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        val updatedList = result.unifiedActionItems.toMutableList()
                                        updatedList[index] = item.copy(isDone = !item.isDone)
                                        parsedResult = result.copy(unifiedActionItems = updatedList)
                                        existingLifeLog?.let { el ->
                                            scope.launch(Dispatchers.IO) {
                                                repository.updateActionItems(el.id, ActionItemModel.toJsonString(updatedList))
                                            }
                                        }
                                    }
                                    .padding(vertical = 5.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = if (item.isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (item.isDone) NeonGreen else TextMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (item.isDone) TextMuted else TextPrimary,
                                    textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Empty / Call to Action
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardDark)
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MintCyan.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = if (dayRecords.isEmpty()) "今日暂无录音内容" else "今日已记录 ${dayRecords.size} 段声音",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (dayRecords.isEmpty())
                            "开始随身录音后，在一天结束时可在此生成完整的全天复盘手记。"
                        else
                            "包含闲聊、会议、思考与待办。点击下方按钮一键提炼今日生活手记。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )

                    if (dayRecords.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonGreen)
                                .clickable(enabled = !isGenerating, onClick = triggerGenerateLifeLog)
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = if (isGenerating) "AI 正在提炼全天生活手记..." else "开始生成全天 LifeLog",
                                style = MaterialTheme.typography.labelLarge,
                                color = PureBlack,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.pageBottomNavClearance))
    }
}
