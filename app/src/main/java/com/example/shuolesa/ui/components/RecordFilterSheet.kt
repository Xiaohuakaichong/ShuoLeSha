package com.example.shuolesa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.theme.Accent
import com.example.shuolesa.theme.AppColor
import com.example.shuolesa.theme.CardElevated
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.util.Formatters
import java.util.Calendar
import java.util.TimeZone

enum class DatePreset { ALL, TODAY, YESTERDAY, LAST_7, DAY, RANGE }

data class RecordFilter(
    val datePreset: DatePreset = DatePreset.ALL,
    val dayMs: Long = 0L,
    val rangeStartMs: Long = 0L,
    val rangeEndMs: Long = 0L,
    val mode: String = "全部",
    val status: String = "全部",
) {
    val isDefault: Boolean
        get() = datePreset == DatePreset.ALL && mode == "全部" && status == "全部"

    fun dateLabel(): String = when (datePreset) {
        DatePreset.ALL -> "日期"
        DatePreset.TODAY -> "今天"
        DatePreset.YESTERDAY -> "昨天"
        DatePreset.LAST_7 -> "近 7 天"
        DatePreset.DAY -> Formatters.formatDateOnly(dayMs)
        DatePreset.RANGE -> compactRangeLabel(rangeStartMs, rangeEndMs)
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
            DatePreset.RANGE -> createdAt in Formatters.getStartOfDay(rangeStartMs)..Formatters.getEndOfDay(rangeEndMs)
        }
    }
}

private fun compactRangeLabel(startMs: Long, endMs: Long): String {
    val start = Formatters.formatDateOnly(startMs)
    val end = Formatters.formatDateOnly(endMs)
    return if (start.take(4) == end.take(4)) {
        "${start.drop(5)} – ${end.drop(5)}"
    } else {
        "$start – $end"
    }
}

/** DatePicker / DateRangePicker emit UTC midnight; convert to local noon for day-range matching. */
private fun utcMillisToLocalNoon(utcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        set(
            utc.get(Calendar.YEAR),
            utc.get(Calendar.MONTH),
            utc.get(Calendar.DAY_OF_MONTH),
            12,
            0,
            0,
        )
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun localMillisToUtcDate(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(
            local.get(Calendar.YEAR),
            local.get(Calendar.MONTH),
            local.get(Calendar.DAY_OF_MONTH),
            0,
            0,
            0,
        )
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
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
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) Accent else TextPrimary,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = if (active) Accent else TextMuted,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

@Composable
fun FilterDropdown(
    label: String,
    active: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        FilterAnchor(label = label, active = active, onClick = { onExpandedChange(true) })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.background(AppColor.surface),
        ) {
            items.forEach { (title, action) ->
                DropdownMenuItem(
                    text = { Text(title, color = TextPrimary) },
                    onClick = {
                        onExpandedChange(false)
                        action()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordFilterMenus(
    filter: RecordFilter,
    onChange: (RecordFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dateMenu by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    var showDayPicker by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterDropdown(
            label = filter.dateLabel(),
            active = filter.datePreset != DatePreset.ALL,
            expanded = dateMenu,
            onExpandedChange = { dateMenu = it },
            items = listOf(
                "全部日期" to { onChange(filter.copy(datePreset = DatePreset.ALL)) },
                "今天" to { onChange(filter.copy(datePreset = DatePreset.TODAY)) },
                "昨天" to { onChange(filter.copy(datePreset = DatePreset.YESTERDAY)) },
                "近 7 天" to { onChange(filter.copy(datePreset = DatePreset.LAST_7)) },
                "选择单日" to { showDayPicker = true },
                "选择区间" to { showRangePicker = true },
            ),
        )
        FilterDropdown(
            label = if (filter.mode == "全部") "类型" else filter.mode,
            active = filter.mode != "全部",
            expanded = modeMenu,
            onExpandedChange = { modeMenu = it },
            items = listOf("全部", "随身", "会议").map { option ->
                option to { onChange(filter.copy(mode = option)) }
            },
        )
        FilterDropdown(
            label = if (filter.status == "全部") "状态" else filter.status,
            active = filter.status != "全部",
            expanded = statusMenu,
            onExpandedChange = { statusMenu = it },
            items = listOf("全部", "已完成", "处理中", "失败").map { option ->
                option to { onChange(filter.copy(status = option)) }
            },
        )
    }

    if (showDayPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = localMillisToUtcDate(
                if (filter.dayMs > 0) filter.dayMs else System.currentTimeMillis(),
            ),
            initialDisplayMode = DisplayMode.Picker,
        )
        DatePickerDialog(
            onDismissRequest = { showDayPicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        val utc = pickerState.selectedDateMillis ?: return@TextButton
                        onChange(filter.copy(datePreset = DatePreset.DAY, dayMs = utcMillisToLocalNoon(utc)))
                        showDayPicker = false
                    },
                ) { Text("确定", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showDayPicker = false }) { Text("取消", color = TextMuted) }
            },
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }

    if (showRangePicker) {
        val rangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = filter.rangeStartMs.takeIf { it > 0 }?.let(::localMillisToUtcDate),
            initialSelectedEndDateMillis = filter.rangeEndMs.takeIf { it > 0 }?.let(::localMillisToUtcDate),
            initialDisplayMode = DisplayMode.Picker,
        )
        val canConfirm = rangeState.selectedStartDateMillis != null && rangeState.selectedEndDateMillis != null
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(
                    enabled = canConfirm,
                    onClick = {
                        val start = rangeState.selectedStartDateMillis ?: return@TextButton
                        val end = rangeState.selectedEndDateMillis ?: return@TextButton
                        onChange(
                            filter.copy(
                                datePreset = DatePreset.RANGE,
                                rangeStartMs = utcMillisToLocalNoon(start),
                                rangeEndMs = utcMillisToLocalNoon(end),
                            ),
                        )
                        showRangePicker = false
                    },
                ) { Text("确定", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showRangePicker = false }) { Text("取消", color = TextMuted) }
            },
        ) {
            DateRangePicker(
                state = rangeState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp),
                title = {
                    Text(
                        "选择日期区间",
                        modifier = Modifier.padding(start = Dimens.gapMd, top = Dimens.gapMd),
                        color = TextPrimary,
                    )
                },
                showModeToggle = false,
            )
        }
    }
}
