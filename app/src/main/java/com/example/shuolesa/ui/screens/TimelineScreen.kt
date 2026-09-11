package com.example.shuolesa.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
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
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.ModeCasual
import com.example.shuolesa.theme.ModeMeeting
import com.example.shuolesa.ui.components.EmptyState
import com.example.shuolesa.ui.components.FilterChip
import com.example.shuolesa.ui.components.PageHeader
import com.example.shuolesa.ui.components.TimelineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 记录流：录音与导入的结果列表。每日复盘不出现在这里。
 */
@Composable
fun TimelineScreen(
    repository: AudioRepository,
    prefs: AppPreferences,
    onRecordClick: (AudioRecordEntity) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allRecords by produceState<List<AudioRecordEntity>?>(initialValue = null, repository) {
        repository.observeAllRecords().collect { value = it }
    }
    val lifelogTriggerDuration by prefs.lifelogTriggerDuration.collectAsState(initial = 2)

    var selectedFilter by remember { mutableStateOf("全部") }
    var isImporting by remember { mutableStateOf(false) }

    val streamRecords = remember(allRecords) {
        allRecords.orEmpty().filter { !it.isDailyLifeLogSummary() }
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

    val filteredRecords = remember(streamRecords, selectedFilter) {
        when (selectedFilter) {
            "随身" -> streamRecords.filter { !it.isMeeting() }
            "会议" -> streamRecords.filter { it.isMeeting() }
            "处理中" -> streamRecords.filter {
                it.status == AudioRecordEntity.STATUS_PENDING || it.status == AudioRecordEntity.STATUS_UPLOADING
            }
            "失败" -> streamRecords.filter { it.status == AudioRecordEntity.STATUS_FAILED }
            else -> streamRecords
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.pagePaddingH, vertical = Dimens.pagePaddingV),
    ) {
        PageHeader(
            title = "记录",
            trailing = {
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

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val tabs = listOf("全部", "随身", "会议", "处理中", "失败")
            items(tabs) { tab ->
                val accent = when (tab) {
                    "会议" -> ModeMeeting
                    "随身" -> ModeCasual
                    else -> Accent
                }
                FilterChip(
                    label = tab,
                    selected = selectedFilter == tab,
                    onClick = { selectedFilter = tab },
                    accent = accent,
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.gapMd))

        if (allRecords == null) {
            Spacer(modifier = Modifier.weight(1f))
        } else if (filteredRecords.isEmpty()) {
            EmptyTimeline(
                hasAnyRecords = streamRecords.isNotEmpty(),
                selectedFilter = selectedFilter,
                triggerSeconds = lifelogTriggerDuration,
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
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTimeline(
    hasAnyRecords: Boolean,
    selectedFilter: String,
    triggerSeconds: Int,
    onRetryFailed: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val title = if (!hasAnyRecords) "还没有记录" else "没有「$selectedFilter」的记录"
        val subtitle = when {
            !hasAnyRecords -> "点底部麦克风开始，或从右上角导入\n息屏时同时按住音量 +/- 约 ${triggerSeconds} 秒可盲操随身录音"
            selectedFilter == "失败" -> "没有失败的录音。处理中的条目在「处理中」筛选里。"
            else -> "试试切换上方筛选。"
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
