package com.example.shuolesa.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.prefs.ProviderPreset
import com.example.shuolesa.network.ApiService
import com.example.shuolesa.theme.BgDark
import com.example.shuolesa.theme.CardDark
import com.example.shuolesa.theme.CardElevated
import com.example.shuolesa.theme.DangerRed
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.MintCyan
import com.example.shuolesa.theme.MintCyanDim
import com.example.shuolesa.theme.NeonGreen
import com.example.shuolesa.theme.SurfaceBorder
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.theme.TextSecondary
import com.example.shuolesa.ui.components.PageHeader
import com.example.shuolesa.ui.components.SectionLabel
import com.example.shuolesa.ui.components.SettingsRow
import com.example.shuolesa.ui.components.TerminalCard
import com.example.shuolesa.ui.components.TerminalOutlineButton
import com.example.shuolesa.ui.components.TerminalTextField
import com.example.shuolesa.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 现代 AI 引擎与设置配置中心：支持多 Tab 分页（录音 / 盲操 / AI引擎 / 关于），修复等高与换行排版。
 */
@Composable
fun NodeSettingsScreen(
    prefs: AppPreferences,
    permissionHelper: PermissionHelper,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val providerMode by prefs.providerMode.collectAsState(initial = ProviderPreset.STEPFUN.id)
    val baseUrl by prefs.baseUrl.collectAsState(initial = ProviderPreset.STEPFUN.defaultBaseUrl)
    val apiKey by prefs.apiKey.collectAsState(initial = AppPreferences.DEFAULT_API_KEY)
    val asrModel by prefs.asrModel.collectAsState(initial = ProviderPreset.STEPFUN.defaultAsrModel)
    val llmModel by prefs.llmModel.collectAsState(initial = ProviderPreset.STEPFUN.defaultLlmModel)
    val systemPrompt by prefs.systemPrompt.collectAsState(initial = AppPreferences.DEFAULT_SYSTEM_PROMPT)
    val keepLocalAudio by prefs.keepLocalAudio.collectAsState(initial = true)

    val hapticEnabled by prefs.hapticEnabled.collectAsState(initial = true)
    val autoResumeAfterCall by prefs.autoResumeAfterCall.collectAsState(initial = false)

    val recordingMode by prefs.recordingMode.collectAsState(initial = "lifelog")
    val lifelogBitrateKbps by prefs.lifelogBitrateKbps.collectAsState(initial = 24)
    val meetingFormat by prefs.meetingFormat.collectAsState(initial = "wav")
    val launchStrategy by prefs.launchStrategy.collectAsState(initial = "default")
    val lifelogTriggerDuration by prefs.lifelogTriggerDuration.collectAsState(initial = 2)
    val meetingTriggerDuration by prefs.meetingTriggerDuration.collectAsState(initial = 4)

    var tokenVisible by remember { mutableStateOf(false) }
    var promptExpanded by remember { mutableStateOf(false) }
    var pingStatus by remember { mutableStateOf<PingState>(PingState.Idle) }
    var a11yEnabled by remember { mutableStateOf(permissionHelper.isAccessibilityServiceEnabled()) }
    var batteryIgnored by remember { mutableStateOf(permissionHelper.isBatteryOptimizationIgnored()) }

    var selectedSettingsTab by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yEnabled = permissionHelper.isAccessibilityServiceEnabled()
                batteryIgnored = permissionHelper.isBatteryOptimizationIgnored()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .padding(horizontal = Dimens.pagePaddingH, vertical = Dimens.pagePaddingV),
    ) {
        PageHeader(
            title = "系统设置",
            subtitle = "AI 引擎、录音品质分级与无障碍盲操配置",
        )

        Spacer(modifier = Modifier.height(Dimens.gapMd))

        // Settings Top Segmented Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.cardRadius))
                .background(CardDark)
                .border(1.dp, SurfaceBorder, RoundedCornerShape(Dimens.cardRadius))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val tabs = listOf(
                Pair(0, "🎙️ 录音"),
                Pair(1, "🦯 盲操"),
                Pair(2, "🧠 AI引擎"),
                Pair(3, "ℹ️ 关于"),
            )
            tabs.forEach { (idx, title) ->
                val isSel = selectedSettingsTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) MintCyan.copy(alpha = 0.15f) else Color.Transparent)
                        .border(1.dp, if (isSel) MintCyan else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { selectedSettingsTab = idx }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSel) MintCyan else TextMuted,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.gapLg))

        // Independent scrollable content container for selected Tab
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            when (selectedSettingsTab) {
                0 -> RecordingSettingsContent(
                    launchStrategy = launchStrategy,
                    recordingMode = recordingMode,
                    lifelogBitrateKbps = lifelogBitrateKbps,
                    meetingFormat = meetingFormat,
                    keepLocalAudio = keepLocalAudio,
                    autoResumeAfterCall = autoResumeAfterCall,
                    onSetLaunchStrategy = { scope.launch { prefs.setLaunchStrategy(it) } },
                    onSetRecordingMode = { scope.launch { prefs.setRecordingMode(it) } },
                    onSetLifelogBitrateKbps = { scope.launch { prefs.setLifelogBitrateKbps(it) } },
                    onSetMeetingFormat = { scope.launch { prefs.setMeetingFormat(it) } },
                    onSetKeepLocalAudio = { scope.launch { prefs.setKeepLocalAudio(it) } },
                    onSetAutoResumeAfterCall = { scope.launch { prefs.setAutoResumeAfterCall(it) } },
                )
                1 -> AccessibilitySettingsContent(
                    hapticEnabled = hapticEnabled,
                    lifelogTriggerDuration = lifelogTriggerDuration,
                    meetingTriggerDuration = meetingTriggerDuration,
                    a11yEnabled = a11yEnabled,
                    batteryIgnored = batteryIgnored,
                    onSetHapticEnabled = { scope.launch { prefs.setHapticEnabled(it) } },
                    onSetLifelogTriggerDuration = { scope.launch { prefs.setLifelogTriggerDuration(it) } },
                    onSetMeetingTriggerDuration = { scope.launch { prefs.setMeetingTriggerDuration(it) } },
                    onOpenAccessibilitySettings = { permissionHelper.openAccessibilitySettings() },
                    onRequestIgnoreBatteryOptimizations = { permissionHelper.requestIgnoreBatteryOptimizations() },
                )
                2 -> AiEngineSettingsContent(
                    providerMode = providerMode,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    asrModel = asrModel,
                    llmModel = llmModel,
                    systemPrompt = systemPrompt,
                    tokenVisible = tokenVisible,
                    promptExpanded = promptExpanded,
                    pingStatus = pingStatus,
                    onToggleTokenVisible = { tokenVisible = !tokenVisible },
                    onTogglePromptExpanded = { promptExpanded = !promptExpanded },
                    onApplyPreset = { preset ->
                        scope.launch {
                            prefs.applyPreset(preset)
                            pingStatus = PingState.Idle
                        }
                    },
                    onSetBaseUrl = { scope.launch { prefs.setBaseUrl(it) } },
                    onSetApiKey = { scope.launch { prefs.setApiKey(it) } },
                    onSetAsrModel = { scope.launch { prefs.setAsrModel(it) } },
                    onSetLlmModel = { scope.launch { prefs.setLlmModel(it) } },
                    onSetSystemPrompt = { scope.launch { prefs.setSystemPrompt(it) } },
                    onResetPrompt = { scope.launch { prefs.setSystemPrompt(AppPreferences.DEFAULT_SYSTEM_PROMPT) } },
                    onPing = {
                        pingStatus = PingState.Loading
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                ApiService().testConnection(baseUrl, apiKey, llmModel)
                            }
                            pingStatus = if (result.isSuccess) {
                                PingState.Success
                            } else {
                                PingState.Error(result.exceptionOrNull()?.message ?: "未知错误")
                            }
                        }
                    },
                )
                3 -> AboutSettingsContent(
                    permissionHelper = permissionHelper,
                )
            }

            Spacer(modifier = Modifier.height(Dimens.pageBottomNavClearance))
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 0: 录音品质与启动策略
// -----------------------------------------------------------------------------
@Composable
private fun RecordingSettingsContent(
    launchStrategy: String,
    recordingMode: String,
    lifelogBitrateKbps: Int,
    meetingFormat: String,
    keepLocalAudio: Boolean,
    autoResumeAfterCall: Boolean,
    onSetLaunchStrategy: (String) -> Unit,
    onSetRecordingMode: (String) -> Unit,
    onSetLifelogBitrateKbps: (Int) -> Unit,
    onSetMeetingFormat: (String) -> Unit,
    onSetKeepLocalAudio: (Boolean) -> Unit,
    onSetAutoResumeAfterCall: (Boolean) -> Unit,
) {
    SectionLabel("录制启动策略与音质")
    Spacer(modifier = Modifier.height(Dimens.gapSm))

    TerminalCard {
        Text(
            text = "录制启动策略 (一级选项)",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "控制每次点击首页录音按钮时的交互行为",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Strategy Selector (Equal Height)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val isDefault = launchStrategy == "default"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .defaultMinSize(minHeight = 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDefault) MintCyan.copy(alpha = 0.15f) else CardElevated)
                    .border(1.dp, if (isDefault) MintCyan else SurfaceBorder, RoundedCornerShape(8.dp))
                    .clickable { onSetLaunchStrategy("default") }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "⚡ 固定默认模式",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDefault) MintCyan else TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "点击直接开启固定模式",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDefault) MintCyan.copy(alpha = 0.8f) else TextMuted,
                        fontSize = 10.sp,
                    )
                }
            }

            val isPrompt = launchStrategy == "prompt"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .defaultMinSize(minHeight = 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isPrompt) NeonGreen.copy(alpha = 0.15f) else CardElevated)
                    .border(1.dp, if (isPrompt) NeonGreen else SurfaceBorder, RoundedCornerShape(8.dp))
                    .clickable { onSetLaunchStrategy("prompt") }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🎯 每次单独选择",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isPrompt) NeonGreen else TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "点击弹窗自选格式",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPrompt) NeonGreen.copy(alpha = 0.8f) else TextMuted,
                        fontSize = 10.sp,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (launchStrategy == "default") {
            Text(
                text = "固定的默认录音模式",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "随身闲聊建议使用 LifeLog 极小文件，正式会议建议使用高保真",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Default Mode Selector (Equal Height)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val isLifeLog = recordingMode == "lifelog"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .defaultMinSize(minHeight = 60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isLifeLog) NeonGreen.copy(alpha = 0.15f) else CardElevated)
                        .border(1.dp, if (isLifeLog) NeonGreen else SurfaceBorder, RoundedCornerShape(8.dp))
                        .clickable { onSetRecordingMode("lifelog") }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🌿 LifeLog 模式",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isLifeLog) NeonGreen else TextPrimary,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "小文件 · 超省电省空间",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLifeLog) NeonGreen.copy(alpha = 0.8f) else TextMuted,
                            fontSize = 10.sp,
                        )
                    }
                }

                val isMeeting = recordingMode == "meeting"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .defaultMinSize(minHeight = 60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isMeeting) MintCyan.copy(alpha = 0.15f) else CardElevated)
                        .border(1.dp, if (isMeeting) MintCyan else SurfaceBorder, RoundedCornerShape(8.dp))
                        .clickable { onSetRecordingMode("meeting") }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "💼 会议模式",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isMeeting) MintCyan else TextPrimary,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "大文件 · 清晰高保真",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isMeeting) MintCyan.copy(alpha = 0.8f) else TextMuted,
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (recordingMode == "lifelog") {
                LifeLogBitrateSection(
                    lifelogBitrateKbps = lifelogBitrateKbps,
                    onSetLifelogBitrateKbps = onSetLifelogBitrateKbps,
                )
            } else {
                MeetingFormatSection(
                    meetingFormat = meetingFormat,
                    onSetMeetingFormat = onSetMeetingFormat,
                )
            }
        } else {
            // Prompt mode: allow configuring both format presets
            LifeLogBitrateSection(
                lifelogBitrateKbps = lifelogBitrateKbps,
                onSetLifelogBitrateKbps = onSetLifelogBitrateKbps,
            )

            Spacer(modifier = Modifier.height(14.dp))

            MeetingFormatSection(
                meetingFormat = meetingFormat,
                onSetMeetingFormat = onSetMeetingFormat,
            )
        }
    }

    Spacer(modifier = Modifier.height(Dimens.gapMd))

    // Storage & Audio Retention
    SettingsRow(
        label = "保留本地录音原件",
        subtitle = "识别完成后仍保留音频用于本地回放",
    ) {
        Switch(
            checked = keepLocalAudio,
            onCheckedChange = onSetKeepLocalAudio,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MintCyan,
                checkedTrackColor = MintCyanDim.copy(alpha = 0.3f),
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = CardElevated,
            ),
        )
    }

    Spacer(modifier = Modifier.height(Dimens.gapMd))

    // Phone call resume
    SettingsRow(
        label = "通话后自动续录",
        subtitle = "来电时自动暂停，挂断后恢复录制",
    ) {
        Switch(
            checked = autoResumeAfterCall,
            onCheckedChange = onSetAutoResumeAfterCall,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MintCyan,
                checkedTrackColor = MintCyanDim.copy(alpha = 0.3f),
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = CardElevated,
            ),
        )
    }
}

@Composable
private fun LifeLogBitrateSection(
    lifelogBitrateKbps: Int,
    onSetLifelogBitrateKbps: (Int) -> Unit,
) {
    Text(
        text = "🌿 LifeLog 压缩码率 (AAC 硬件编码)",
        style = MaterialTheme.typography.bodySmall,
        color = NeonGreen,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            Triple(16, "16 kbps", "极省 · ~7MB/h"),
            Triple(24, "24 kbps", "标准 · ~10MB/h"),
            Triple(32, "32 kbps", "清晰 · ~14MB/h"),
        ).forEach { (kbps, label, est) ->
            val isSel = lifelogBitrateKbps == kbps
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .defaultMinSize(minHeight = 52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSel) NeonGreen.copy(alpha = 0.15f) else CardElevated)
                    .border(1.dp, if (isSel) NeonGreen else SurfaceBorder, RoundedCornerShape(6.dp))
                    .clickable { onSetLifelogBitrateKbps(kbps) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSel) NeonGreen else TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = est,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSel) NeonGreen.copy(alpha = 0.85f) else TextMuted,
                        fontSize = 9.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeetingFormatSection(
    meetingFormat: String,
    onSetMeetingFormat: (String) -> Unit,
) {
    Text(
        text = "💼 会议录音格式与品质",
        style = MaterialTheme.typography.bodySmall,
        color = MintCyan,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            Triple("wav", "无损 WAV", "16kHz · ~115MB/h"),
            Triple("aac_64k", "高清 AAC", "64kbps · ~28MB/h"),
        ).forEach { (fmt, label, est) ->
            val isSel = meetingFormat == fmt
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .defaultMinSize(minHeight = 52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSel) MintCyan.copy(alpha = 0.15f) else CardElevated)
                    .border(1.dp, if (isSel) MintCyan else SurfaceBorder, RoundedCornerShape(6.dp))
                    .clickable { onSetMeetingFormat(fmt) }
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSel) MintCyan else TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = est,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSel) MintCyan.copy(alpha = 0.85f) else TextMuted,
                        fontSize = 9.sp,
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 1: 盲操设置与系统保活
// -----------------------------------------------------------------------------
@Composable
private fun AccessibilitySettingsContent(
    hapticEnabled: Boolean,
    lifelogTriggerDuration: Int,
    meetingTriggerDuration: Int,
    a11yEnabled: Boolean,
    batteryIgnored: Boolean,
    onSetHapticEnabled: (Boolean) -> Unit,
    onSetLifelogTriggerDuration: (Int) -> Unit,
    onSetMeetingTriggerDuration: (Int) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestIgnoreBatteryOptimizations: () -> Unit,
) {
    SectionLabel("无障碍盲操与后台守护")
    Spacer(modifier = Modifier.height(Dimens.gapSm))

    // Haptics switch
    SettingsRow(
        label = "触觉震动反馈",
        subtitle = "盲操开始/结束录音时震动脉冲提示",
    ) {
        Switch(
            checked = hapticEnabled,
            onCheckedChange = onSetHapticEnabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MintCyan,
                checkedTrackColor = MintCyanDim.copy(alpha = 0.3f),
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = CardElevated,
            ),
        )
    }

    Spacer(modifier = Modifier.height(Dimens.gapMd))

    // Dual-Stage Blind Trigger Durations
    var tempLifelogDuration by remember(lifelogTriggerDuration) { mutableFloatStateOf(lifelogTriggerDuration.toFloat()) }
    var tempMeetingDuration by remember(meetingTriggerDuration) { mutableFloatStateOf(meetingTriggerDuration.toFloat()) }

    TerminalCard {
        Text(
            text = "无障碍双阶盲操时长",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "息屏或后台下，同时按住音量 +/- 键不同时长触发不同音质",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Tactile Ladder Guide Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MintCyan.copy(alpha = 0.08f))
                .border(1.dp, MintCyan.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "🪜 振动阶梯手感说明：",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MintCyan,
                )
                Text(
                    text = "1. 按住达 ${tempLifelogDuration.toInt()} 秒：震动脉冲 1 次；松手即开启 LifeLog 格式录制。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
                Text(
                    text = "2. 持续按住达 ${tempMeetingDuration.toInt()} 秒：连震 2 次，自动开启高保真会议录制。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
                Text(
                    text = "3. 录音中再次长按 (~1.2 秒)：震动停止录制。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontSize = 11.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Stage 1 Slider: LifeLog
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "阶梯 1 · LifeLog 随身模式",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonGreen,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "轻按触发，震动 1 次后松手",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
            Text(
                text = "${tempLifelogDuration.toInt()} 秒",
                style = MaterialTheme.typography.titleMedium,
                color = NeonGreen,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = tempLifelogDuration,
            onValueChange = {
                tempLifelogDuration = it
                if (tempMeetingDuration <= it) {
                    tempMeetingDuration = (it + 1).coerceAtMost(7f)
                }
            },
            onValueChangeFinished = {
                val lifeSec = tempLifelogDuration.toInt()
                val meetSec = tempMeetingDuration.toInt()
                onSetLifelogTriggerDuration(lifeSec)
                if (meetSec <= lifeSec) {
                    onSetMeetingTriggerDuration(lifeSec + 1)
                }
            },
            valueRange = 1f..4f,
            steps = 2,
            colors = SliderDefaults.colors(
                thumbColor = NeonGreen,
                activeTrackColor = NeonGreen,
                inactiveTrackColor = CardElevated,
                activeTickColor = BgDark,
                inactiveTickColor = TextMuted,
            ),
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Stage 2 Slider: Meeting
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "阶梯 2 · 会议高保真模式",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MintCyan,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "深按不放，震动 2 次直接开启",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
            Text(
                text = "${tempMeetingDuration.toInt()} 秒",
                style = MaterialTheme.typography.titleMedium,
                color = MintCyan,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = tempMeetingDuration,
            onValueChange = {
                if (it > tempLifelogDuration) {
                    tempMeetingDuration = it
                }
            },
            onValueChangeFinished = {
                onSetMeetingTriggerDuration(tempMeetingDuration.toInt())
            },
            valueRange = 3f..7f,
            steps = 3,
            colors = SliderDefaults.colors(
                thumbColor = MintCyan,
                activeTrackColor = MintCyan,
                inactiveTrackColor = CardElevated,
                activeTickColor = BgDark,
                inactiveTickColor = TextMuted,
            ),
        )
    }

    Spacer(modifier = Modifier.height(Dimens.gapMd))

    // Accessibility Service Status Card
    TerminalCard(
        borderColor = if (a11yEnabled) MintCyan.copy(alpha = 0.35f) else DangerRed.copy(alpha = 0.35f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(Dimens.statusDot)
                    .clip(CircleShape)
                    .background(if (a11yEnabled) MintCyan else DangerRed),
            )
            Text(
                text = if (a11yEnabled) "无障碍按键监听服务已开启" else "无障碍服务未启用",
                style = MaterialTheme.typography.titleMedium,
                color = if (a11yEnabled) MintCyan else DangerRed,
                modifier = Modifier.padding(start = Dimens.gapSm),
            )
        }
        if (!a11yEnabled) {
            Spacer(modifier = Modifier.height(Dimens.gapSm))
            Text(
                text = "开启“说了啥”无障碍服务后，即可在息屏时长按音量键盲操录音",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            TextButton(onClick = onOpenAccessibilitySettings) {
                Text("前往系统设置开启", style = MaterialTheme.typography.labelLarge, color = MintCyan)
            }
        }
    }

    if (!batteryIgnored) {
        Spacer(modifier = Modifier.height(Dimens.gapMd))
        TerminalCard {
            Text(
                text = "后台电池优化白名单",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(Dimens.gapXs))
            Text(
                text = "将应用加入无限制白名单，避免息屏长时间录音被系统休眠杀死",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            TextButton(onClick = onRequestIgnoreBatteryOptimizations) {
                Text("开启无限制白名单", style = MaterialTheme.typography.labelLarge, color = MintCyan)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 2: AI 引擎与模型配置
// -----------------------------------------------------------------------------
@Composable
private fun AiEngineSettingsContent(
    providerMode: String,
    baseUrl: String,
    apiKey: String,
    asrModel: String,
    llmModel: String,
    systemPrompt: String,
    tokenVisible: Boolean,
    promptExpanded: Boolean,
    pingStatus: PingState,
    onToggleTokenVisible: () -> Unit,
    onTogglePromptExpanded: () -> Unit,
    onApplyPreset: (ProviderPreset) -> Unit,
    onSetBaseUrl: (String) -> Unit,
    onSetApiKey: (String) -> Unit,
    onSetAsrModel: (String) -> Unit,
    onSetLlmModel: (String) -> Unit,
    onSetSystemPrompt: (String) -> Unit,
    onResetPrompt: () -> Unit,
    onPing: () -> Unit,
) {
    SectionLabel("AI 提供商预设")
    Spacer(modifier = Modifier.height(Dimens.gapSm))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProviderPreset.entries.forEach { preset ->
            val isSelected = providerMode == preset.id
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Dimens.fieldRadius))
                    .background(if (isSelected) MintCyan.copy(alpha = 0.12f) else CardDark)
                    .border(1.dp, if (isSelected) MintCyan else SurfaceBorder, RoundedCornerShape(Dimens.fieldRadius))
                    .clickable { onApplyPreset(preset) }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = preset.displayName.split(" ")[0],
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MintCyan else TextSecondary,
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(Dimens.gapLg))

    SectionLabel("API 端点 (Base URL)")
    Spacer(modifier = Modifier.height(Dimens.gapSm))
    TerminalTextField(
        value = baseUrl,
        onValueChange = onSetBaseUrl,
        placeholder = "https://api.stepfun.com/v1",
    )

    Spacer(modifier = Modifier.height(Dimens.gapLg))

    SectionLabel("API Key 密钥")
    Spacer(modifier = Modifier.height(Dimens.gapSm))
    TerminalTextField(
        value = apiKey,
        onValueChange = onSetApiKey,
        placeholder = if (providerMode == ProviderPreset.LOCAL_OR_CUSTOM.id) "本地免认证时可为空" else "请输入 API Key",
        visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleTokenVisible) {
                Icon(
                    imageVector = if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (tokenVisible) "隐藏" else "显示",
                    tint = TextSecondary,
                )
            }
        },
    )

    Spacer(modifier = Modifier.height(Dimens.gapLg))

    SectionLabel("语音转写模型 (ASR)")
    Spacer(modifier = Modifier.height(Dimens.gapSm))
    TerminalTextField(
        value = asrModel,
        onValueChange = onSetAsrModel,
        placeholder = "stepaudio-2.5-asr",
    )
    Spacer(modifier = Modifier.height(6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val asrSuggestions = listOf("stepaudio-2.5-asr", "FunAudioLLM/SenseVoiceSmall", "whisper-1")
        items(asrSuggestions) { suggestion ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CardElevated)
                    .clickable { onSetAsrModel(suggestion) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(text = suggestion, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
    }

    Spacer(modifier = Modifier.height(Dimens.gapLg))

    SectionLabel("智能整理模型 (LLM)")
    Spacer(modifier = Modifier.height(Dimens.gapSm))
    TerminalTextField(
        value = llmModel,
        onValueChange = onSetLlmModel,
        placeholder = "step-1-8k",
    )
    Spacer(modifier = Modifier.height(6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val llmSuggestions = listOf("step-router-v1", "step-1-8k", "step-2-16k", "deepseek-ai/DeepSeek-V3")
        items(llmSuggestions) { suggestion ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CardElevated)
                    .clickable { onSetLlmModel(suggestion) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(text = suggestion, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
    }

    Spacer(modifier = Modifier.height(Dimens.gapLg))

    // System Prompt Customization
    TerminalCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTogglePromptExpanded() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = "提炼提示词 (Prompt)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(text = if (promptExpanded) "点击收起" else "点击展开自定义 Prompt", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            IconButton(onClick = onResetPrompt) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "恢复默认",
                    tint = MintCyan,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        AnimatedVisibility(visible = promptExpanded) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                TerminalTextField(
                    value = systemPrompt,
                    onValueChange = onSetSystemPrompt,
                    singleLine = false,
                    placeholder = "输入定制的 System Prompt",
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(Dimens.gapLg))

    // Ping Connection Button
    val pingColor by animateColorAsState(
        targetValue = when (pingStatus) {
            is PingState.Success -> MintCyan
            is PingState.Error -> DangerRed
            else -> MintCyan
        },
        animationSpec = tween(300),
        label = "pingColor",
    )
    val pingLabel = when (pingStatus) {
        is PingState.Success -> "测试通过 · 节点响应正常"
        is PingState.Error -> "测试失败"
        is PingState.Loading -> "正在连接节点..."
        else -> "测试连接"
    }

    TerminalOutlineButton(
        text = pingLabel,
        onClick = onPing,
        modifier = Modifier.fillMaxWidth(),
        enabled = pingStatus !is PingState.Loading,
        loading = pingStatus is PingState.Loading,
        color = pingColor,
        selected = true,
    )

    if (pingStatus is PingState.Error) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = pingStatus.message,
            style = MaterialTheme.typography.bodySmall,
            color = DangerRed,
        )
    }
}

// -----------------------------------------------------------------------------
// TAB 3: 关于应用与全局系统状态
// -----------------------------------------------------------------------------
@Composable
private fun AboutSettingsContent(
    permissionHelper: PermissionHelper,
) {
    SectionLabel("关于应用与设计")
    Spacer(modifier = Modifier.height(Dimens.gapSm))

    TerminalCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "说了啥 · ShuoLeSha",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(Dimens.gapXs))
                Text(
                    text = "随身 AI 录音卡片 · 极客生产力工具",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MintCyan.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "v2.1.1",
                    style = MaterialTheme.typography.labelSmall,
                    color = MintCyan,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(modifier = Modifier.height(Dimens.gapSm))
        Text(
            text = "✨ 核心特性：\n• 🌿 LifeLog 极小文件 / 💼 会议高保真音频分级\n• 4 栏全功能工作台（记忆流 · 生活手记 · 待办 · 设置）\n• 方便快捷的设置 Tab 分页分类交互\n• 无障碍双阶触觉长按盲操（单脉冲/双脉冲自选手感）\n• 录制启动策略自由切换（固定默认 / 每次单独选择）\n• 全局交互式待办勾选闭环与 Markdown 一键导出",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            lineHeight = 20.sp,
        )
    }

    Spacer(modifier = Modifier.height(Dimens.gapMd))

    SectionLabel("系统运行与权限状态")
    Spacer(modifier = Modifier.height(Dimens.gapSm))

    TerminalCard {
        val hasMic = permissionHelper.hasPermission(Manifest.permission.RECORD_AUDIO)
        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionHelper.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else true
        val hasA11y = permissionHelper.isAccessibilityServiceEnabled()
        val hasBattery = permissionHelper.isBatteryOptimizationIgnored()

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PermissionStatusRow(name = "麦克风录音权限", isGranted = hasMic)
            PermissionStatusRow(name = "无障碍盲操监听服务", isGranted = hasA11y)
            PermissionStatusRow(name = "后台无限制电池优化", isGranted = hasBattery)
            PermissionStatusRow(name = "前台通知与磁贴权限", isGranted = hasNotif)
        }
    }

    Spacer(modifier = Modifier.height(Dimens.gapMd))

    SectionLabel("快捷操作秘籍")
    Spacer(modifier = Modifier.height(Dimens.gapSm))

    TerminalCard {
        Text(
            text = "💡 操作技巧：\n" +
                "• 盲操录音：熄屏或锁屏下，同时长按音量 +/- 键。震动 1 次松手录 LifeLog 闲聊，持续按住连震 2 次自动开高保真会议；\n" +
                "• 停止录音：录音过程中再次长按音量双键约 1.2 秒，震动即停止；\n" +
                "• 桌面快捷方式：长按桌面“说了啥”图标可快速启动录音；\n" +
                "• 快速导出：在“生活手记”或录音详情页顶部点击复制，直接获取排版完备的 Markdown 笔记。",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun PermissionStatusRow(name: String, isGranted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (isGranted) NeonGreen else DangerRed,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = if (isGranted) "正常" else "待授权",
                style = MaterialTheme.typography.labelSmall,
                color = if (isGranted) NeonGreen else DangerRed,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private sealed class PingState {
    data object Idle : PingState()
    data object Loading : PingState()
    data object Success : PingState()
    data class Error(val message: String) : PingState()
}
