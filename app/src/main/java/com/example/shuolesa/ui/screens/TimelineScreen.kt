package com.example.shuolesa.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.repository.AudioRepository
import com.example.shuolesa.network.UploadWorker
import com.example.shuolesa.theme.Accent
import com.example.shuolesa.theme.AccentOn
import com.example.shuolesa.theme.CardElevated
import com.example.shuolesa.theme.DangerRed
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.SurfaceBorder
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.theme.TextSecondary
import com.example.shuolesa.theme.WarningAmber
import com.example.shuolesa.ui.components.EmptyState
import com.example.shuolesa.ui.components.FilterChip
import com.example.shuolesa.ui.components.PageHeader
import com.example.shuolesa.ui.components.RecordFilter
import com.example.shuolesa.ui.components.RecordFilterMenus
import com.example.shuolesa.ui.components.TimelineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 记录流（v3.2）：顶栏搜/问、FTS、处理中置顶提示；空态区分筛选/真空。
 */
@Composable
fun TimelineScreen(
    repository: AudioRepository,
    prefs: AppPreferences,
    onRecordClick: (AudioRecordEntity) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    showProcessingHint: Boolean = false,
    onDismissProcessingHint: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allRecords by produceState<List<AudioRecordEntity>?>(initialValue = null, repository) {
        repository.observeAllRecords().collect { value = it }
    }
    val lifelogTriggerDuration by prefs.lifelogTriggerDuration.collectAsState(initial = 2)

    var filter by remember { mutableStateOf(RecordFilter()) }
    var pendingDelete by remember { mutableStateOf<AudioRecordEntity?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchModeAsk by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<AudioRecordEntity>?>(null) }
    var isSearching by remember { mutableStateOf(false) }

    val streamRecords = remember(allRecords) {
        allRecords.orEmpty().filter { !it.isDailyLifeLogSummary() }
    }

    val processingRecords = remember(streamRecords) {
        streamRecords.filter {
            it.status == AudioRecordEntity.STATUS_PENDING ||
                it.status == AudioRecordEntity.STATUS_UPLOADING
        }.sortedByDescending { it.createdAt }
    }

    LaunchedEffect(searchQuery, searchModeAsk) {
        val q = searchQuery.trim()
        if (searchModeAsk) {
            // 「问」为占位：不跑 FTS，避免与搜结果混淆
            searchResults = null
            isSearching = false
            return@LaunchedEffect
        }
        if (q.isEmpty()) {
            searchResults = null
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        kotlinx.coroutines.delay(280)
        val results = withContext(Dispatchers.IO) {
            repository.searchRecords(q).filter { !it.isDailyLifeLogSummary() }
        }
        searchResults = results
        isSearching = false
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            isImporting = true
            try {
                var displayName = "本地音频_${System.currentTimeMillis()}"
                var extension = "mp3"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) {
                            displayName = name
                            val dotIndex = name.lastIndexOf('.')
                            if (dotIndex != -1) {
                                extension = name.substring(dotIndex + 1)
                            }
                        }
                    }
                }

                val audioDir = File(context.filesDir, "audio").apply { if (!exists()) mkdirs() }
                val targetFile = File(audioDir, "imported_${System.currentTimeMillis()}.$extension")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val retriever = MediaMetadataRetriever()
                val durationMs = try {
                    retriever.setDataSource(targetFile.absolutePath)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } catch (_: Exception) {
                    0L
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }

                val cleanTitle = if (displayName.contains('.')) displayName.substringBeforeLast('.') else displayName
                val record = AudioRecordEntity(
                    sessionId = "import_${System.currentTimeMillis()}",
                    chunkIndex = 0,
                    filePath = targetFile.absolutePath,
                    durationMs = durationMs,
                    fileSizeBytes = targetFile.length(),
                    createdAt = System.currentTimeMillis(),
                    status = AudioRecordEntity.STATUS_PENDING,
                    title = cleanTitle,
                    recordingMode = "lifelog",
                    audioFormat = targetFile.extension.lowercase(),
                    tags = "[\"导入\"]",
                )
                repository.insertRecord(record)
                UploadWorker.enqueueOrRunNow(context)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "音频已导入，正在后台转写", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                isImporting = false
            }
        }
    }

    val filteredRecords = remember(streamRecords, filter, searchResults, searchQuery, searchModeAsk) {
        val base = if (searchQuery.trim().isNotEmpty() && !searchModeAsk) {
            searchResults.orEmpty()
        } else {
            streamRecords
        }
        base.filter { filter.matches(it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.pagePaddingH, vertical = Dimens.pagePaddingV),
    ) {
        PageHeader(
            title = "记录",
            onSettings = onOpenSettings,
            trailing = {
                if (!filter.isDefault) {
                    Text(
                        text = "重置",
                        style = MaterialTheme.typography.labelLarge,
                        color = Accent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.chipRadius))
                            .clickable { filter = RecordFilter() }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
                val failedCount = streamRecords.count { it.status == AudioRecordEntity.STATUS_FAILED }
                if (failedCount > 0) {
                    IconButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                repository.resetPendingAndFailed()
                                UploadWorker.processPendingUploadsManual(context)
                            }
                            Toast.makeText(context, "正在重试 $failedCount 条失败录音", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Replay,
                            contentDescription = "重试失败录音",
                            tint = Accent,
                        )
                    }
                }
                IconButton(
                    onClick = { audioPickerLauncher.launch("audio/*") },
                    enabled = !isImporting,
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Accent,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.FileUpload,
                            contentDescription = "导入音频",
                            tint = Accent,
                        )
                    }
                }
            },
        )

        Spacer(modifier = Modifier.height(Dimens.gapSm))

        RecordFilterMenus(
            filter = filter,
            onChange = { filter = it },
        )

        Spacer(modifier = Modifier.height(Dimens.gapSm))

        // 搜 / 问
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                label = "搜",
                selected = !searchModeAsk,
                onClick = { searchModeAsk = false },
            )
            FilterChip(
                label = "问",
                selected = searchModeAsk,
                onClick = { searchModeAsk = true },
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = {
                    Text(
                        if (searchModeAsk) "跨会话提问（即将支持）" else "搜索标题 / 转写 / 摘要",
                        color = TextMuted,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = TextMuted,
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = "清除搜索",
                                tint = TextMuted,
                            )
                        }
                    }
                },
                enabled = !searchModeAsk,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = SurfaceBorder,
                    focusedContainerColor = CardElevated,
                    unfocusedContainerColor = CardElevated,
                    disabledContainerColor = CardElevated.copy(alpha = 0.6f),
                    cursorColor = Accent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    disabledTextColor = TextMuted,
                ),
                shape = RoundedCornerShape(Dimens.fieldRadius),
            )
        }
        if (searchModeAsk) {
            Text(
                text = "跨会话提问即将支持。现在请切回「搜」，用关键词检索标题 / 转写 / 摘要（FTS）。",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Spacer(modifier = Modifier.height(Dimens.gapMd))

        if (showProcessingHint || processingRecords.isNotEmpty()) {
            ProcessingBanner(
                count = processingRecords.size.coerceAtLeast(1),
                onDismiss = onDismissProcessingHint,
                onOpenFirst = {
                    processingRecords.firstOrNull()?.let(onRecordClick)
                },
            )
            Spacer(modifier = Modifier.height(Dimens.gapSm))
        }

        if (allRecords == null) {
            Spacer(modifier = Modifier.weight(1f))
        } else if (isSearching && searchQuery.trim().isNotEmpty() && !searchModeAsk) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
        } else if (filteredRecords.isEmpty()) {
            val searching = searchQuery.trim().isNotEmpty() && !searchModeAsk
            EmptyTimeline(
                hasAnyRecords = streamRecords.isNotEmpty(),
                selectedFilter = when {
                    searching -> "搜索「${searchQuery.trim()}」"
                    filter.isDefault -> "全部"
                    else -> listOf(filter.dateLabel(), filter.mode, filter.status)
                        .filter { it != "全部" && it != "全部日期" && it != "类型" && it != "状态" }
                        .joinToString(" · ")
                        .ifBlank { "当前筛选" }
                },
                triggerSeconds = lifelogTriggerDuration,
                emptyBecauseSearch = searching,
                onRetryFailed = {
                    scope.launch(Dispatchers.IO) {
                        repository.resetPendingAndFailed()
                        UploadWorker.processPendingUploadsManual(context)
                    }
                    Toast.makeText(context, "正在重试失败录音", Toast.LENGTH_SHORT).show()
                },
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Dimens.listGap),
                contentPadding = PaddingValues(bottom = Dimens.gapLg),
            ) {
                items(filteredRecords, key = { it.id }) { record ->
                    TimelineItem(
                        record = record,
                        onClick = { onRecordClick(record) },
                        onLongClick = { pendingDelete = record },
                    )
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条记录？") },
            text = { Text("录音文件和纪要都会删掉，无法恢复。") },
            confirmButton = {
                Text(
                    text = "删除",
                    color = DangerRed,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable {
                            val toDelete = target
                            pendingDelete = null
                            scope.launch(Dispatchers.IO) {
                                repository.deleteRecord(toDelete)
                            }
                        }
                        .padding(Dimens.gapSm),
                )
            },
            dismissButton = {
                Text(
                    text = "取消",
                    color = TextSecondary,
                    modifier = Modifier
                        .clickable { pendingDelete = null }
                        .padding(Dimens.gapSm),
                )
            },
        )
    }
}

@Composable
private fun ProcessingBanner(
    count: Int,
    onDismiss: () -> Unit,
    onOpenFirst: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(WarningAmber.copy(alpha = 0.15f))
            .clickable(onClick = onOpenFirst)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "处理中 · $count 段",
                style = MaterialTheme.typography.labelLarge,
                color = WarningAmber,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "转写 → 摘要 → 待办。点此查看进度。",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
        Text(
            text = "知道了",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            modifier = Modifier
                .clickable(onClick = onDismiss)
                .padding(8.dp),
        )
    }
}

@Composable
private fun EmptyTimeline(
    hasAnyRecords: Boolean,
    selectedFilter: String,
    triggerSeconds: Int,
    emptyBecauseSearch: Boolean = false,
    onRetryFailed: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val title = when {
            emptyBecauseSearch -> "没有匹配的结果"
            !hasAnyRecords -> "还没有记录"
            else -> "没有「$selectedFilter」的记录"
        }
        val subtitle = when {
            emptyBecauseSearch -> "换个关键词试试，可搜标题、转写或摘要。"
            !hasAnyRecords -> "点底部麦克风开始，或从右上角导入\n息屏时同时按住音量 +/- 约 ${triggerSeconds} 秒可盲操随身录音"
            selectedFilter == "失败" -> "没有失败的录音。处理中的条目在「处理中」筛选里。"
            else -> "当前筛选下没有条目。试试切换上方筛选，或清除筛选。"
        }
        EmptyState(
            symbol = "",
            title = title,
            subtitle = subtitle,
        )

        if (selectedFilter == "失败" && hasAnyRecords) {
            Spacer(modifier = Modifier.height(Dimens.gapLg))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.pillRadius))
                    .background(Accent)
                    .clickable(onClick = onRetryFailed)
                    .padding(horizontal = Dimens.gapLg, vertical = Dimens.gapSm),
            ) {
                Text(
                    text = "重试失败录音",
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentOn,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
