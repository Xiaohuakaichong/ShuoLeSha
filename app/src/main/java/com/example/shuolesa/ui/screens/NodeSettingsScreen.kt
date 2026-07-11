package com.example.shuolesa.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.network.ApiService
import com.example.shuolesa.theme.BorderGray
import com.example.shuolesa.theme.DangerRed
import com.example.shuolesa.theme.DarkGray
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.NeonGreen
import com.example.shuolesa.theme.NeonGreenDim
import com.example.shuolesa.theme.PureBlack
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
 * 节点配置：Coze 连接、盲操参数与系统权限状态。
 */
@Composable
fun NodeSettingsScreen(
    prefs: AppPreferences,
    permissionHelper: PermissionHelper,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val cozeBaseUrl by prefs.cozeBaseUrl.collectAsState(initial = "https://api.coze.cn")
    val cozeApiKey by prefs.cozeApiKey.collectAsState(initial = "")
    val cozeWorkflowId by prefs.cozeWorkflowId.collectAsState(initial = "")
    val hapticEnabled by prefs.hapticEnabled.collectAsState(initial = true)
    val triggerDuration by prefs.triggerDuration.collectAsState(initial = 3)
    val autoResumeAfterCall by prefs.autoResumeAfterCall.collectAsState(initial = false)

    var tokenVisible by remember { mutableStateOf(false) }
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
            .background(PureBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.pagePaddingH, vertical = Dimens.pagePaddingV),
    ) {
        PageHeader(
            title = "节点配置",
            subtitle = "配置 Coze 节点与盲操参数",
        )

        Spacer(modifier = Modifier.height(Dimens.gapXl))

        SectionLabel("服务区域")
        Spacer(modifier = Modifier.height(Dimens.gapSm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val isCn = cozeBaseUrl.contains("coze.cn")
            TerminalOutlineButton(
                text = "国内 · 扣子",
                onClick = { scope.launch { prefs.setCozeBaseUrl("https://api.coze.cn") } },
                modifier = Modifier.weight(1f),
                selected = isCn,
            )
            TerminalOutlineButton(
                text = "国际 · Coze",
                onClick = { scope.launch { prefs.setCozeBaseUrl("https://api.coze.com") } },
                modifier = Modifier.weight(1f),
                selected = !isCn,
            )
        }

        Spacer(modifier = Modifier.height(Dimens.gapLg))

        SectionLabel("API Key")
        Spacer(modifier = Modifier.height(Dimens.gapSm))
        TerminalTextField(
            value = cozeApiKey,
            onValueChange = { scope.launch { prefs.setCozeApiKey(it) } },
            placeholder = "pat_xxxxxxxx",
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

        SectionLabel("工作流 ID")
        Spacer(modifier = Modifier.height(Dimens.gapSm))
        TerminalTextField(
            value = cozeWorkflowId,
            onValueChange = { scope.launch { prefs.setCozeWorkflowId(it) } },
            placeholder = "739xxxxxxxxxxxx",
        )

        Spacer(modifier = Modifier.height(Dimens.gapLg))

        val pingColor by animateColorAsState(
            targetValue = when (pingStatus) {
                is PingState.Success -> NeonGreen
                is PingState.Error -> DangerRed
                else -> NeonGreen
            },
            animationSpec = tween(300),
            label = "pingColor",
        )
        val pingLabel = when (pingStatus) {
            is PingState.Success -> "连接成功"
            is PingState.Error -> "连接失败"
            else -> "测试连接"
        }

        TerminalOutlineButton(
            text = pingLabel,
            onClick = {
                pingStatus = PingState.Loading
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        ApiService().testCozeConnection(cozeBaseUrl, cozeApiKey)
                    }
                    pingStatus = if (result.isSuccess) {
                        PingState.Success
                    } else {
                        PingState.Error(result.exceptionOrNull()?.message ?: "未知错误")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = cozeApiKey.isNotBlank(),
            loading = pingStatus is PingState.Loading,
            color = pingColor,
            selected = true,
        )

        if (pingStatus is PingState.Error) {
            Spacer(modifier = Modifier.height(Dimens.gapXs))
            Text(
                text = (pingStatus as PingState.Error).message,
                style = MaterialTheme.typography.bodySmall,
                color = DangerRed,
            )
        }

        Spacer(modifier = Modifier.height(Dimens.gapXl))

        SettingsRow(
            label = "震动反馈",
            subtitle = "开停录时短振提示",
        ) {
            Switch(
                checked = hapticEnabled,
                onCheckedChange = { scope.launch { prefs.setHapticEnabled(it) } },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NeonGreen,
                    checkedTrackColor = NeonGreenDim.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = DarkGray,
                ),
            )
        }

        Spacer(modifier = Modifier.height(Dimens.gapMd))

        SettingsRow(
            label = "通话后继续",
            subtitle = "来电暂停，挂断后自动续录",
        ) {
            Switch(
                checked = autoResumeAfterCall,
                onCheckedChange = { scope.launch { prefs.setAutoResumeAfterCall(it) } },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NeonGreen,
                    checkedTrackColor = NeonGreenDim.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = DarkGray,
                ),
            )
        }

        Spacer(modifier = Modifier.height(Dimens.gapMd))

        var tempDuration by remember(triggerDuration) { mutableFloatStateOf(triggerDuration.toFloat()) }
        TerminalCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(text = "触发时长", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(text = "同时长按音量 +/-", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Text(
                    text = "${tempDuration.toInt()} 秒",
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonGreen,
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
                    thumbColor = NeonGreen,
                    activeTrackColor = NeonGreen,
                    inactiveTrackColor = DarkGray,
                    activeTickColor = PureBlack,
                    inactiveTickColor = TextMuted,
                ),
            )
        }

        Spacer(modifier = Modifier.height(Dimens.gapMd))

        TerminalCard(
            borderColor = if (a11yEnabled) NeonGreen.copy(alpha = 0.35f) else DangerRed.copy(alpha = 0.35f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(Dimens.statusDot)
                        .clip(CircleShape)
                        .background(if (a11yEnabled) NeonGreen else DangerRed),
                )
                Text(
                    text = if (a11yEnabled) "无障碍服务已启用" else "无障碍服务未启用",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (a11yEnabled) NeonGreen else DangerRed,
                    modifier = Modifier.padding(start = Dimens.gapSm),
                )
            }
            if (!a11yEnabled) {
                Spacer(modifier = Modifier.height(Dimens.gapSm))
                Text(
                    text = "开启后可使用音量键组合盲操录音",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
                TextButton(onClick = { permissionHelper.openAccessibilitySettings() }) {
                    Text("前往设置", style = MaterialTheme.typography.labelLarge, color = NeonGreen)
                }
            }
        }

        if (!batteryIgnored) {
            Spacer(modifier = Modifier.height(Dimens.gapMd))
            TerminalCard {
                Text(
                    text = "电池优化",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(Dimens.gapXs))
                Text(
                    text = "建议关闭电池优化，避免后台录音被系统杀掉",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
                TextButton(onClick = { permissionHelper.requestIgnoreBatteryOptimizations() }) {
                    Text("关闭电池优化", style = MaterialTheme.typography.labelLarge, color = NeonGreen)
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
