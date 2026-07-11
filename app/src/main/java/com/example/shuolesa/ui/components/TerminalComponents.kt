package com.example.shuolesa.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.theme.BorderGray
import com.example.shuolesa.theme.DarkCard
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.NeonGreen
import com.example.shuolesa.theme.StatusFailed
import com.example.shuolesa.theme.StatusPending
import com.example.shuolesa.theme.StatusUploaded
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.theme.TextSecondary

@Composable
fun PageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
            }
            Text(
                text = "> ${title}_",
                style = MaterialTheme.typography.headlineLarge,
                color = NeonGreen,
            )
        }
        Spacer(modifier = Modifier.height(Dimens.gapSm))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
        modifier = modifier,
    )
}

@Composable
fun TerminalCard(
    modifier: Modifier = Modifier,
    borderColor: Color = BorderGray,
    contentPadding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cardRadius))
            .background(DarkCard)
            .border(Dimens.borderThin, borderColor, RoundedCornerShape(Dimens.cardRadius))
            .then(
                if (contentPadding) {
                    Modifier.padding(Dimens.cardPadding)
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}

@Composable
fun SettingsRow(
    label: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit,
) {
    TerminalCard(modifier = modifier, contentPadding = false) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.cardPadding, vertical = Dimens.cardPaddingVCompact),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            trailing()
        }
    }
}

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier,
    showDot: Boolean = true,
) {
    val color = statusColor(status)
    val label = statusLabel(status)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(Dimens.statusDot)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

fun statusColor(status: String): Color = when (status) {
    AudioRecordEntity.STATUS_UPLOADED -> StatusUploaded
    AudioRecordEntity.STATUS_PENDING, AudioRecordEntity.STATUS_UPLOADING -> StatusPending
    AudioRecordEntity.STATUS_FAILED -> StatusFailed
    else -> TextMuted
}

fun statusLabel(status: String): String = when (status) {
    AudioRecordEntity.STATUS_UPLOADED -> "已上传"
    AudioRecordEntity.STATUS_PENDING -> "待上传"
    AudioRecordEntity.STATUS_UPLOADING -> "上传中"
    AudioRecordEntity.STATUS_FAILED -> "失败"
    else -> status
}

@Composable
fun TerminalOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = true,
    color: Color = NeonGreen,
    loading: Boolean = false,
) {
    val contentColor = if (selected) color else TextMuted
    val border = if (selected) color else BorderGray
    val container = if (selected) color.copy(alpha = 0.15f) else DarkCard

    Button(
        onClick = onClick,
        modifier = modifier.height(Dimens.buttonHeight),
        enabled = enabled && !loading,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = contentColor,
            disabledContainerColor = DarkCard,
            disabledContentColor = TextMuted,
        ),
        shape = RoundedCornerShape(Dimens.fieldRadius),
        border = BorderStroke(Dimens.borderThin, border),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = contentColor,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = contentColor)
        }
    }
}

@Composable
fun TerminalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            if (placeholder.isNotEmpty()) {
                Text(placeholder, color = TextMuted)
            }
        },
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = NeonGreen,
            focusedBorderColor = NeonGreen,
            unfocusedBorderColor = BorderGray,
            focusedContainerColor = DarkCard,
            unfocusedContainerColor = DarkCard,
        ),
        shape = RoundedCornerShape(Dimens.fieldRadius),
    )
}

@Composable
fun EmptyState(
    symbol: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.displayLarge,
            color = TextMuted,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
    }
}
