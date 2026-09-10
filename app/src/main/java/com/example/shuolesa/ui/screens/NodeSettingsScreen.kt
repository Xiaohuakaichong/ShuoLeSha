package com.example.shuolesa.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.example.shuolesa.theme.ElectricBlue
import com.example.shuolesa.theme.MintCyan
import com.example.shuolesa.theme.MintCyanDim
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
 * 现代 AI 引擎与节点配置中心：支持多 Provider 预设、ASR/LLM 模型参数、自定义 Prompt 与盲操系统设置。
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
    val triggerDuration by prefs.triggerDuration.collectAsState(initial = 3)
    val autoResumeAfterCall by prefs.autoResumeAfterCall.collectAsState(initial = false)

    var tokenVisible by remember { mutableStateOf(false) }
    var promptExpanded by remember { mutableStateOf(false) }
    var pingStatus by remember { mutableStateOf<PingState>(PingState.Idle) }
    var a11yEnabled by remember { mutableStateOf(permissionHelper.isAccessibilityServiceEnabled()) }
    var batteryIgnored by remember { mutableStateOf(permissionHelper.isBatteryOptimizationIgnored()) }

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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.pagePaddingH, vertical = Dimens.pagePaddingV),
    ) {
        PageHeader(
            title = "引擎与设置",
            subtitle = "配置 AI 提供商、模型节点及盲操触发",
        )

        Spacer(modifier = Modifier.height(Dimens.gapLg))

        // Provider Preset Selection
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
                        .clickable {
                            scope.launch {
                                prefs.applyPreset(preset)
                                pingStatus = PingState.Idle
                            }
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = preset.displayName.split(" ")[0], // Short name
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MintCyan else TextSecondary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.gapLg))

        // Base URL
        SectionLabel("API 端点 (Base URL)")
        Spacer(modifier = Modifier.height(Dimens.gapSm))
        TerminalTextField(
            value = baseUrl,
            onValueChange = { scope.launch { prefs.setBaseUrl(it) } },
            placeholder = "https://api.stepfun.com/v1",
        )

        Spacer(modifier = Modifier.height(Dimens.gapLg))

        // API Key
        SectionLabel("API Key 密钥")
        Spacer(modifier = Modifier.height(Dimens.gapSm))
        TerminalTextField(
            value = apiKey,
            onValueChange = { scope.launch { prefs.setApiKey(it) } },
            placeholder = if (providerMode == ProviderPreset.LOCAL_OR_CUSTOM.id) "本地免认证时可为空" else "请输入 API Key",
            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                    Icon(
                        imageVector = if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (tokenVisible) "隐藏" else "显示",
                        tint = TextSecondary,
                    )
                }
            },
        )

        Spacer(modifier = Modifier.height(Dimens.gapLg))

        // ASR Model
        SectionLabel("语音转写模型 (ASR)")
        Spacer(modifier = Modifier.height(Dimens.gapSm))
        TerminalTextField(
            value = asrModel,
            onValueChange = { scope.launch { prefs.setAsrModel(it) } },
            placeholder = "stepaudio-2.5-asr",
        )
        Spacer(modifier = Modifier.height(6.dp))
        // Suggestions
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val asrSuggestions = listOf("stepaudio-2.5-asr", "FunAudioLLM/SenseVoiceSmall", "whisper-1")
            items(asrSuggestions) { suggestion ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CardElevated)
                        .clickable { scope.launch { prefs.setAsrModel(suggestion) } }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(text = suggestion, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.gapLg))

        // LLM Model
        SectionLabel("智能整理模型 (LLM)")
        Spacer(modifier = Modifier.height(Dimens.gapSm))
        TerminalTextField(
            value = llmModel,
            onValueChange = { scope.launch { prefs.setLlmModel(it) } },
            placeholder = "step-1-8k",
        )
        Spacer(modifier = Modifier.height(6.dp))
        // Suggestions
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val llmSuggestions = listOf("step-router-v1", "step-1-8k", "step-2-16k", "deepseek-ai/DeepSeek-V3")
            items(llmSuggestions) { suggestion ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CardElevated)
                        .clickable { scope.launch { prefs.setLlmModel(suggestion) } }
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
                    .clickable { promptExpanded = !promptExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(text = "提炼提示词 (Prompt)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(text = if (promptExpanded) "点击收起" else "点击展开自定义 Prompt", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                IconButton(onClick = {
                    scope.launch { prefs.setSystemPrompt(AppPreferences.DEFAULT_SYSTEM_PROMPT) }
                }) {
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
                        onValueChange = { scope.launch { prefs.setSystemPrompt(it) } },
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
            onClick = {
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
            modifier = Modifier.fillMaxWidth(),
            enabled = pingStatus !is PingState.Loading,
            loading = pingStatus is PingState.Loading,
            color = pingColor,
            selected = true,
        )

        if (pingStatus is PingState.Error) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = (pingStatus as PingState.Error).message,
                style = MaterialTheme.typography.bodySmall,
                color = DangerRed,
            )
        }

        Spacer(modifier = Modifier.height(Dimens.gapXl))

        // Storage & Audio Retention
        SettingsRow(
            label = "保留本地录音原件",
            subtitle = "识别完成后仍保留 Opus 音频用于本地回放",
        ) {
            Switch(
                checked = keepLocalAudio,
                onCheckedChange = { scope.launch { prefs.setKeepLocalAudio(it) } },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MintCyan,
                    checkedTrackColor = MintCyanDim.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = CardElevated,
                ),
            )
        }

        Spacer(modifier = Modifier.height(Dimens.gapMd))

        // Haptics
        SettingsRow(
            label = "触觉震动反馈",
            subtitle = "盲操开始/结束录音时震动脉冲提示",
        ) {
            Switch(
                checked = hapticEnabled,
                onCheckedChange = { scope.launch { prefs.setHapticEnabled(it) } },
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
                onCheckedChange = { scope.launch { prefs.setAutoResumeAfterCall(it) } },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MintCyan,
                    checkedTrackColor = MintCyanDim.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = CardElevated,
                ),
            )
        }

        Spacer(modifier = Modifier.height(Dimens.gapMd))

        // Trigger Duration
        var tempDuration by remember(triggerDuration) { mutableFloatStateOf(triggerDuration.toFloat()) }
        TerminalCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(text = "盲操触发时长", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(text = "同时按住音量 +/- 键所需时长", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Text(
                    text = "${tempDuration.toInt()} 秒",
                    style = MaterialTheme.typography.titleLarge,
                    color = MintCyan,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(Dimens.gapSm))
            Slider(
                value = tempDuration,
                onValueChange = { tempDuration = it },
                onValueChangeFinished = {
                    scope.launch { prefs.setTriggerDuration(tempDuration.toInt()) }
                },
                valueRange = 1f..5f,
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

        // Accessibility Service Status
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
                TextButton(onClick = { permissionHelper.openAccessibilitySettings() }) {
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
                TextButton(onClick = { permissionHelper.requestIgnoreBatteryOptimizations() }) {
                    Text("开启无限制白名单", style = MaterialTheme.typography.labelLarge, color = MintCyan)
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.pageBottomNavClearance))
    }
}

private sealed class PingState {
    data object Idle : PingState()
    data object Loading : PingState()
    data object Success : PingState()
    data class Error(val message: String) : PingState()
}

