package com.example.shuolesa.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.theme.Accent
import com.example.shuolesa.theme.AppColor
import com.example.shuolesa.theme.CardDark
import com.example.shuolesa.theme.CardElevated
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.ModeMeeting
import com.example.shuolesa.theme.StatusFailed
import com.example.shuolesa.theme.StatusPending
import com.example.shuolesa.theme.StatusUploaded
import com.example.shuolesa.theme.SurfaceBorder
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.theme.TextSecondary
import com.example.shuolesa.theme.recordRoleColor

@Composable
fun Modifier.pressScale(pressed: Boolean, minScale: Float = 0.97f): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (pressed) minScale else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )
    return this.scale(scale)
}

@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.gapMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(Dimens.iconButton)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextPrimary,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Dimens.gapXs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
        if (onSettings != null) {
            IconButton(onClick = onSettings, modifier = Modifier.size(Dimens.iconButton)) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = TextSecondary,
                )
            }
        }
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier,
    )
}

@Composable
fun TerminalCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Color.Transparent,
    backgroundColor: Color = CardDark,
    contentPadding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val outer = RoundedCornerShape(Dimens.cardRadius)
    val inner = RoundedCornerShape(Dimens.radiusXl)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = outer,
                ambientColor = AppColor.shadowTint,
                spotColor = AppColor.shadowTint,
            )
            .clip(outer)
            .background(Color.Black.copy(alpha = 0.035f))
            .border(Dimens.borderThin, Color.Black.copy(alpha = 0.05f), outer)
            .padding(5.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(inner)
                .background(backgroundColor)
                .then(
                    if (borderColor.alpha > 0.01f) {
                        Modifier.border(Dimens.borderThin, borderColor.copy(alpha = 0.35f), inner)
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (contentPadding) {
                        Modifier.padding(
                            start = Dimens.cardPadding,
                            end = Dimens.cardPadding,
                            top = Dimens.cardPadding,
                            bottom = Dimens.cardPadding + Dimens.gapXs,
                        )
                    } else {
                        Modifier
                    },
                ),
            content = content,
        )
    }
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
                Spacer(modifier = Modifier.height(2.dp))
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
    val bg = color.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .background(bg)
            .padding(horizontal = Dimens.chipPaddingH, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.gapXs),
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun TagChip(
    tag: String,
    modifier: Modifier = Modifier,
    color: Color = ModeMeeting,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        val label = if (tag.startsWith("#")) tag else "#$tag"
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Accent,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(Dimens.chipRadius)
    Box(
        modifier = modifier
            .pressScale(pressed)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.16f) else CardElevated)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) accent else TextSecondary,
        )
    }
}

@Composable
fun ModeBadge(
    isMeeting: Boolean,
    modifier: Modifier = Modifier,
    isDigest: Boolean = false,
) {
    val color = recordRoleColor(isDigest, isMeeting)
    val label = when {
        isDigest -> "复盘"
        isMeeting -> "会议"
        else -> "随身"
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun SelectableTile(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Accent,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(Dimens.fieldRadius)
    Box(
        modifier = modifier
            .pressScale(pressed, 0.98f)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.15f) else CardElevated)
            .border(
                Dimens.borderThin,
                if (selected) accent else SurfaceBorder,
                shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = Dimens.gapSm),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) accent else TextPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) accent.copy(alpha = 0.85f) else TextMuted,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
fun statusColor(status: String): Color = when (status) {
    AudioRecordEntity.STATUS_UPLOADED -> StatusUploaded
    AudioRecordEntity.STATUS_PENDING, AudioRecordEntity.STATUS_UPLOADING -> StatusPending
    AudioRecordEntity.STATUS_FAILED -> StatusFailed
    else -> TextMuted
}

fun statusLabel(status: String): String = when (status) {
    AudioRecordEntity.STATUS_UPLOADED -> "已完成"
    AudioRecordEntity.STATUS_PENDING -> "排队中"
    AudioRecordEntity.STATUS_UPLOADING -> "处理中"
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
    color: Color = Accent,
    loading: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val contentColor = if (selected) color else TextMuted
    val border = if (selected) color.copy(alpha = 0.6f) else SurfaceBorder
    val container = if (selected) color.copy(alpha = 0.12f) else CardDark

    Button(
        onClick = onClick,
        modifier = modifier.height(Dimens.buttonHeight),
        enabled = enabled && !loading,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = contentColor,
            disabledContainerColor = CardDark,
            disabledContentColor = TextMuted,
        ),
        shape = RoundedCornerShape(Dimens.radiusFull),
        border = BorderStroke(Dimens.borderThin, border),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
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
            cursorColor = Accent,
            focusedBorderColor = Accent,
            unfocusedBorderColor = SurfaceBorder,
            focusedContainerColor = CardDark,
            unfocusedContainerColor = CardDark,
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
    imageRes: Int? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (imageRes != null) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .height(220.dp),
                contentScale = ContentScale.Crop,
                alignment = Alignment.BottomCenter,
            )
            Spacer(modifier = Modifier.height(Dimens.gapSm))
        } else if (symbol.isNotBlank()) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.displayLarge,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(Dimens.gapSm))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Dimens.gapXs))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}
