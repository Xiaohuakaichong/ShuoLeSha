package com.example.shuolesa.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.theme.Accent
import com.example.shuolesa.theme.AppColor
import com.example.shuolesa.theme.CardElevated
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.SurfaceBorder
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.util.Formatters
import java.util.Calendar

enum class DatePreset { ALL, TODAY, YESTERDAY, LAST_7, DAY }

data class RecordFilter(
    val datePreset: DatePreset = DatePreset.ALL,
    val dayMs: Long = 0L,
    val mode: String = "全部",
    val status: String = "全部",
) {
    val isDefault: Boolean
        get() = datePreset == DatePreset.ALL && mode == "全部" && status == "全部"

    fun dateLabel(): String = when (datePreset) {
        DatePreset.ALL -> "全部日期"
        DatePreset.TODAY -> "今天"
        DatePreset.YESTERDAY -> "昨天"
        DatePreset.LAST_7 -> "近 7 天"
        DatePreset.DAY -> Formatters.formatDateOnly(dayMs)
    }

    fun matches(record: AudioRecordEntity): Boolean {
        if (!matchesDate(record.createdAt)) return false
        when (mode) {
            "随身" -> if (record.isMeeting()) return false
            "会议" -> if (!record.isMeeting()) return false
        }
        when (status) {
            "已完成" -> if (record.status != AudioRecordEntity.STATUS_UPLOADED) return false
            "处理中" -> if (
                record.status != AudioRecordEntity.STATUS_PENDING &&
                record.status != AudioRecordEntity.STATUS_UPLOADING
            ) return false
            "失败" -> if (record.status != AudioRecordEntity.STATUS_FAILED) return false
        }
        return true
    }

    private fun matchesDate(createdAt: Long): Boolean {
        val now = System.currentTimeMillis()
        return when (datePreset) {
            DatePreset.ALL -> true
            DatePreset.TODAY -> createdAt in Formatters.getStartOfDay(now)..Formatters.getEndOfDay(now)
            DatePreset.YESTERDAY -> {
                val y = now - 24 * 60 * 60 * 1000L
                createdAt in Formatters.getStartOfDay(y)..Formatters.getEndOfDay(y)
            }
            DatePreset.LAST_7 -> createdAt >= Formatters.getStartOfDay(now - 6 * 24 * 60 * 60 * 1000L)
            DatePreset.DAY -> createdAt in Formatters.getStartOfDay(dayMs)..Formatters.getEndOfDay(dayMs)
        }
    }
}

@Composable
fun FilterAnchor(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .background(if (active) Accent.copy(alpha = 0.14f) else CardElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) Accent else TextPrimary,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        )
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = if (active) Accent else TextMuted,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordFilterSheet(
    filter: RecordFilter,
    onChange: (RecordFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun pickDay() {
        val cal = Calendar.getInstance()
        if (filter.datePreset == DatePreset.DAY && filter.dayMs > 0) {
            cal.timeInMillis = filter.dayMs
        }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply { set(year, month, day, 12, 0, 0) }
                onChange(filter.copy(datePreset = DatePreset.DAY, dayMs = picked.timeInMillis))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColor.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.pagePaddingH, vertical = Dimens.gapMd),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                ) {
                    Icon(Icons.Outlined.FilterList, contentDescription = null, tint = Accent)
                    Text(
                        text = "筛选记录",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(Dimens.gapMd))
            SectionLabel("日期")
            Spacer(modifier = Modifier.height(Dimens.gapSm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                verticalArrangement = Arrangement.spacedBy(Dimens.gapSm),
            ) {
                FilterChip("全部", filter.datePreset == DatePreset.ALL, { onChange(filter.copy(datePreset = DatePreset.ALL)) })
                FilterChip("今天", filter.datePreset == DatePreset.TODAY, { onChange(filter.copy(datePreset = DatePreset.TODAY)) })
                FilterChip("昨天", filter.datePreset == DatePreset.YESTERDAY, { onChange(filter.copy(datePreset = DatePreset.YESTERDAY)) })
                FilterChip("近 7 天", filter.datePreset == DatePreset.LAST_7, { onChange(filter.copy(datePreset = DatePreset.LAST_7)) })
                FilterChip(
                    if (filter.datePreset == DatePreset.DAY) Formatters.formatDateOnly(filter.dayMs) else "选日期",
                    filter.datePreset == DatePreset.DAY,
                    { pickDay() },
                )
            }

            Spacer(modifier = Modifier.height(Dimens.gapLg))
            SectionLabel("类型")
            Spacer(modifier = Modifier.height(Dimens.gapSm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm)) {
                listOf("全部", "随身", "会议").forEach { option ->
                    FilterChip(option, filter.mode == option, { onChange(filter.copy(mode = option)) })
                }
            }

            Spacer(modifier = Modifier.height(Dimens.gapLg))
            SectionLabel("状态")
            Spacer(modifier = Modifier.height(Dimens.gapSm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm)) {
                listOf("全部", "已完成", "处理中", "失败").forEach { option ->
                    FilterChip(option, filter.status == option, { onChange(filter.copy(status = option)) })
                }
            }

            Spacer(modifier = Modifier.height(Dimens.gapXl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Dimens.radiusFull))
                        .background(CardElevated)
                        .clickable { onChange(RecordFilter()) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("重置", style = MaterialTheme.typography.labelLarge, color = TextMuted)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Dimens.radiusFull))
                        .background(Accent)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("完成", style = MaterialTheme.typography.labelLarge, color = AppColor.onPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(Dimens.gapLg))
        }
    }
}
