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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.repository.AudioRepository
import com.example.shuolesa.network.UploadWorker
import com.example.shuolesa.theme.BgDark
import com.example.shuolesa.theme.CardDark
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.MintCyan
import com.example.shuolesa.theme.NeonGreen
import com.example.shuolesa.theme.SurfaceBorder
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.theme.TextSecondary
import com.example.shuolesa.ui.components.EmptyState
import com.example.shuolesa.ui.components.LifeLogSheet
import com.example.shuolesa.ui.components.PageHeader
import com.example.shuolesa.ui.components.TerminalCard
import com.example.shuolesa.ui.components.TimelineItem
import com.example.shuolesa.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 现代记忆流时间线：智能便签流、分类筛选与本地音频导入解析。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    repository: AudioRepository,
    prefs: AppPreferences,
    onRecordClick: (AudioRecordEntity) -> Unit = {},
    onStartRecord: () -> Unit = {},
    onNavigateToLifeLog: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allRecords by repository.observeAllRecords().collectAsState(initial = emptyList())
    val triggerDuration by prefs.triggerDuration.collectAsState(initial = 3)
    val recordingMode by prefs.recordingMode.collectAsState(initial = "lifelog")

    var selectedFilter by remember { mutableStateOf("全部") }
    val filterTabs = listOf("全部", "已提炼", "含待办", "处理中")
    var isImporting by remember { mutableStateOf(false) }
    var showLifeLogSheet by remember { mutableStateOf(false) }

    val todayStart = remember { Formatters.getStartOfDay(System.currentTimeMillis()) }
    val todayRecords = remember(allRecords) {
        allRecords.filter { it.createdAt >= todayStart && !it.tags.orEmpty().contains("LifeLog") }
    }
    val todayLifeLog = remember(allRecords) {
        allRecords.find { it.createdAt >= todayStart && it.tags.orEmpty().contains("LifeLog") }
    }

    // 本地音频选择器 Launcher
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            isImporting = true
            try {
                // 1. 查询原文件名
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

                // 2. 复制音频到应用专属目录
                val audioDir = File(context.filesDir, "audio").apply { if (!exists()) mkdirs() }
                val targetFile = File(audioDir, "imported_${System.currentTimeMillis()}.$extension")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 3. 计算音频时长
                val retriever = MediaMetadataRetriever()
                val durationMs = try {
                    retriever.setDataSource(targetFile.absolutePath)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } catch (_: Exception) {
                    0L
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }

                // 4. 插入本地数据库记录
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
                )
                repository.insertRecord(record)

                // 5. 触发后台转写与结构化提炼
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val uploadRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                    .setConstraints(constraints)
                    .build()
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        UploadWorker.WORK_NAME,
                        ExistingWorkPolicy.REPLACE,
                        uploadRequest,
                    )

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "音频导入成功，正在后台转写提炼...", Toast.LENGTH_SHORT).show()
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

    val filteredRecords = remember(allRecords, selectedFilter) {
        when (selectedFilter) {
            "已提炼" -> allRecords.filter { it.status == AudioRecordEntity.STATUS_UPLOADED && !it.summary.isNullOrBlank() }
            "含待办" -> allRecords.filter { !it.actionItems.isNullOrBlank() && it.actionItems != "[]" }
            "处理中" -> allRecords.filter { it.status == AudioRecordEntity.STATUS_PENDING || it.status == AudioRecordEntity.STATUS_UPLOADING }
            else -> allRecords
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = Dimens.pagePaddingH, vertical = Dimens.pagePaddingV),
    ) {
        PageHeader(
            title = "说了啥 · 记忆流",
            subtitle = "随手语音转写与 AI 智能提炼",
            trailing = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.fieldRadius))
                        .background(MintCyan.copy(alpha = 0.12f))
                        .border(1.dp, MintCyan.copy(alpha = 0.35f), RoundedCornerShape(Dimens.fieldRadius))
                        .clickable(enabled = !isImporting) {
                            audioPickerLauncher.launch("audio/*")
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = MintCyan,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = "导入音频",
                                tint = MintCyan,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(
                            text = if (isImporting) "导入中..." else "导入音频",
                            style = MaterialTheme.typography.labelMedium,
                            color = MintCyan,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter chips row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(filterTabs) { tab ->
                val isSelected = selectedFilter == tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MintCyan.copy(alpha = 0.15f) else CardDark)
                        .clickable { selectedFilter = tab }
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
        Spacer(modifier = Modifier.height(12.dp))

        // LifeLog Banner Card
        TerminalCard(
            borderColor = if (todayLifeLog != null) NeonGreen.copy(alpha = 0.35f) else MintCyan.copy(alpha = 0.25f),
            modifier = Modifier.clickable { onNavigateToLifeLog() },
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
                            .background(if (todayLifeLog != null) NeonGreen.copy(alpha = 0.15f) else MintCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (todayLifeLog != null) NeonGreen else MintCyan,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🌿 生活手记 · 每日复盘",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (todayLifeLog != null)
                                "今日已复盘 · 点击查看闲聊亮点与待办"
                            else
                                "今日已捕捉 ${todayRecords.size} 段声音 · 点击一键生成",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (todayLifeLog != null) NeonGreen.copy(alpha = 0.15f) else MintCyan.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = if (todayLifeLog != null) "已复盘" else "去生成",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (todayLifeLog != null) NeonGreen else MintCyan,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (filteredRecords.isEmpty()) {
            EmptyTimeline(
                triggerSeconds = triggerDuration,
                onStartRecord = onStartRecord,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = Dimens.pageBottomNavClearance),
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

    if (showLifeLogSheet) {
        LifeLogSheet(
            repository = repository,
            prefs = prefs,
            onDismiss = { showLifeLogSheet = false },
        )
    }
}

@Composable
private fun EmptyTimeline(
    triggerSeconds: Int,
    onStartRecord: () -> Unit = {},
) {
    val infiniteTransition = rememberInfiniteTransition(label = "empty")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "emptyAlpha",
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            symbol = "🎙️",
            title = "尚无录音记录",
            subtitle = "点击右上角【导入音频】或右下角悬浮按钮开启录音\n亦可息屏长按音量 +/- 键 ${triggerSeconds} 秒盲操录音",
            modifier = Modifier.alpha(alpha),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MintCyan.copy(alpha = 0.12f))
                .clickable { onStartRecord() }
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(
                text = "⚡ 立即启动录音",
                style = MaterialTheme.typography.labelLarge,
                color = MintCyan,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
