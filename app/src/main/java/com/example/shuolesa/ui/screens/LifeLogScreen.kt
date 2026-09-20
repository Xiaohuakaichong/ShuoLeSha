package com.example.shuolesa.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.StickyNote2
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.model.ActionItemModel
import com.example.shuolesa.data.model.LifeLogResult
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.repository.AudioRepository
import com.example.shuolesa.network.ApiService
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
import com.example.shuolesa.ui.components.ModeBadge
import com.example.shuolesa.ui.components.PageHeader
import com.example.shuolesa.ui.components.SectionLabel
import com.example.shuolesa.ui.components.TerminalCard
import com.example.shuolesa.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 今日两层（v3.2）：日期 chips + 当天片段可点进纪要 + 下方复盘正文。
 * 筛选只排除复盘行（isDailyLifeLogSummary），不误伤随身。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LifeLogScreen(
    repository: AudioRepository,
    prefs: AppPreferences,
    onOpenSettings: () -> Unit = {},
    onRecordClick: (AudioRecordEntity) -> Unit = {},
    focusDateMs: Long? = null,
    onFocusDateConsumed: () -> Unit = {},
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
    var dayReady by remember { mutableStateOf(false) }

    LaunchedEffect(focusDateMs) {
        if (focusDateMs != null && focusDateMs > 0L) {
            selectedDateMs = focusDateMs
            onFocusDateConsumed()
        }
    }

    fun applyDaySnapshot(all: List<AudioRecordEntity>) {
        val startOfDay = Formatters.getStartOfDay(selectedDateMs)
        val endOfDay = Formatters.getEndOfDay(selectedDateMs)
        // P0：只排除复盘行，不要用 tags.contains("LifeLog") 滤掉随身
        val records = all.filter {
            it.createdAt in startOfDay..endOfDay && !it.isDailyLifeLogSummary()
        }.sortedBy { it.createdAt }
        val lifeLogRecord = all.firstOrNull { it.sessionId == "lifelog_$dateStr" }
            ?: all.firstOrNull {
                it.isDailyLifeLogSummary() &&
                    it.createdAt in startOfDay..endOfDay
            }
        dayRecords = records
        existingLifeLog = lifeLogRecord
        parsedResult = if (lifeLogRecord != null && !lifeLogRecord.agentResult.isNullOrBlank()) {
            LifeLogResult.fromJson(lifeLogRecord.agentResult)
        } else {
            null
        }
        dayReady = true
    }

    fun reloadDayData() {
        scope.launch(Dispatchers.IO) {
            val all = repository.observeAllRecords().first()
            withContext(Dispatchers.Main) {
                applyDaySnapshot(all)
            }
        }
    }

    // 订阅全量流：录中上云产生的新片段会自动出现在「当天片段」
    LaunchedEffect(selectedDateMs) {
        dayReady = false
        repository.observeAllRecords().collect { all ->
            applyDaySnapshot(all)
        }
    }

    val dateOptions = remember {
        val now = System.currentTimeMillis()
        listOf(
            "今天" to now,
            "昨天" to now - 86_400_000L,
            "前天" to now - 2 * 86_400_000L,
        )
    }

    val triggerGenerateLifeLog = {
        if (!isGenerating && dayRecords.isNotEmpty()) {
            isGenerating = true
            scope.launch(Dispatchers.IO) {
                try {
                    val baseUrl = prefs.baseUrl.first()
                    val apiKey = prefs.apiKey.first()
                    val llmModel = prefs.llmModel.first()
                    if (prefs.requiresApiKeySync()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "请先在设置中配置 API Key", Toast.LENGTH_LONG).show()
                            onOpenSettings()
                            isGenerating = false
                        }
                        return@launch
                    }

                    val recordsContext = buildString {
                        dayRecords.sortedBy { it.createdAt }.forEachIndexed { idx, r ->
                            val time = Formatters.formatTimeOnly(r.createdAt)
                            val title = r.title ?: "片段 ${idx + 1}"
                            val dur = Formatters.formatDuration(r.durationMs)
                            val role = when {
                                r.isMeeting() -> "会议"
                                else -> "随身"
                            }
                            appendLine("【片段 ${idx + 1}·$role】时间：$time | 时长：$dur | 标题：$title")
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
                            tags = "[\"复盘\", \"每日复盘\"]",
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
                            tags = "[\"复盘\", \"每日复盘\"]",
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.pagePaddingH),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = Dimens.pagePaddingV,
            bottom = Dimens.gapXl,
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        stickyHeader {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(com.example.shuolesa.theme.AppColor.background.copy(alpha = 0.96f))
                    .padding(bottom = Dimens.gapSm),
            ) {
                PageHeader(
                    title = "今日",
                    subtitle = dateStr,
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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm)) {
                    items(dateOptions) { (label, ms) ->
                        val selected = Formatters.formatDateOnly(selectedDateMs) == Formatters.formatDateOnly(ms)
                        FilterChip(
                            label = label,
                            selected = selected,
                            onClick = {
                                if (!isGenerating) selectedDateMs = ms
                            },
                            accent = ModeDigest,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.gapSm))
                TerminalCard(borderColor = if (parsedResult != null) ModeDigest.copy(alpha = 0.35f) else SurfaceBorder) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "当天 ${dayRecords.size} 段",
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
                                    .clickable(enabled = !isGenerating, onClick = { triggerGenerateLifeLog() })
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
                                        color = if (isGenerating) {
                                            TextSecondary
                                        } else {
                                            com.example.shuolesa.theme.AppColor.onTertiary
                                        },
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(Dimens.gapMd)) }

        // 当天片段列表
        if (dayRecords.isNotEmpty()) {
            item {
                SectionLabel("当天片段")
                Spacer(modifier = Modifier.height(Dimens.gapSm))
            }
            items(dayRecords.sortedBy { it.createdAt }, key = { it.id }) { record ->
                DaySegmentRow(record = record, onClick = { onRecordClick(record) })
                Spacer(modifier = Modifier.height(Dimens.gapSm))
            }
        } else if (dayReady) {
            item {
                TerminalCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimens.gapLg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = null,
                            tint = ModeDigest.copy(alpha = 0.7f),
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            text = "这一天还没有声音",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "去录一段：点底部麦克风。有片段后再生成复盘。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.gapMd))
            }
        }

        // 复盘正文
        val result = parsedResult
        if (result != null) {
            item {
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
                Spacer(modifier = Modifier.height(Dimens.gapMd))
            }
            if (result.summary.isNotBlank()) {
                item {
                    DigestSection(title = "综述", icon = Icons.Outlined.AutoAwesome, accent = ModeDigest, body = result.summary)
                    Spacer(modifier = Modifier.height(Dimens.gapMd))
                }
            }
            if (!result.socialAndChats.isNullOrBlank()) {
                item {
                    DigestSection(title = "闲聊与社交", icon = Icons.Outlined.ChatBubbleOutline, accent = ModeCasual, body = result.socialAndChats)
                    Spacer(modifier = Modifier.height(Dimens.gapMd))
                }
            }
            if (!result.workAndDecisions.isNullOrBlank()) {
                item {
                    DigestSection(title = "工作与决议", icon = Icons.Outlined.WorkOutline, accent = ModeMeeting, body = result.workAndDecisions)
                    Spacer(modifier = Modifier.height(Dimens.gapMd))
                }
            }
            item {
                DigestListSection(title = "提醒", icon = Icons.Outlined.NotificationsNone, accent = ModeMeeting, items = result.reminders, emptyHint = "今天没有需要跟进的提醒")
                Spacer(modifier = Modifier.height(Dimens.gapMd))
            }
            item {
                DigestSection(title = "备忘", icon = Icons.Outlined.StickyNote2, accent = ModeCasual, body = result.notepad?.takeIf { it.isNotBlank() } ?: "暂无备忘杂记")
                Spacer(modifier = Modifier.height(Dimens.gapMd))
            }
            item {
                DigestListSection(title = "记忆", icon = Icons.Outlined.Psychology, accent = ModeDigest, items = result.memory, emptyHint = "暂无值得长期记住的条目")
                Spacer(modifier = Modifier.height(Dimens.gapMd))
            }
            if (result.timelineHighlights.isNotEmpty()) {
                item {
                    TerminalCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                        ) {
                            Icon(Icons.Outlined.Timeline, null, tint = ModeDigest, modifier = Modifier.size(16.dp))
                            SectionLabel("关键时刻")
                        }
                        Spacer(modifier = Modifier.height(Dimens.gapSm))
                        result.timelineHighlights.forEach { itemHl ->
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
                                        text = itemHl.timePeriod,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ModeDigest,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Text(
                                    text = if (itemHl.title.isNotBlank()) "${itemHl.title}：${itemHl.content}" else itemHl.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(Dimens.gapMd))
                }
            }
        } else if (dayReady && dayRecords.isNotEmpty()) {
            item {
                TerminalCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.gapLg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = ModeDigest.copy(alpha = 0.7f), modifier = Modifier.size(40.dp))
                        Text(
                            text = "已有 ${dayRecords.size} 段记录",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "点上方「生成复盘」，把当天的随身、会议和待办收成一篇手记。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.gapMd))
            }
        }

        if (dayTasks.isNotEmpty()) {
            item {
                val doneCount = dayTasks.count { it.item.isDone }
                TerminalCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel("当日待办 ($doneCount/${dayTasks.size})")
                        IconButton(
                            onClick = {
                                val text = dayTasks.filter { !it.item.isDone }.joinToString("\n") { "- [ ] ${it.item.text}" }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("今日待办", text))
                                Toast.makeText(context, "已复制待办", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Default.ContentCopy, "复制待办", tint = TextSecondary, modifier = Modifier.size(15.dp))
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
        }
    }
}

@Composable
private fun DaySegmentRow(
    record: AudioRecordEntity,
    onClick: () -> Unit,
) {
    val isMeeting = record.isMeeting()
    TerminalCard(
        contentPadding = false,
        borderColor = (if (isMeeting) ModeMeeting else ModeCasual).copy(alpha = 0.2f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(Dimens.cardPaddingVCompact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
        ) {
            Text(
                text = Formatters.formatTimeOnly(record.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
            ModeBadge(isMeeting = isMeeting, isDigest = false)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title?.takeIf { it.isNotBlank() }
                        ?: if (isMeeting) "会议录音" else "随身记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!record.summary.isNullOrBlank()) {
                    Text(
                        text = record.summary!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = Formatters.formatDuration(record.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun DigestListSection(
    title: String,
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    items: List<String>,
    emptyHint: String,
) {
    TerminalCard(borderColor = accent.copy(alpha = 0.28f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
            SectionLabel(title, color = accent)
        }
        Spacer(modifier = Modifier.height(Dimens.gapSm))
        if (items.isEmpty()) {
            Text(text = emptyHint, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        } else {
            items.forEach { line ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                ) {
                    Text(text = "•", color = accent, fontWeight = FontWeight.Bold)
                    Text(text = line, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                }
            }
        }
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
            Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
            SectionLabel(title, color = accent)
        }
        Spacer(modifier = Modifier.height(Dimens.gapSm))
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}
