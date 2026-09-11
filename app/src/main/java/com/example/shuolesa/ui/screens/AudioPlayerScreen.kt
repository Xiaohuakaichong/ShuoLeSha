package com.example.shuolesa.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shuolesa.audio.OpusPlayer
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.model.ActionItemModel
import com.example.shuolesa.data.model.PromptTemplate
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.repository.AudioRepository
import com.example.shuolesa.network.ApiService
import com.example.shuolesa.theme.BorderGray
import com.example.shuolesa.theme.CardDark
import com.example.shuolesa.theme.CardElevated
import com.example.shuolesa.theme.DangerRed
import com.example.shuolesa.theme.DarkCard
import com.example.shuolesa.theme.DarkGray
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.ElectricBlue
import com.example.shuolesa.theme.MintCyan
import com.example.shuolesa.theme.NeonGreen
import com.example.shuolesa.theme.NeonGreenDim
import com.example.shuolesa.theme.PureBlack
import com.example.shuolesa.theme.SurfaceBorder
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.theme.TextSecondary
import com.example.shuolesa.ui.components.PageHeader
import com.example.shuolesa.ui.components.SectionLabel
import com.example.shuolesa.ui.components.StatusBadge
import com.example.shuolesa.ui.components.TerminalCard
import com.example.shuolesa.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

data class DialogueTurn(
    val speaker: String,
    val content: String,
)

private fun parseDialogueTurns(text: String): List<DialogueTurn> {
    if (text.isBlank()) return emptyList()
    val pattern = Regex("""(?:【([^】]+)】|([^\n：:]{1,10})[：:])\s*([\s\S]*?)(?=(?:【[^】]+】|[^\n：:]{1,10}[：:])|$)""")
    val matches = pattern.findAll(text).toList()
    if (matches.size < 2) return emptyList()
    return matches.mapNotNull { m ->
        val spk = m.groupValues[1].ifBlank { m.groupValues[2] }.trim()
        val cnt = m.groupValues[3].trim()
        if (spk.isNotEmpty() && cnt.isNotEmpty()) {
            DialogueTurn(spk, cnt)
        } else null
    }
}

/**
 * 现代播放与多模态 AI 纪要界面：
 * 支持音字回放、交互式待办勾选、多场景 AI 重提炼、对话角色对白渲染与“针对此录音追问”
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AudioPlayerScreen(
    record: AudioRecordEntity,
    repository: AudioRepository? = null,
    prefs: AppPreferences? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 观察实时记录状态
    val liveRecordState by repository?.observeRecordById(record.id)?.collectAsState(initial = record) ?: remember { mutableStateOf(record) }
    val currentRecord = liveRecordState ?: record

    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(currentRecord.durationMs.coerceAtLeast(1L)) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    val fileExists = remember(currentRecord.filePath) { File(currentRecord.filePath).exists() }
    var playerError by remember(currentRecord.filePath) {
        mutableStateOf(
            if (!fileExists) "本地文件已清理（仅保留云端分析结果）" else null,
        )
    }

    // 重新提炼与问答状态
    var isRegenerating by remember { mutableStateOf(false) }
    var regeneratingTemplateId by remember { mutableStateOf<String?>(null) }
    var questionInput by remember { mutableStateOf("") }
    var aiAnswer by remember { mutableStateOf<String?>(null) }
    var isAskingAi by remember { mutableStateOf(false) }

    val opusPlayer = remember { OpusPlayer(context) }

    BackHandler(enabled = true) {
        try {
            opusPlayer.stop()
        } catch (_: Exception) {
        }
        onBack()
    }

    DisposableEffect(currentRecord.filePath) {
        if (!fileExists) {
            onDispose { }
        } else {
            opusPlayer.onProgressUpdate = { pos ->
                if (!isSeeking) {
                    currentPositionMs = pos
                    sliderPosition = (pos.toFloat() / durationMs).coerceIn(0f, 1f)
                }
            }
            opusPlayer.onCompletion = {
                isPlaying = false
                currentPositionMs = durationMs
                sliderPosition = 1f
            }
            opusPlayer.onError = { err ->
                playerError = err
            }

            val success = opusPlayer.prepare(currentRecord.filePath)
            if (success) {
                durationMs = opusPlayer.getDurationMs().coerceAtLeast(1L)
            }

            onDispose {
                opusPlayer.release()
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
        ),
        label = "rotation",
    )

    // 解析待办与标签
    val actionItems = remember(currentRecord.actionItems) {
        ActionItemModel.fromJsonString(currentRecord.actionItems)
    }

    val tags = remember(currentRecord.tags) {
        val list = mutableListOf<String>()
        if (!currentRecord.tags.isNullOrBlank()) {
            try {
                val json = JSONArray(currentRecord.tags)
                for (i in 0 until json.length()) {
                    list.add(json.getString(i))
                }
            } catch (_: Exception) {
                currentRecord.tags.split(",", " ").filter { it.isNotBlank() }.forEach { list.add(it) }
            }
        }
        list
    }

    val dialogueTurns = remember(currentRecord.transcription) {
        parseDialogueTurns(currentRecord.transcription ?: "")
    }

    // 导出/复制 Markdown 逻辑
    val copyMarkdown = {
        val md = buildString {
            appendLine("# ${currentRecord.title ?: "语音记录"}")
            appendLine("- **时间**：${Formatters.formatDetailDate(currentRecord.createdAt)}")
            appendLine("- **时长**：${Formatters.formatDuration(currentRecord.durationMs)}")
            if (tags.isNotEmpty()) {
                appendLine("- **标签**：${tags.joinToString(" ") { "#$it" }}")
            }
            appendLine()
            appendLine("## 💡 核心要点")
            appendLine(currentRecord.summary ?: "无")
            appendLine()
            if (actionItems.isNotEmpty()) {
                appendLine("## ✅ 行动待办")
                actionItems.forEach { item ->
                    val mark = if (item.isDone) "[x]" else "[ ]"
                    appendLine("- $mark ${item.text}")
                }
                appendLine()
            }
            appendLine("## 📝 转录稿与对白")
            appendLine(currentRecord.transcription ?: "无转录文本")
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("说了啥纪要", md))
        Toast.makeText(context, "已复制 Markdown 纪要到剪贴板", Toast.LENGTH_SHORT).show()
    }

    // 重新提炼触发逻辑
    val triggerRegenerate = { template: PromptTemplate ->
        val textToProcess = currentRecord.transcription
            ?.takeIf { it.isNotBlank() }
            ?: currentRecord.summary
            ?: ""

        if (textToProcess.isBlank()) {
            Toast.makeText(context, "没有可供提炼的转录文字", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch(Dispatchers.IO) {
                isRegenerating = true
                regeneratingTemplateId = template.id
                try {
                    val baseUrl = prefs?.getBaseUrlSync() ?: ""
                    val apiKey = prefs?.getApiKeySync() ?: ""
                    val llmModel = prefs?.getLlmModelSync() ?: ""

                    val apiService = ApiService()
                    val result = apiService.generateStructuredNotes(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        llmModel = llmModel,
                        systemPrompt = template.systemPrompt,
                        transcription = textToProcess,
                    )

                    val notes = result.getOrNull()
                    if (notes != null) {
                        val actionsJson = ActionItemModel.toJsonString(notes.actionItems)
                        val tagsJson = JSONArray(notes.tags).toString()
                        val finalTrans = notes.structuredTranscript?.takeIf { it.isNotBlank() }
                            ?: currentRecord.transcription

                        repository?.markProcessedStructured(
                            id = currentRecord.id,
                            title = notes.title,
                            summary = notes.summary,
                            actionItems = actionsJson,
                            tags = tagsJson,
                            transcription = finalTrans,
                            agentResult = notes.rawJson,
                        )
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "已切换为【${template.title}】提炼完成", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "提炼返回空结果", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "提炼失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    isRegenerating = false
                    regeneratingTemplateId = null
                }
            }
        }
    }

    // 录音追问逻辑
    val askQuestion = { q: String ->
        val question = q.trim()
        if (question.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                isAskingAi = true
                aiAnswer = null
                try {
                    val baseUrl = prefs?.getBaseUrlSync() ?: ""
                    val apiKey = prefs?.getApiKeySync() ?: ""
                    val llmModel = prefs?.getLlmModelSync() ?: ""
                    val content = currentRecord.transcription ?: currentRecord.summary ?: ""

                    val apiService = ApiService()
                    val res = apiService.askQuestionAboutNote(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        llmModel = llmModel,
                        transcription = content,
                        question = question,
                    )
                    withContext(Dispatchers.Main) {
                        aiAnswer = res.getOrElse { "提问遇到异常: ${it.message}" }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        aiAnswer = "提问异常: ${e.message}"
                    }
                } finally {
                    isAskingAi = false
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Dimens.pagePaddingH),
    ) {
        // Top Action Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.gapSm, bottom = Dimens.gapSm),
        ) {
            IconButton(onClick = {
                try {
                    opusPlayer.stop()
                } catch (_: Exception) {
                }
                onBack()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextPrimary,
                )
            }
            Spacer(modifier = Modifier.width(Dimens.gapXs))
            PageHeader(
                title = "播放与纪要",
                subtitle = "智能对白与多维提炼",
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { copyMarkdown() }) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "复制 Markdown 纪要",
                    tint = MintCyan,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // 1. Meta Info Card
            TerminalCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = Formatters.formatDetailDate(currentRecord.createdAt),
                        style = MaterialTheme.typography.titleMedium,
                        color = MintCyan,
                    )
                    StatusBadge(status = currentRecord.status)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Mode & Audio Format Specification Badges
                val isMeeting = currentRecord.isMeeting()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isMeeting) MintCyan.copy(alpha = 0.15f) else NeonGreen.copy(alpha = 0.15f))
                            .border(1.dp, if (isMeeting) MintCyan.copy(alpha = 0.4f) else NeonGreen.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = if (isMeeting) "💼 高保真会议录音" else "🌿 LifeLog 随身省流记",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isMeeting) MintCyan else NeonGreen,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CardElevated)
                            .border(1.dp, SurfaceBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "🎵 ${currentRecord.getDisplayFormatInfo()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "会话 ${currentRecord.sessionId} · 分片 #${currentRecord.chunkIndex}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${Formatters.formatFileSize(currentRecord.fileSizeBytes)} · ${currentRecord.getEstimatedBitrateDesc()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }

                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        tags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CardElevated)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricBlue,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }

            // 2. Title & Summary Card
            val isMeeting = currentRecord.isMeeting()
            val fallbackTitle = if (isMeeting) "💼 会议录音 #${currentRecord.id}" else "🌿 随身生活记录 #${currentRecord.id}"
            val displayTitle = currentRecord.title?.takeIf { it.isNotBlank() } ?: fallbackTitle

            Spacer(modifier = Modifier.height(Dimens.gapMd))
            TerminalCard {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                if (!currentRecord.summary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Dimens.gapSm))
                    Text(
                        text = currentRecord.summary!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        lineHeight = 22.sp,
                    )
                }
            }

            // 3. Multi-scene AI Re-generation Selector
            Spacer(modifier = Modifier.height(Dimens.gapMd))
            TerminalCard {
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
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = NeonGreen,
                            modifier = Modifier.size(16.dp),
                        )
                        SectionLabel("多场景 AI 提炼")
                    }
                    if (isRegenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = NeonGreen,
                            strokeWidth = 2.dp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "根据实际场景切换大模型认知模式，重新梳理要点与对白：",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PromptTemplate.entries.forEach { template ->
                        val isCurrentTarget = isRegenerating && regeneratingTemplateId == template.id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCurrentTarget) NeonGreen.copy(alpha = 0.2f) else CardElevated)
                                .border(
                                    width = 1.dp,
                                    color = if (isCurrentTarget) NeonGreen else SurfaceBorder,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable(enabled = !isRegenerating) {
                                    triggerRegenerate(template)
                                }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = template.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isCurrentTarget) NeonGreen else TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = template.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // 4. Interactive Action Items (待办事项，带复选框互动)
            if (actionItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimens.gapMd))
                val completedCount = actionItems.count { it.isDone }
                TerminalCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel("行动待办 ($completedCount/${actionItems.size})")
                        IconButton(
                            onClick = {
                                val text = actionItems.joinToString("\n") { (if (it.isDone) "☑ " else "☐ ") + it.text }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("待办事项", text))
                                Toast.makeText(context, "已复制待办清单", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "复制所有待办",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    actionItems.forEachIndexed { idx, item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val updated = actionItems.mapIndexed { i, itm ->
                                        if (i == idx) itm.copy(isDone = !itm.isDone) else itm
                                    }
                                    scope.launch(Dispatchers.IO) {
                                        repository?.updateActionItems(currentRecord.id, ActionItemModel.toJsonString(updated))
                                    }
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = if (item.isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = if (item.isDone) "已完成" else "未完成",
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

            // 5. Transcript & Dialogue (逐字转录稿 / 智能多轮对白)
            if (!currentRecord.transcription.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Dimens.gapMd))
                TerminalCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel(if (dialogueTurns.isNotEmpty()) "多人对白纪要" else "逐字转录稿")
                        if (dialogueTurns.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ElectricBlue.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "已智能推断发言人",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricBlue,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimens.gapSm))

                    if (dialogueTurns.isNotEmpty()) {
                        // 角色对白排版
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            dialogueTurns.forEachIndexed { index, turn ->
                                val isSpeakerA = turn.speaker.contains("A") || index % 2 == 0
                                val speakerColor = if (isSpeakerA) MintCyan else ElectricBlue
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CardDark)
                                        .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(speakerColor.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = turn.speaker,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = speakerColor,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = turn.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        lineHeight = 22.sp,
                                    )
                                }
                            }
                        }
                    } else {
                        // 普通文本排版
                        Text(
                            text = currentRecord.transcription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            lineHeight = 22.sp,
                        )
                    }
                }
            }

            // 6. Ask AI (针对此录音追问)
            Spacer(modifier = Modifier.height(Dimens.gapMd))
            TerminalCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.QuestionAnswer,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(16.dp),
                    )
                    SectionLabel("向 AI 提问这段录音")
                }

                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("提炼3个核心争议或要点", "各方达成了什么共识？", "有什么潜在遗漏风险？").forEach { prompt ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardElevated)
                                .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
                                .clickable(enabled = !isAskingAi) {
                                    questionInput = prompt
                                    askQuestion(prompt)
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = prompt,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = questionInput,
                        onValueChange = { questionInput = it },
                        placeholder = {
                            Text("向 AI 提问录音细节...", color = TextMuted, fontSize = 13.sp)
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CardDark,
                            unfocusedContainerColor = CardDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedIndicatorColor = MintCyan,
                            unfocusedIndicatorColor = BorderGray,
                            cursorColor = MintCyan,
                        ),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { askQuestion(questionInput) },
                        enabled = !isAskingAi && questionInput.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isAskingAi || questionInput.isBlank()) CardElevated else MintCyan),
                    ) {
                        if (isAskingAi) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = TextMuted,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送提问",
                                tint = if (questionInput.isBlank()) TextMuted else PureBlack,
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = aiAnswer != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    aiAnswer?.let { answer ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ElectricBlue.copy(alpha = 0.08f))
                                .border(1.dp, ElectricBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = ElectricBlue,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = "AI 智能解答：",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ElectricBlue,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = answer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    lineHeight = 22.sp,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.gapLg))

            // 7. Visualizer
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.gapMd),
            ) {
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .rotate(ringRotation)
                            .border(
                                width = Dimens.borderThick,
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        NeonGreen.copy(alpha = 0f),
                                        NeonGreen.copy(alpha = 0.6f),
                                        NeonGreen,
                                        NeonGreen.copy(alpha = 0f),
                                    ),
                                ),
                                shape = CircleShape,
                            ),
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    if (isPlaying) NeonGreen.copy(alpha = 0.15f) else DarkGray,
                                    DarkCard,
                                ),
                            ),
                        )
                        .border(
                            Dimens.borderThin,
                            if (isPlaying) NeonGreen.copy(alpha = 0.3f) else BorderGray,
                            CircleShape,
                        ),
                ) {
                    val percent = (sliderPosition * 100).toInt()
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.headlineLarge,
                            color = if (isPlaying) NeonGreen else TextMuted,
                        )
                        Text(
                            text = when {
                                playerError != null -> "不可用"
                                isPlaying -> "播放中"
                                else -> "已暂停"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPlaying) NeonGreenDim else TextMuted,
                        )
                    }
                }
            }

            if (playerError != null) {
                Text(
                    text = playerError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = DangerRed,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Dimens.gapMd),
                )
            }
        }

        // 8. Pinned Bottom Player Controls
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gapXs),
        ) {
            Text(
                text = Formatters.formatElapsed(
                    if (isSeeking) (sliderPosition * durationMs).toLong() else currentPositionMs,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = NeonGreen,
            )
            Text(
                text = Formatters.formatElapsed(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }

        Slider(
            value = sliderPosition,
            onValueChange = { value ->
                isSeeking = true
                sliderPosition = value
            },
            onValueChangeFinished = {
                val seekTo = (sliderPosition * durationMs).toLong()
                try {
                    opusPlayer.seekTo(seekTo)
                    currentPositionMs = seekTo
                } catch (_: Exception) {
                }
                isSeeking = false
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = NeonGreen,
                activeTrackColor = NeonGreen,
                inactiveTrackColor = DarkGray,
            ),
            enabled = playerError == null,
        )

        Spacer(modifier = Modifier.height(Dimens.gapMd))

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Dimens.gapXl),
        ) {
            IconButton(
                onClick = {
                    try {
                        val newPos = (currentPositionMs - 10000).coerceAtLeast(0)
                        opusPlayer.seekTo(newPos)
                        currentPositionMs = newPos
                        sliderPosition = (newPos.toFloat() / durationMs).coerceIn(0f, 1f)
                    } catch (_: Exception) {
                    }
                },
                enabled = playerError == null,
            ) {
                Icon(
                    imageVector = Icons.Default.Replay10,
                    contentDescription = "后退 10 秒",
                    tint = if (playerError == null) TextPrimary else TextMuted,
                    modifier = Modifier.size(32.dp),
                )
            }

            IconButton(
                onClick = {
                    if (playerError != null) return@IconButton
                    try {
                        if (isPlaying) {
                            opusPlayer.pause()
                            isPlaying = false
                        } else {
                            if (currentPositionMs >= durationMs - 100) {
                                opusPlayer.seekTo(0)
                                currentPositionMs = 0
                                sliderPosition = 0f
                            }
                            opusPlayer.start()
                            isPlaying = true
                        }
                    } catch (_: Exception) {
                    }
                },
                modifier = Modifier
                    .size(Dimens.iconButtonLg)
                    .clip(CircleShape)
                    .background(
                        if (isPlaying) NeonGreen.copy(alpha = 0.15f) else NeonGreen.copy(alpha = 0.1f),
                    )
                    .border(Dimens.borderThick, NeonGreen, CircleShape),
                enabled = playerError == null,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = NeonGreen,
                    modifier = Modifier.size(40.dp),
                )
            }

            IconButton(
                onClick = {
                    try {
                        val newPos = (currentPositionMs + 10000).coerceAtMost(durationMs)
                        opusPlayer.seekTo(newPos)
                        currentPositionMs = newPos
                        sliderPosition = (newPos.toFloat() / durationMs).coerceIn(0f, 1f)
                    } catch (_: Exception) {
                    }
                },
                enabled = playerError == null,
            ) {
                Icon(
                    imageVector = Icons.Default.Forward10,
                    contentDescription = "快进 10 秒",
                    tint = if (playerError == null) TextPrimary else TextMuted,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}
