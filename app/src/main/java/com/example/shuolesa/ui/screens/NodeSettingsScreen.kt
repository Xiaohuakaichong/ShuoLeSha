package com.example.shuolesa.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.network.ApiService
import com.example.shuolesa.service.BlindTriggerService
import com.example.shuolesa.theme.BorderGray
import com.example.shuolesa.theme.DangerRed
import com.example.shuolesa.theme.DarkCard
import com.example.shuolesa.theme.DarkGray
import com.example.shuolesa.theme.MediumGray
import com.example.shuolesa.theme.NeonGreen
import com.example.shuolesa.theme.NeonGreenDim
import com.example.shuolesa.theme.PureBlack
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.theme.TextSecondary
import com.example.shuolesa.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Page 1: Node Settings — server config, auth, and system status.
 */
@Composable
fun NodeSettingsScreen(
    prefs: AppPreferences,
    permissionHelper: PermissionHelper,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    val cozeBaseUrl by prefs.cozeBaseUrl.collectAsState(initial = "https://api.coze.cn")
    val cozeApiKey by prefs.cozeApiKey.collectAsState(initial = "")
    val cozeWorkflowId by prefs.cozeWorkflowId.collectAsState(initial = "")
    val hapticEnabled by prefs.hapticEnabled.collectAsState(initial = true)
    val triggerDuration by prefs.triggerDuration.collectAsState(initial = 3)

    var tokenVisible by remember { mutableStateOf(false) }
    var pingStatus by remember { mutableStateOf<PingState>(PingState.Idle) }
    val a11yEnabled = BlindTriggerService.isServiceActive

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // Title
        Text(
            text = "> 节点配置_",
            style = MaterialTheme.typography.headlineLarge,
            color = NeonGreen,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "CONFIGURE YOUR ENDPOINT",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Coze Platform Region
        SectionLabel("COZE REGION / BASE URL")
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val isCn = cozeBaseUrl == "https://api.coze.cn"
            Button(
                onClick = { scope.launch { prefs.setCozeBaseUrl("https://api.coze.cn") } },
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCn) NeonGreen.copy(alpha = 0.15f) else DarkCard,
                    contentColor = if (isCn) NeonGreen else TextMuted
                ),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (isCn) NeonGreen else BorderGray)
            ) {
                Text("国内版 (扣子)", style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = { scope.launch { prefs.setCozeBaseUrl("https://api.coze.com") } },
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isCn) NeonGreen.copy(alpha = 0.15f) else DarkCard,
                    contentColor = if (!isCn) NeonGreen else TextMuted
                ),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (!isCn) NeonGreen else BorderGray)
            ) {
                Text("国际版 (Coze)", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Coze API Key
        SectionLabel("COZE API KEY (TOKEN)")
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = cozeApiKey,
            onValueChange = { scope.launch { prefs.setCozeApiKey(it) } },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("pat_xxxxxxxxxxxxxxxx", color = TextMuted) },
            singleLine = true,
            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                    Icon(
                        imageVector = if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle visibility",
                        tint = TextSecondary,
                    )
                }
            },
            colors = textFieldColors(),
            shape = RoundedCornerShape(8.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Coze Workflow ID
        SectionLabel("COZE WORKFLOW ID")
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = cozeWorkflowId,
            onValueChange = { scope.launch { prefs.setCozeWorkflowId(it) } },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("739xxxxxxxxxxxxxx", color = TextMuted) },
            singleLine = true,
            colors = textFieldColors(),
            shape = RoundedCornerShape(8.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Test Connection Button
        val pingButtonColor by animateColorAsState(
            targetValue = when (pingStatus) {
                is PingState.Success -> NeonGreen
                is PingState.Error -> DangerRed
                else -> NeonGreen
            },
            animationSpec = tween(300),
            label = "pingColor",
        )

        Button(
            onClick = {
                pingStatus = PingState.Loading
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        ApiService().testCozeConnection(cozeBaseUrl, cozeApiKey)
                    }
                    pingStatus = if (result.isSuccess) PingState.Success else PingState.Error(
                        result.exceptionOrNull()?.message ?: "Unknown error"
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = pingButtonColor.copy(alpha = 0.15f),
                contentColor = pingButtonColor,
            ),
            shape = RoundedCornerShape(8.dp),
            enabled = pingStatus !is PingState.Loading && cozeApiKey.isNotBlank(),
        ) {
            when (pingStatus) {
                is PingState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = NeonGreen,
                        strokeWidth = 2.dp,
                    )
                }
                is PingState.Success -> Text("✓ CONNECTED", style = MaterialTheme.typography.labelLarge)
                is PingState.Error -> Text("✗ FAILED", style = MaterialTheme.typography.labelLarge, color = DangerRed)
                else -> Text("TEST CONNECTION", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (pingStatus is PingState.Error) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = (pingStatus as PingState.Error).message,
                style = MaterialTheme.typography.bodySmall,
                color = DangerRed,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Haptic Feedback Toggle
        SettingsRow(
            label = "震动反馈",
            subtitle = "HAPTIC FEEDBACK",
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

        Spacer(modifier = Modifier.height(16.dp))

        // Trigger Duration Slider
        var tempDuration by remember(triggerDuration) { mutableFloatStateOf(triggerDuration.toFloat()) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkCard)
                .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "触发延迟", style = MaterialTheme.typography.titleMedium)
                    Text(text = "TRIGGER DELAY", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Text(
                    text = "${tempDuration.toInt()}s",
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonGreen,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
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
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Accessibility Service Status
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkCard)
                .border(1.dp, if (a11yEnabled) NeonGreen.copy(alpha = 0.3f) else DangerRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (a11yEnabled) NeonGreen else DangerRed),
                    )
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    Text(
                        text = if (a11yEnabled) "无障碍服务已启用" else "无障碍服务未启用",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (a11yEnabled) NeonGreen else DangerRed,
                    )
                }

                if (!a11yEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "需要开启无障碍服务才能使用音量键盲操功能",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { permissionHelper.openAccessibilitySettings() },
                        colors = ButtonDefaults.textButtonColors(contentColor = NeonGreen),
                    ) {
                        Text("前往设置 →", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Battery optimization
        if (!permissionHelper.isBatteryOptimizationIgnored()) {
            TextButton(
                onClick = { permissionHelper.requestIgnoreBatteryOptimizations() },
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
            ) {
                Text("⚡ 关闭电池优化（推荐）", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(80.dp)) // Bottom nav padding
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
    )
}

@Composable
private fun SettingsRow(
    label: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        trailing()
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = NeonGreen,
    focusedBorderColor = NeonGreen,
    unfocusedBorderColor = BorderGray,
    focusedContainerColor = DarkCard,
    unfocusedContainerColor = DarkCard,
)

private sealed class PingState {
    data object Idle : PingState()
    data object Loading : PingState()
    data object Success : PingState()
    data class Error(val message: String) : PingState()
}
