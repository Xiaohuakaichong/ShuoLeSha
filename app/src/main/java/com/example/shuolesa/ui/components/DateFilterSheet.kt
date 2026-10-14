package com.example.shuolesa.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DateFilterSheet(onDismiss:()->Unit,onApply:(Long?,Long?)->Unit) {
    val state=rememberDateRangePickerState()
    ModalBottomSheet(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom=24.dp)) {
            Text("选择日期范围",Modifier.padding(horizontal=24.dp),style=MaterialTheme.typography.titleLarge)
            DateRangePicker(state,modifier=Modifier.fillMaxWidth().height(420.dp),title=null)
            Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick={onApply(null,null)},modifier=Modifier.weight(1f)) {Text("不限日期")}
                Button(onClick={
                    val zone=ZoneId.systemDefault()
                    val start=Instant.ofEpochMilli(state.selectedStartDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                    val end=Instant.ofEpochMilli(state.selectedEndDateMillis ?: state.selectedStartDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                    onApply(start.atStartOfDay(zone).toInstant().toEpochMilli(),end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                },enabled=state.selectedStartDateMillis!=null,modifier=Modifier.weight(1f)) {Text("应用")}
            }
        }
    }
}
