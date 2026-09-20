package com.example.shuolesa.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.shuolesa.theme.Accent
import com.example.shuolesa.theme.ModeCasual
import com.example.shuolesa.theme.ModeMeeting
import com.example.shuolesa.theme.StatusUploaded
import com.example.shuolesa.theme.SurfaceBorder
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.theme.TextSecondary
import com.example.shuolesa.ui.components.PageHeader
import com.example.shuolesa.ui.components.SectionLabel
import com.example.shuolesa.ui.components.SelectableTile
import com.example.shuolesa.ui.components.SettingsRow
import com.example.shuolesa.ui.components.TerminalCard
import com.example.shuolesa.ui.components.TerminalOutlineButton
import com.example.shuolesa.ui.components.TerminalTextField
import com.example.shuolesa.util.PermissionHelper
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.shuolesa.data.db.AppDatabase
import com.example.shuolesa.data.repository.AudioRepository
import com.example.shuolesa.network.UploadWorker
import com.example.shuolesa.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 二级设置页：录音与音质 / 盲操与保活 / AI 引擎 / 诊断与关于。
 */
@Composable
fun NodeSettingsScreen(
    prefs: AppPreferences,
    permissionHelper: PermissionHelper,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val providerMode by prefs.providerMode.collectAsState(initial = ProviderPreset.STEPFUN.id)
    val baseUrl by prefs.baseUrl.collectAsState(initial = ProviderPreset.STEPFUN.defaultBaseUrl)
    val apiKey by prefs.apiKey.collectAsState(initial = "")
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        a11yEnabled = permissionHelper.isAccessibilityServiceEnabled()
        batteryIgnored = permissionHelper.isBatteryOptimizationIgnored()
    }

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

    val themeMode by prefs.themeMode.collectAsState(initial = "light")

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = Dimens.pagePaddingH, vertical = Dimens.pagePaddingV),
    ) {
        PageHeader(
            title = "设置",
            subtitle = "权限、信任、录音与引擎",
            onBack = onBack,
        )

        Spacer(modifier = Modifier.height(Dimens.gapMd))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // —— 数据与信任（v3.2）——
            DataTrustSettingsContent(
                baseUrl = baseUrl,
                providerMode = providerMode,
                asrModel = asrModel,
                llmModel = llmModel,
                keepLocalAudio = keepLocalAudio,
                onSetKeepLocalAudio = { scope.launch { prefs.setKeepLocalAudio(it) } },
                permissionHelper = permissionHelper,
                onRequestRuntimePermissions = {
                    permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS)
                },
            )

            Spacer(modifier = Modifier.height(Dimens.gapXl))

            SectionLabel("外观")
            Spacer(modifier = Modifier.height(Dimens.gapSm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
            ) {
                SelectableTile(
                    title = "浅色",
                    subtitle = "奶油纸",
                    selected = themeMode == "light",
                    onClick = { scope.launch { prefs.setThemeMode("light") } },
                    modifier = Modifier.weight(1f),
                )
                SelectableTile(
                    title = "深色",
                    subtitle = "墨色",
                    selected = themeMode == "dark",
                    onClick = { scope.launch { prefs.setThemeMode("dark") } },
                    modifier = Modifier.weight(1f),
                )
                SelectableTile(
                    title = "系统",
                    subtitle = "跟随",
                    selected = themeMode == "system",
                    onClick = { scope.launch { prefs.setThemeMode("system") } },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(Dimens.gapXl))

            RecordingSettingsContent(
                launchStrategy = launchStrategy,
                recordingMode = recordingMode,
                lifelogBitrateKbps = lifelogBitrateKbps,
                meetingFormat = meetingFormat,
                autoResumeAfterCall = autoResumeAfterCall,
                onSetLaunchStrategy = { scope.launch { prefs.setLaunchStrategy(it) } },
                onSetRecordingMode = { scope.launch { prefs.setRecordingMode(it) } },
                onSetLifelogBitrateKbps = { scope.launch { prefs.setLifelogBitrateKbps(it) } },
                onSetMeetingFormat = { scope.launch { prefs.setMeetingFormat(it) } },
                onSetAutoResumeAfterCall = { scope.launch { prefs.setAutoResumeAfterCall(it) } },
            )

            Spacer(modifier = Modifier.height(Dimens.gapXl))

            AccessibilitySettingsContent(
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

            Spacer(modifier = Modifier.height(Dimens.gapXl))

            AiEngineSettingsContent(
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

            Spacer(modifier = Modifier.height(Dimens.gapXl))

            AboutSettingsContent(
                permissionHelper = permissionHelper,
                onRequestRuntimePermissions = {
                    permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS)
                },
                onOpenAppSettings = { permissionHelper.openAppSettings() },
            )

            Spacer(modifier = Modifier.height(Dimens.gapXl))
        }
    }
}

// -----------------------------------------------------------------------------
// 数据与信任（v3.2）
// -----------------------------------------------------------------------------
@Composable
private fun DataTrustSettingsContent(
    baseUrl: String,
    providerMode: String,
    asrModel: String,
    llmModel: String,
    keepLocalAudio: Boolean,
    onSetKeepLocalAudio: (Boolean) -> Unit,
    permissionHelper: PermissionHelper,
    onRequestRuntimePermissions: () -> Unit,
) {
    SectionLabel("数据与信任")
    Spacer(modifier = Modifier.height(Dimens.gapSm))

    TerminalCard(borderColor = ModeCasual.copy(alpha = 0.25f)) {
        Text(
            text = "只手动开麦",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(Dimens.gapXs))
        Text(
            text = "本应用不会常开麦克风。只有你点底栏录音键、桌面快捷方式/磁贴，或主动使用音量盲操手势时才会开麦。",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            lineHeight = 18.sp,
        )
    }

    Spacer(modifier = Modifier.height(Dimens.gapMd))

    TerminalCard {
        Text(
            text = "当前引擎",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(Dimens.gapXs))
        Text(
            text = "供应商：$providerMode",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Text(
            text = "Base URL：${baseUrl.ifBlank { "（未设置）" }}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Text(
            text = "ASR：$asrModel",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        Text(
            text = "LLM：$llmModel",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        Spacer(modifier = Modifier.height(Dimens.gapXs))
        Text(
            text = "可在下方「AI 引擎」分组修改；密钥仅保存在本机，默认不内置任何真实 Key。",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
    }

    Spacer(modifier = Modifier.height(Dimens.gapMd))

    SettingsRow(
        label = "保留本地录音原件",
        subtitle = "识别完成后仍保留音频用于本地回放；关闭则更偏云端结果、节省空间",
    ) {
        Switch(
            checked = keepLocalAudio,
            onCheckedChange = onSetKeepLocalAudio,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Accent,
                checkedTrackColor = Accent.copy(alpha = 0.3f),
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = CardElevated,
            ),
        )
    }

    Spacer(modifier = Modifier.height(Dimens.gapMd))

    TerminalCard {
        Text(
            text = "权限（可点申请）",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(Dimens.gapSm))
        val hasMic = permissionHelper.hasPermission(Manifest.permission.RECORD_AUDIO)
        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionHelper.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else true
        PermissionStatusRow(
            name = "麦克风",
            isGranted = hasMic,
            onClick = onRequestRuntimePermissions,
        )
        Spacer(modifier = Modifier.height(6.dp))
        PermissionStatusRow(
            name = "通知",
            isGranted = hasNotif,
            onClick = onRequestRuntimePermissions,
        )
        Spacer(modifier = Modifier.height(6.dp))
        PermissionStatusRow(
            name = "无障碍盲操",
            isGranted = permissionHelper.isAccessibilityServiceEnabled(),
            onClick = { permissionHelper.openAccessibilitySettings() },
        )
        Spacer(modifier = Modifier.height(6.dp))
        PermissionStatusRow(
            name = "忽略电池优化",
            isGranted = permissionHelper.isBatteryOptimizationIgnored(),
            onClick = { permissionHelper.requestIgnoreBatteryOptimizations() },
        )
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
    autoResumeAfterCall: Boolean,
    onSetLaunchStrategy: (String) -> Unit,
    onSetRecordingMode: (String) -> Unit,
    onSetLifelogBitrateKbps: (Int) -> Unit,
    onSetMeetingFormat: (String) -> Unit,
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
        Spacer(modifier = Modifier.height(Dimens.gapSm))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
        ) {
            SelectableTile(
                title = "固定默认",
                subtitle = "点击直接开启固定模式",
                selected = launchStrategy == "default",
                onClick = { onSetLaunchStrategy("default") },
                accent = Accent,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            SelectableTile(
                title = "每次选择",
                subtitle = "点击弹窗自选格式",
                selected = launchStrategy == "prompt",
                onClick = { onSetLaunchStrategy("prompt") },
                accent = ModeMeeting,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
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
                text = "日常用随身省流，正式场合用会议高保真",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(Dimens.gapSm))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
            ) {
                SelectableTile(
                    title = "随身",
                    subtitle = "小文件 · 省电省空间",
                    selected = recordingMode == "lifelog",
                    onClick = { onSetRecordingMode("lifelog") },
                    accent = ModeCasual,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                SelectableTile(
                    title = "会议",
                    subtitle = "大文件 · 清晰高保真",
                    selected = recordingMode == "meeting",
                    onClick = { onSetRecordingMode("meeting") },
                    accent = ModeMeeting,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
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

    // 原件保留开关已上移到「数据与信任」，避免重复设置
    Text(
        text = "本地原件是否保留，请在上方「数据与信任」中调整。",
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
    )

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
                checkedThumbColor = Accent,
                checkedTrackColor = Accent.copy(alpha = 0.3f),
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
        text = "随身压缩码率 (AAC)",
        style = MaterialTheme.typography.bodySmall,
        color = Accent,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(Dimens.gapXs))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
    ) {
        listOf(
            Triple(16, "16k 极省", "约 7MB/h"),
            Triple(24, "24k 标准", "约 10MB/h"),
            Triple(32, "32k 清晰", "约 14MB/h"),
        ).forEach { (kbps, label, est) ->
            SelectableTile(
                title = label,
                subtitle = est,
                selected = lifelogBitrateKbps == kbps,
                onClick = { onSetLifelogBitrateKbps(kbps) },
                accent = ModeCasual,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun MeetingFormatSection(
    meetingFormat: String,
    onSetMeetingFormat: (String) -> Unit,
) {
    Text(
        text = "会议录音格式",
        style = MaterialTheme.typography.bodySmall,
        color = ModeMeeting,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(Dimens.gapXs))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
    ) {
        listOf(
            Triple("wav", "无损 WAV", "16kHz · 115MB/h"),
            Triple("aac_64k", "高清 AAC", "64kbps · 28MB/h"),
        ).forEach { (fmt, label, est) ->
            SelectableTile(
                title = label,
                subtitle = est,
                selected = meetingFormat == fmt,
                onClick = { onSetMeetingFormat(fmt) },
                accent = ModeMeeting,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
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
                checkedThumbColor = Accent,
                checkedTrackColor = Accent.copy(alpha = 0.3f),
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
            text = "息屏或后台下，同时按住音量 + 和 - 不同时长，触发随身或会议录音",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Tactile Ladder Guide Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Accent.copy(alpha = 0.08f))
                .border(1.dp, Accent.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "🪜 振动阶梯手感说明：",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Accent,
                )
                Text(
                    text = "1. 同时按住达 ${tempLifelogDuration.toInt()} 秒：震动脉冲 1 次；松手即开启随身录制。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text(
                    text = "2. 持续按住达 ${tempMeetingDuration.toInt()} 秒：连震 2 次，自动开启高保真会议录制。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text(
                    text = "3. 录音中再次同时按住约 1.2 秒：震动停止录制。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
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
                    text = "阶梯 1 · 随身",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ModeCasual,
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
                color = ModeCasual,
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
                thumbColor = ModeCasual,
                activeTrackColor = ModeCasual,
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
                    text = "阶梯 2 · 会议",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ModeMeeting,
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
                color = ModeMeeting,
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
                thumbColor = ModeMeeting,
                activeTrackColor = ModeMeeting,
                inactiveTrackColor = CardElevated,
                activeTickColor = BgDark,
                inactiveTickColor = TextMuted,
            ),
        )
    }

    Spacer(modifier = Modifier.height(Dimens.gapMd))

    // Accessibility Service Status Card
    TerminalCard(
        borderColor = if (a11yEnabled) Accent.copy(alpha = 0.35f) else DangerRed.copy(alpha = 0.35f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(Dimens.statusDot)
                    .clip(CircleShape)
                    .background(if (a11yEnabled) Accent else DangerRed),
            )
            Text(
                text = if (a11yEnabled) "无障碍按键监听服务已开启" else "无障碍服务未启用",
                style = MaterialTheme.typography.titleMedium,
                color = if (a11yEnabled) Accent else DangerRed,
                modifier = Modifier.padding(start = Dimens.gapSm),
            )
        }
        if (!a11yEnabled) {
            Spacer(modifier = Modifier.height(Dimens.gapSm))
            Text(
                text = "开启“说了啥”无障碍服务后，即可在息屏时同时按住音量 + 和 - 盲操录音",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            TextButton(onClick = onOpenAccessibilitySettings) {
                Text("前往系统设置开启", style = MaterialTheme.typography.labelLarge, color = Accent)
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
                Text("开启无限制白名单", style = MaterialTheme.typography.labelLarge, color = Accent)
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
                    .background(if (isSelected) Accent.copy(alpha = 0.12f) else CardDark)
                    .border(1.dp, if (isSelected) Accent else SurfaceBorder, RoundedCornerShape(Dimens.fieldRadius))
                    .clickable { onApplyPreset(preset) }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = preset.displayName.split(" ")[0],
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Accent else TextSecondary,
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
                    tint = Accent,
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
            is PingState.Success -> Accent
            is PingState.Error -> DangerRed
            else -> Accent
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
    onRequestRuntimePermissions: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var isRetrying by remember { mutableStateOf(false) }

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
                    .background(Accent.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "v3.2.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = Accent,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(modifier = Modifier.height(Dimens.gapSm))
        Text(
            text = "核心能力：\n• 记录 / 今日 / 待办 三栏工作台，录音键常驻底栏\n• 随身省流与会议高保真两档音质\n• 无障碍双阶音量键盲操\n• AI 转写提炼、待办闭环、Markdown 导出\n• 运行诊断与日志一键导出 / 分享",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            lineHeight = 20.sp,
        )
    }

    Spacer(modifier = Modifier.height(Dimens.gapMd))

    SectionLabel("运行诊断与日志导出")
    Spacer(modifier = Modifier.height(Dimens.gapSm))

    TerminalCard {
        Text(
            text = "运行诊断与日志导出",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "当遇到录音卡在“排队中”、网络受限或识别失败时，可在此导出完整诊断日志或立即发起即时重试。",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            lineHeight = 18.sp,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Diagnostic Actions Row: Copy Report & Share Log File
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TerminalOutlineButton(
                text = if (isExporting) "正在提取..." else "复制诊断",
                onClick = {
                    if (!isExporting) {
                        isExporting = true
                        scope.launch {
                            try {
                                AppLogger.copyDiagnosticReportToClipboard(context)
                            } catch (e: Exception) {
                                Toast.makeText(context, "复制失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isExporting = false
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )

            TerminalOutlineButton(
                text = "导出并分享",
                onClick = {
                    scope.launch {
                        try {
                            AppLogger.shareDiagnosticReport(context)
                        } catch (e: Exception) {
                            Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Immediate retry button
        TerminalOutlineButton(
            text = if (isRetrying) "正在重试队列中..." else "立即重试排队/失败录音",
            onClick = {
                if (!isRetrying) {
                    isRetrying = true
                    scope.launch {
                        Toast.makeText(context, "正在启动前台即时重试处理...", Toast.LENGTH_SHORT).show()
                        val (success, fail) = withContext(Dispatchers.IO) {
                            val db = AppDatabase.getInstance(context)
                            val repo = AudioRepository(db.audioRecordDao())
                            repo.resetPendingAndFailed()
                            UploadWorker.processPendingUploadsManual(context)
                        }
                        isRetrying = false
                        if (fail == 0) {
                            Toast.makeText(context, "✅ 重试完成：已成功提炼 $success 条录音", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "⚠️ 重试结束：成功 $success 条，失败 $fail 条，可查看诊断日志", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
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
            PermissionStatusRow(
                name = "麦克风录音权限",
                isGranted = hasMic,
                onClick = onRequestRuntimePermissions,
            )
            PermissionStatusRow(
                name = "无障碍盲操监听服务",
                isGranted = hasA11y,
                onClick = { permissionHelper.openAccessibilitySettings() },
            )
            PermissionStatusRow(
                name = "后台无限制电池优化",
                isGranted = hasBattery,
                onClick = { permissionHelper.requestIgnoreBatteryOptimizations() },
            )
            PermissionStatusRow(
                name = "前台通知与磁贴权限",
                isGranted = hasNotif,
                onClick = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    onRequestRuntimePermissions
                } else {
                    onOpenAppSettings
                },
            )
        }
    }

    Spacer(modifier = Modifier.height(Dimens.gapMd))

    SectionLabel("快捷操作秘籍")
    Spacer(modifier = Modifier.height(Dimens.gapSm))

    TerminalCard {
        Text(
            text = "操作技巧：\n" +
                "• 盲操：熄屏时同时按住音量 + 和 - 。震动 1 次松手=随身，连震 2 次=会议；\n" +
                "• 停止：录音中再长按约 1.2 秒；\n" +
                "• 桌面快捷方式：长按应用图标可直接开始录音；\n" +
                "• 导出：在今日复盘或纪要页顶部复制 Markdown。",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun PermissionStatusRow(
    name: String,
    isGranted: Boolean,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (isGranted) StatusUploaded else DangerRed,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = if (isGranted) "正常" else "待授权",
                style = MaterialTheme.typography.labelSmall,
                color = if (isGranted) StatusUploaded else DangerRed,
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
