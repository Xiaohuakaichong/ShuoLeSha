package com.example.shuolesa.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.shuolesa.ui.logic.DatePreset
import com.example.shuolesa.ui.logic.DateRange
import com.example.shuolesa.ui.logic.FilterLogic
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * v4.1-b ③ 按日期查看 (replaces 仅今天). Presets 今天/昨天/近 7 天/本月 + 自定义范围.
 * Calendar area scrolls (no fixed height) so the two buttons stay visible at large font sizes.
 * Buttons: 不限日期 | 看 M/d–M/d.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DateFilterSheet(onDismiss:()->Unit,onApply:(Long?,Long?)->Unit) {
    val zone=ZoneId.systemDefault();val today=LocalDate.now()
    val state=rememberDateRangePickerState()
    var preset by remember { mutableStateOf<DatePreset?>(null) }
    val custom:DateRange?=state.selectedStartDateMillis?.let { s ->
        val a=Instant.ofEpochMilli(s).atZone(ZoneOffset.UTC).toLocalDate()
        val b=Instant.ofEpochMilli(state.selectedEndDateMillis ?: s).atZone(ZoneOffset.UTC).toLocalDate()
        FilterLogic.range(a,b,zone)
    }
    val range=preset?.let { FilterLogic.preset(it,today,zone) } ?: custom
    ModalBottomSheet(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom=24.dp)) {
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState())) {
                Text("按日期查看",Modifier.padding(horizontal=24.dp),style=MaterialTheme.typography.titleLarge)
                Row(Modifier.padding(horizontal=24.dp,vertical=12.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    DatePreset.entries.forEach { p -> AppChip(preset==p,{preset=if(preset==p) null else p},label={Text(p.label)}) }
                }
                Meta("自定义范围",Modifier.padding(horizontal=24.dp))
                DateRangePicker(state,modifier=Modifier.fillMaxWidth(),title=null,headline=null,showModeToggle=false)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick={onApply(null,null)},modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text("不限日期")}
                Button(onClick={range?.let { onApply(it.start,it.endExclusive) }},enabled=range!=null,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                    Text(range?.let { "看 ${FilterLogic.label(it,zone)}" } ?: "选择日期") }
            }
        }
    }
}
