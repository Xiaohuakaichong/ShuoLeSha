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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.model.ActionItemModel
import com.example.shuolesa.data.repository.AudioRepository
import com.example.shuolesa.theme.Accent
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.ModeDigest
import com.example.shuolesa.theme.ModeMeeting
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.theme.TextSecondary
import com.example.shuolesa.ui.components.FilterChip
import com.example.shuolesa.ui.components.ModeBadge
import com.example.shuolesa.ui.components.PageHeader
import com.example.shuolesa.ui.components.TerminalCard
import com.example.shuolesa.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class TaskEntry(
    val recordId: Long,
    val recordTitle: String,
    val recordDate: Long,
    val isLifeLog: Boolean,
    val isMeeting: Boolean,
    val itemIndex: Int,
    val item: ActionItemModel,
    val allItemsInRecord: List<ActionItemModel>,
)

@Composable
fun TasksScreen(
    repository: AudioRepository,
    onRecordClick: (AudioRecordEntity) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allRecords by produceState<List<AudioRecordEntity>?>(initialValue = null, repository) {
        repository.observeAllRecords().collect { value = it }
    }
    var selectedFilter by remember { mutableStateOf("进行中") }

    val allTasks = remember(allRecords) {
        val list = mutableListOf<TaskEntry>()
        allRecords.orEmpty().sortedByDescending { it.createdAt }.forEach { r ->
            if (r.isDailyLifeLogSummary()) return@forEach
            if (!r.actionItems.isNullOrBlank() && r.actionItems != "[]") {
                val parsedItems = ActionItemModel.fromJsonString(r.actionItems)
                val isLifeLog = r.isDailyLifeLogSummary()
                val title = r.title?.ifBlank { null } ?: if (isLifeLog) "全天复盘" else "语音记录"
                parsedItems.forEachIndexed { idx, item ->
                    list.add(
                        TaskEntry(
                            recordId = r.id,
                            recordTitle = title,
                            recordDate = r.createdAt,
                            isLifeLog = isLifeLog,
                            isMeeting = r.isMeeting(),
                            itemIndex = idx,
                            item = item,
                            allItemsInRecord = parsedItems,
                        ),
                    )
                }
            }
        }
        list
    }

    val pendingTasks = remember(allTasks) { allTasks.filter { !it.item.isDone } }
    val doneTasks = remember(allTasks) { allTasks.filter { it.item.isDone } }
    val filteredTasks = remember(allTasks, selectedFilter) {
        when (selectedFilter) {
            "进行中" -> pendingTasks
            "已完成" -> doneTasks
            else -> allTasks
        }
    }

    fun toggleTask(entry: TaskEntry) {
        val updatedItems = entry.allItemsInRecord.toMutableList()
        if (entry.itemIndex in updatedItems.indices) {
            val current = updatedItems[entry.itemIndex]
            updatedItems[entry.itemIndex] = current.copy(isDone = !current.isDone)
            scope.launch(Dispatchers.IO) {
                repository.updateActionItems(entry.recordId, ActionItemModel.toJsonString(updatedItems))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.pagePaddingH, vertical = Dimens.pagePaddingV),
    ) {
        PageHeader(
            title = "待办",
            subtitle = "${pendingTasks.size} 项进行中",
            trailing = {
                if (pendingTasks.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            val text = pendingTasks.joinToString("\n") { "- [ ] ${it.item.text}" }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Pending Tasks", text))
                            Toast.makeText(context, "已复制 ${pendingTasks.size} 条待办", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制待办",
                            tint = Accent,
                        )
                    }
                }
            },
        )

        Spacer(modifier = Modifier.height(Dimens.gapSm))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val tabs = listOf(
                "进行中" to pendingTasks.size,
                "已完成" to doneTasks.size,
                "全部" to allTasks.size,
            )
            items(tabs) { (name, count) ->
                FilterChip(
                    label = "$name $count",
                    selected = selectedFilter == name,
                    onClick = { selectedFilter = name },
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.gapMd))

        if (allRecords == null) {
            Spacer(modifier = Modifier.weight(1f))
        } else if (filteredTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.FormatListBulleted,
                        contentDescription = null,
                        tint = TextMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = if (allTasks.isEmpty()) "还没有待办" else "当前筛选为空",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (allTasks.isEmpty()) {
                            "录音提炼后，行动项会汇总到这里。"
                        } else {
                            "切换上方筛选查看其他任务。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Dimens.listGap),
                contentPadding = PaddingValues(bottom = Dimens.gapLg),
            ) {
                items(filteredTasks, key = { "${it.recordId}_${it.itemIndex}" }) { entry ->
                    TaskCard(
                        entry = entry,
                        onToggle = { toggleTask(entry) },
                        onNavigate = {
                            val target = allRecords.orEmpty().find { it.id == entry.recordId }
                            if (target != null) {
                                onRecordClick(target)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    entry: TaskEntry,
    onToggle: () -> Unit,
    onNavigate: () -> Unit,
) {
    val isDone = entry.item.isDone
    TerminalCard(contentPadding = false) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.cardPaddingVCompact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
        ) {
            IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isDone) Accent else TextMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDone) TextMuted else TextPrimary,
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.clickable(onClick = onToggle),
                )
                if (!entry.item.whenHint.isNullOrBlank() || !entry.item.quote.isNullOrBlank()) {
                    val detail = listOfNotNull(entry.item.whenHint, entry.item.quote).joinToString(" · ")
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(Dimens.gapXs))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                ) {
                    ModeBadge(isMeeting = entry.isMeeting, isDigest = entry.isLifeLog)
                    Text(
                        text = entry.recordTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            entry.isLifeLog -> ModeDigest
                            entry.isMeeting -> ModeMeeting
                            else -> TextSecondary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable(onClick = onNavigate),
                    )
                    Text(
                        text = Formatters.formatListDate(entry.recordDate),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                }
            }
        }
    }
}
