package com.example.shuolesa.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.QuestionAnswer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.shuolesa.audio.OpusPlayer
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.model.ActionItemModel
import com.example.shuolesa.data.model.PromptTemplate
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.repository.AudioRepository
import com.example.shuolesa.network.ApiService
import com.example.shuolesa.theme.Accent
import com.example.shuolesa.theme.AccentOn
import com.example.shuolesa.theme.BgDark
import com.example.shuolesa.theme.CardDark
import com.example.shuolesa.theme.CardElevated
import com.example.shuolesa.theme.DangerRed
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.ModeMeeting
import com.example.shuolesa.theme.SurfaceBorder
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.theme.TextSecondary
import com.example.shuolesa.theme.recordRoleColor
import com.example.shuolesa.ui.components.ModeBadge
import com.example.shuolesa.ui.components.PageHeader
import com.example.shuolesa.ui.components.SectionLabel
import com.example.shuolesa.ui.components.StatusBadge
import com.example.shuolesa.ui.components.TagChip
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
        if (spk.isNotEmpty() && cnt.isNotEmpty()) DialogueTurn(spk, cnt) else null
    }
}

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

    val liveRecordState by repository?.observeRecordById(record.id)?.collectAsState(initial = record)
        ?: remember { mutableStateOf(record) }
    val currentRecord = liveRecordState ?: record
    val isDigest = currentRecord.isDailyLifeLogSummary()
    val isMeeting = currentRecord.isMeeting()
    val accent = recordRoleColor(isDigest, isMeeting)

    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(currentRecord.durationMs.coerceAtLeast(1L)) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    val fileExists = remember(currentRecord.filePath) {
        currentRecord.filePath.isNotBlank() && File(currentRecord.filePath).exists()
    }
    val showPlayer = fileExists && !isDigest
    var playerError by remember(currentRecord.filePath) {
        mutableStateOf(if (!fileExists && !isDigest) "本地音频已清理，仅保留纪要" else null)
    }

    var isRegenerating by remember { mutableStateOf(false) }
    var regeneratingTemplateId by remember { mutableStateOf<String?>(null) }
    var questionInput by remember { mutableStateOf("") }
    var aiAnswer by remember { mutableStateOf<String?>(null) }
    var isAskingAi by remember { mutableStateOf(false) }
    var toolsExpanded by remember { mutableStateOf(false) }
    var metaExpanded by remember { mutableStateOf(false) }

    val opusPlayer = remember { OpusPlayer(context) }

    BackHandler(enabled = true) {
        try { opusPlayer.stop() } catch (_: Exception) {}
        onBack()
    }

    DisposableEffect(currentRecord.filePath) {
        if (!showPlayer) {
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
            opusPlayer.onError = { err -> playerError = err }

            val success = opusPlayer.prepare(currentRecord.filePath)
            if (success) {
                durationMs = opusPlayer.getDurationMs().coerceAtLeast(1L)
            }
            onDispose { opusPlayer.release() }
        }
    }

    val actionItems = remember(currentRecord.actionItems) {
        ActionItemModel.fromJsonString(currentRecord.actionItems)
    }
    val tags = remember(currentRecord.tags) {
        val list = mutableListOf<String>()
        if (!currentRecord.tags.isNullOrBlank()) {
            try {
                val json = JSONArray(currentRecord.tags)
                for (i in 0 until json.length()) list.add(json.getString(i))
            } catch (_: Exception) {
                currentRecord.tags.split(",", " ").filter { it.isNotBlank() }.forEach { list.add(it) }
            }
        }
        list.filter { !it.contains("LifeLog") && !it.contains("每日复盘") && !it.contains("会议") }
    }
    val dialogueTurns = remember(currentRecord.transcription) {
        parseDialogueTurns(currentRecord.transcription ?: "")
    }

    val copyMarkdown = {
        val md = buildString {
            appendLine("# ${currentRecord.title ?: "语音记录"}")
            appendLine("- **时间**：${Formatters.formatDetailDate(currentRecord.createdAt)}")
            appendLine("- **时长**：${Formatters.formatDuration(currentRecord.durationMs)}")
            if (tags.isNotEmpty()) appendLine("- **标签**：${tags.joinToString(" ") { "#$it" }}")
            appendLine()
            appendLine("## 核心要点")
            appendLine(currentRecord.summary ?: "无")
            appendLine()
            if (actionItems.isNotEmpty()) {
                appendLine("## 行动待办")
                actionItems.forEach { item ->
                    appendLine("- ${if (item.isDone) "[x]" else "[ ]"} ${item.text}")
                }
                appendLine()
            }
            appendLine("## 转录稿")
            appendLine(currentRecord.transcription ?: "无转录文本")
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("说了啥纪要", md))
        Toast.makeText(context, "已复制 Markdown 纪要", Toast.LENGTH_SHORT).show()
    }

    val triggerRegenerate = { template: PromptTemplate ->
        val textToProcess = currentRecord.transcription?.takeIf { it.isNotBlank() }
            ?: currentRecord.summary
            ?: ""
        if (textToProcess.isBlank()) {
            Toast.makeText(context, "没有可供提炼的文字", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch(Dispatchers.IO) {
                isRegenerating = true
                regeneratingTemplateId = template.id
                try {
                    val notes = ApiService().generateStructuredNotes(
                        baseUrl = prefs?.getBaseUrlSync() ?: "",
                        apiKey = prefs?.getApiKeySync() ?: "",
                        llmModel = prefs?.getLlmModelSync() ?: "",
                        systemPrompt = template.systemPrompt,
                        transcription = textToProcess,
                    ).getOrNull()
                    if (notes != null) {
                        repository?.markProcessedStructured(
                            id = currentRecord.id,
                            title = notes.title,
                            summary = notes.summary,
                            actionItems = ActionItemModel.toJsonString(notes.actionItems),
                            tags = JSONArray(notes.tags).toString(),
                            transcription = notes.structuredTranscript?.takeIf { it.isNotBlank() }
                                ?: currentRecord.transcription,
                            agentResult = notes.rawJson,
                        )
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "已按「${template.title}」重新提炼", Toast.LENGTH_SHORT).show()
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

    val askQuestion = { q: String ->
        val question = q.trim()
        if (question.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                isAskingAi = true
                aiAnswer = null
                try {
                    val res = ApiService().askQuestionAboutNote(
                        baseUrl = prefs?.getBaseUrlSync() ?: "",
                        apiKey = prefs?.getApiKeySync() ?: "",
                        llmModel = prefs?.getLlmModelSync() ?: "",
                        transcription = currentRecord.transcription ?: currentRecord.summary ?: "",
                        question = question,
                    )
                    withContext(Dispatchers.Main) {
                        aiAnswer = res.getOrElse { "提问遇到异常: ${it.message}" }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { aiAnswer = "提问异常: ${e.message}" }
                } finally {
                    isAskingAi = false
                }
            }
        }
    }

    val displayTitle = currentRecord.title?.takeIf { it.isNotBlank() }
        ?: if (isMeeting) "会议录音" else if (isDigest) "每日复盘" else "随身记录"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = Dimens.pagePaddingH),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            PageHeader(
                title = if (isDigest) "复盘" else "纪要",
                modifier = Modifier.weight(1f),
                onBack = {
                    try { opusPlayer.stop() } catch (_: Exception) {}
                    onBack()
                },
                trailing = {
                    IconButton(onClick = { copyMarkdown() }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制 Markdown 纪要",
                            tint = Accent,
                        )
                    }
                },
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            TerminalCard(borderColor = accent.copy(alpha = 0.3f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = Formatters.formatDetailDate(currentRecord.createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                    StatusBadge(status = currentRecord.status)
                }
                Spacer(modifier = Modifier.height(Dimens.gapSm))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModeBadge(isMeeting = isMeeting, isDigest = isDigest)
                    if (!isDigest) {
                        Text(
                            text = Formatters.formatDuration(currentRecord.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.gapMd))
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                if (!currentRecord.summary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Dimens.gapSm))
                    Text(
                        text = currentRecord.summary!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }
                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Dimens.gapSm))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                        verticalArrangement = Arrangement.spacedBy(Dimens.gapXs),
                    ) {
                        tags.forEach { TagChip(tag = it, color = accent) }
                    }
                }
            }

            if (actionItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimens.gapMd))
                val completedCount = actionItems.count { it.isDone }
                TerminalCard {
                    SectionLabel("待办 ($completedCount/${actionItems.size})")
                    Spacer(modifier = Modifier.height(Dimens.gapSm))
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
                                        repository?.updateActionItems(
                                            currentRecord.id,
                                            ActionItemModel.toJsonString(updated),
                                        )
                                    }
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = if (item.isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (item.isDone) Accent else TextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                                ),
                                color = if (item.isDone) TextMuted else TextPrimary,
                            )
                        }
                    }
                }
            }

            if (!currentRecord.transcription.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Dimens.gapMd))
                TerminalCard {
                    SectionLabel(if (dialogueTurns.isNotEmpty()) "对白" else "转录稿")
                    Spacer(modifier = Modifier.height(Dimens.gapSm))
                    if (dialogueTurns.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.gapSm)) {
                            dialogueTurns.forEachIndexed { index, turn ->
                                val speakerColor = if (index % 2 == 0) Accent else ModeMeeting
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(Dimens.fieldRadius))
                                        .background(CardElevated)
                                        .padding(10.dp),
                                ) {
                                    Text(
                                        text = turn.speaker,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = speakerColor,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(modifier = Modifier.height(Dimens.gapXs))
                                    Text(
                                        text = turn.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = currentRecord.transcription ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.gapMd))
            TerminalCard(contentPadding = false) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { toolsExpanded = !toolsExpanded }
                        .padding(Dimens.cardPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(16.dp),
                        )
                        SectionLabel("AI 工具")
                    }
                    Icon(
                        imageVector = if (toolsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextMuted,
                    )
                }
                AnimatedVisibility(visible = toolsExpanded) {
                    Column(modifier = Modifier.padding(start = Dimens.cardPadding, end = Dimens.cardPadding, bottom = Dimens.cardPadding)) {
                        if (!isDigest) {
                        Text(
                            text = "按场景重新提炼",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                        )
                        Spacer(modifier = Modifier.height(Dimens.gapSm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                        ) {
                            PromptTemplate.entries.forEach { template ->
                                val active = isRegenerating && regeneratingTemplateId == template.id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(Dimens.fieldRadius))
                                        .background(if (active) Accent.copy(alpha = 0.2f) else CardElevated)
                                        .border(
                                            Dimens.borderThin,
                                            if (active) Accent else SurfaceBorder,
                                            RoundedCornerShape(Dimens.fieldRadius),
                                        )
                                        .clickable(enabled = !isRegenerating) { triggerRegenerate(template) }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = template.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) Accent else TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                        }

                        Spacer(modifier = Modifier.height(Dimens.gapMd))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.QuestionAnswer,
                                contentDescription = null,
                                tint = ModeMeeting,
                                modifier = Modifier.size(16.dp),
                            )
                            SectionLabel(if (isDigest) "追问这篇复盘" else "追问这段录音")
                        }
                        Spacer(modifier = Modifier.height(Dimens.gapSm))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                            verticalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                        ) {
                            listOf("核心要点是什么？", "达成了什么共识？", "有什么遗漏风险？").forEach { prompt ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CardElevated)
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
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(Dimens.gapSm))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedTextField(
                                value = questionInput,
                                onValueChange = { questionInput = it },
                                placeholder = { Text("提问录音细节…", color = TextMuted) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = CardDark,
                                    unfocusedContainerColor = CardDark,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedIndicatorColor = Accent,
                                    unfocusedIndicatorColor = SurfaceBorder,
                                    cursorColor = Accent,
                                ),
                            )
                            Spacer(modifier = Modifier.width(Dimens.gapSm))
                            IconButton(
                                onClick = { askQuestion(questionInput) },
                                enabled = !isAskingAi && questionInput.isNotBlank(),
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(Dimens.fieldRadius))
                                    .background(if (isAskingAi || questionInput.isBlank()) CardElevated else Accent),
                            ) {
                                if (isAskingAi) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = TextMuted,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "发送提问",
                                        tint = if (questionInput.isBlank()) TextMuted else AccentOn,
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(visible = aiAnswer != null, enter = fadeIn(), exit = fadeOut()) {
                            aiAnswer?.let { answer ->
                                Spacer(modifier = Modifier.height(Dimens.gapSm))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(Dimens.fieldRadius))
                                        .background(ModeMeeting.copy(alpha = 0.08f))
                                        .padding(12.dp),
                                ) {
                                    Text(
                                        text = answer,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.gapMd))
            TerminalCard(contentPadding = false) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { metaExpanded = !metaExpanded }
                        .padding(Dimens.cardPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("技术信息")
                    Icon(
                        imageVector = if (metaExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextMuted,
                    )
                }
                AnimatedVisibility(visible = metaExpanded) {
                    Column(modifier = Modifier.padding(start = Dimens.cardPadding, end = Dimens.cardPadding, bottom = Dimens.cardPadding)) {
                        Text(
                            text = "${currentRecord.getDisplayFormatInfo()} · ${Formatters.formatFileSize(currentRecord.fileSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(Dimens.gapXs))
                        Text(
                            text = "会话 ${currentRecord.sessionId} · 分片 #${currentRecord.chunkIndex}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.gapLg))
        }

        if (showPlayer) {
            if (playerError != null) {
                Text(
                    text = playerError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = DangerRed,
                    modifier = Modifier.padding(bottom = Dimens.gapSm),
                )
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = Formatters.formatElapsed(
                        if (isSeeking) (sliderPosition * durationMs).toLong() else currentPositionMs,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = Accent,
                )
                Text(
                    text = Formatters.formatElapsed(durationMs),
                    style = MaterialTheme.typography.labelSmall,
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
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = CardElevated,
                ),
                enabled = playerError == null,
            )
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.gapMd),
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
                    Icon(Icons.Default.Replay10, contentDescription = "后退 10 秒", tint = TextPrimary, modifier = Modifier.size(28.dp))
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
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Accent.copy(alpha = 0.16f))
                        .border(Dimens.borderThick, Accent, CircleShape),
                    enabled = playerError == null,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = Accent,
                        modifier = Modifier.size(32.dp),
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
                    Icon(Icons.Default.Forward10, contentDescription = "快进 10 秒", tint = TextPrimary, modifier = Modifier.size(28.dp))
                }
            }
        } else if (isDigest) {
            Spacer(modifier = Modifier.height(Dimens.gapMd))
        }
    }
}
