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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.model.ActionItemModel
import com.example.shuolesa.data.repository.AudioRepository
import com.example.shuolesa.theme.BgDark
import com.example.shuolesa.theme.CardDark
import com.example.shuolesa.theme.CardElevated
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.MintCyan
import com.example.shuolesa.theme.NeonGreen
import com.example.shuolesa.theme.SurfaceBorder
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.theme.TextSecondary
import com.example.shuolesa.ui.components.PageHeader
import com.example.shuolesa.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class TaskEntry(
    val recordId: Long,
    val recordTitle: String,
    val recordDate: Long,
    val isLifeLog: Boolean,
    val itemIndex: Int,
    val item: ActionItemModel,
    val allItemsInRecord: List<ActionItemModel>,
)

/**
 * 待办中心：跨录音与 LifeLog 的全局统一任务工作台
 */
@Composable
fun TasksScreen(
    repository: AudioRepository,
    onRecordClick: (AudioRecordEntity) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allRecords by repository.observeAllRecords().collectAsState(initial = emptyList())
    var selectedFilter by remember { mutableStateOf("全部") }

    // Parse all tasks across all records
    val allTasks = remember(allRecords) {
        val list = mutableListOf<TaskEntry>()
        allRecords.sortedByDescending { it.createdAt }.forEach { r ->
            if (!r.actionItems.isNullOrBlank() && r.actionItems != "[]") {
                val parsedItems = ActionItemModel.fromJsonString(r.actionItems)
                val isLifeLog = r.tags.orEmpty().contains("LifeLog")
                val title = r.title?.ifBlank { null } ?: if (isLifeLog) "全天复盘" else "语音便签"
                parsedItems.forEachIndexed { idx, item ->
                    list.add(
                        TaskEntry(
                            recordId = r.id,
                            recordTitle = title,
                            recordDate = r.createdAt,
                            isLifeLog = isLifeLog,
                            itemIndex = idx,
                            item = item,
                            allItemsInRecord = parsedItems,
                        )
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

    val filterTabs = listOf(
        "全部 (${allTasks.size})",
        "进行中 (${pendingTasks.size})",
        "已完成 (${doneTasks.size})",
    )

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
            .background(BgDark)
            .padding(horizontal = Dimens.pagePaddingH),
    ) {
        PageHeader(
            title = "待办中心",
            subtitle = "跨录音与复盘 · 行动任务清单",
            trailing = {
                if (pendingTasks.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            val text = pendingTasks.joinToString("\n") { "- [ ] ${it.item.text}" }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Pending Tasks", text))
                            Toast.makeText(context, "已复制 ${pendingTasks.size} 条待处理任务", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制待办",
                            tint = MintCyan,
                        )
                    }
                }
            },
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter tabs
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(filterTabs) { tab ->
                val baseTabName = tab.substringBefore(" ")
                val isSelected = selectedFilter == baseTabName
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MintCyan.copy(alpha = 0.15f) else CardDark)
                        .clickable { selectedFilter = baseTabName }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = tab,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MintCyan else TextSecondary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (filteredTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatListBulleted,
                        contentDescription = null,
                        tint = TextMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = if (allTasks.isEmpty()) "暂无待办事项" else "当前筛选无任务",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (allTasks.isEmpty())
                            "录音或生成 LifeLog 后，AI 提炼的所有行动事项将自动汇总至此。"
                        else
                            "可切换上方标签查看全部或进行中的任务。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Dimens.pageBottomNavClearance),
            ) {
                items(filteredTasks, key = { "${it.recordId}_${it.itemIndex}" }) { entry ->
                    TaskCard(
                        entry = entry,
                        onToggle = { toggleTask(entry) },
                        onNavigate = {
                            val target = allRecords.find { it.id == entry.recordId }
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardDark)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isDone) NeonGreen else TextMuted,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggle),
            ) {
                Text(
                    text = entry.item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDone) TextMuted else TextPrimary,
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                    lineHeight = 20.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (entry.isLifeLog) NeonGreen.copy(alpha = 0.12f) else CardElevated)
                            .clickable(onClick = onNavigate)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = (if (entry.isLifeLog) "🌿 " else "🎙️ ") + entry.recordTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.isLifeLog) NeonGreen else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 10.sp,
                        )
                    }

                    Text(
                        text = Formatters.formatListDate(entry.recordDate),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}
