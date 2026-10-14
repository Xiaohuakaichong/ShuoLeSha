package com.example.shuolesa.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.shuolesa.BuildConfig
import com.example.shuolesa.data.prefs.*
import com.example.shuolesa.ui.SettingsViewModel
import com.example.shuolesa.ui.components.*
import com.example.shuolesa.util.PermissionHelper

@Composable fun SettingsScreen(vm:SettingsViewModel,page:String,onBack:()->Unit,onPage:(String)->Unit) {
    val context=LocalContext.current;val helper=remember { PermissionHelper(context) }
    val busy by vm.busy.collectAsStateWithLifecycle();val config by vm.config.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    var cleanup by remember { mutableStateOf(false) }
    var permissionRefresh by remember { mutableIntStateOf(0) }
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionRefresh++ }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::export) }
    val title=when(page) {"service"->"转写与整理";"record"->"录音与快捷启动";"appearance"->"外观";"data"->"数据与诊断";else->"设置"}
    Page(title,onBack) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            when(page) {
                "home"->{
                    item { Panel { Row(verticalAlignment=Alignment.CenterVertically) {Ceer(CeerPose.IDLE);Column(Modifier.padding(start=16.dp)) {Text("说了啥",style=MaterialTheme.typography.titleLarge);Meta("找回说过的话 · v${BuildConfig.VERSION_NAME}")} } } }
                    item { Panel {
                        ActionRow("转写与整理",if(config?.apiKey?.isNotBlank()==true || config?.local==true) "已配置服务" else "未配置，仍可本地录音",Icons.Outlined.Cloud) {onPage("service")}
                        HorizontalDivider();ActionRow("录音与快捷启动","随身、会议、音量键与来电处理",Icons.Outlined.Mic) {onPage("record")}
                        HorizontalDivider();ActionRow("外观","跟随系统或手动切换",Icons.Outlined.Palette) {onPage("appearance")}
                        HorizontalDivider();ActionRow("数据与诊断","备份、原音清理和处理队列",Icons.Outlined.Storage) {onPage("data")}
                    } }
                }
                "service"->item { config?.let { ServiceForm(it,busy,vm::save,vm::test,vm::preset) } ?: LinearProgressIndicator(Modifier.fillMaxWidth()) }
                "appearance"->item { Panel {
                    val theme by vm.preferences.themeMode.collectAsStateWithLifecycle("system")
                    SectionTitle("主题")
                    listOf("system" to "跟随系统","light" to "浅色","dark" to "深色").forEach { (key,label) -> Choice(label,theme==key) {vm.theme(key)} }
                } }
                "record"->{
                    item { Panel {
                        val mode by vm.preferences.recordingMode.collectAsStateWithLifecycle("lifelog")
                        val strategy by vm.preferences.launchStrategy.collectAsStateWithLifecycle("prompt")
                        SectionTitle("默认录音方式")
                        Choice("随身 · 边录边转写",mode=="lifelog") {vm.recordMode("lifelog")}
                        Choice("会议 · 停止后转写",mode=="meeting") {vm.recordMode("meeting")}
                        Toggle("每次启动先选方式",strategy=="prompt") {vm.launchStrategy(if(it) "prompt" else "default")}
                    } }
                    item { Panel {
                        val bitrate by vm.preferences.lifelogBitrateKbps.collectAsStateWithLifecycle(24)
                        val format by vm.preferences.meetingFormat.collectAsStateWithLifecycle("wav")
                        SectionTitle("音频质量")
                        listOf(16,24,32).forEach { rate -> Choice("随身 AAC · ${rate}kbps",bitrate==rate) {vm.bitrate(rate)} }
                        HorizontalDivider();Choice("会议 WAV · 约 115 MB/小时",format=="wav") {vm.meetingFormat("wav")}
                        Choice("会议 AAC · 约 28 MB/小时",format!="wav") {vm.meetingFormat("aac")}
                        Meta("原音一直保留在本机，只有手动清理时才删除。")
                    } }
                    item { Panel {
                        val haptic by vm.preferences.hapticEnabled.collectAsStateWithLifecycle(true)
                        val resume by vm.preferences.autoResumeAfterCall.collectAsStateWithLifecycle(false)
                        SectionTitle("反馈与来电")
                        Toggle("录音触觉反馈",haptic,vm::haptic)
                        Toggle("来电结束后自动继续",resume,vm::autoResume)
                        Meta("来电暂停需要电话状态权限。默认不会在通话后自动继续。")
                        TextButton(onClick={permissions.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))}) {Text(if(helper.hasPermission(Manifest.permission.READ_PHONE_STATE)) "电话状态权限已授予" else "授予电话状态权限")}
                        if(Build.VERSION.SDK_INT>=33) TextButton(onClick={permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))}) {Text(if(helper.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) "通知权限已授予" else "开启录音通知")}
                    } }
                    item { Panel {
                        val life by vm.preferences.lifelogTriggerDuration.collectAsStateWithLifecycle(2)
                        val meeting by vm.preferences.meetingTriggerDuration.collectAsStateWithLifecycle(4)
                        SectionTitle("双音量键快捷启动")
                        Meta("同时长按：达到随身时长后松手开始随身录音；继续按住至会议时长开始会议。录音时长按 1.2 秒停止。")
                        Text("随身阈值：$life 秒")
                        Slider(life.toFloat(),{vm.blindDuration("lifelog",it.toInt().coerceAtMost(meeting-1))},valueRange=1f..8f,steps=6)
                        Text("会议阈值：$meeting 秒")
                        Slider(meeting.toFloat(),{vm.blindDuration("meeting",it.toInt().coerceAtLeast(life+1))},valueRange=2f..15f,steps=12)
                        TextButton(onClick={helper.openAccessibilitySettings()}) {Text("打开系统无障碍设置")}
                        TextButton(onClick={helper.requestIgnoreBatteryOptimizations()}) {Text("设置后台录音电池策略")}
                    } }
                }
                "data"->{
                    item { Panel {
                        SectionTitle("记忆库")
                        Text("${snapshot.sessions.size} 次记录 · ${snapshot.transcripts.size} 段原稿",style=MaterialTheme.typography.titleMedium)
                        Meta("${snapshot.tasks.count { it.state=="ACTIVE" }} 项待办 · ${snapshot.jobs.count { it.state!="COMPLETE" }} 项处理任务")
                        Button(onClick={export.launch("说了啥-v${BuildConfig.VERSION_NAME}-备份.zip")},enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text("导出记忆库和原音备份")}
                        Meta("导出前先停止录音。备份包含原音和文字，不包含服务密钥；请妥善保存。")
                    } }
                    item { Panel {
                        SectionTitle("处理队列")
                        snapshot.jobs.filter { it.state!="COMPLETE" }.groupingBy {it.state}.eachCount().forEach { (state,count) -> Meta("$state · $count") }
                        OutlinedButton(onClick={vm.retry()},enabled=!busy) {Text("重试未完成任务")}
                        Meta("失败任务保留原稿和原音，不会在重试时覆盖已确认待办。")
                    } }
                    item { Panel {SectionTitle("手动清理");Meta("只删除结束会话的本机原音，保留原稿、整理结果和待办。正在录音的会话会跳过。")
                        OutlinedButton(onClick={cleanup=true},enabled=!busy) {Text("清理已结束会话的原音")}
                    } }
                    item { Meta("说了啥 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · 记忆库结构 6") }
                }
            }
        }
    }
    if(cleanup) AlertDialog(onDismissRequest={cleanup=false},title={Text("清理已结束会话的原音")},text={Text("清理后无法回听。建议先导出备份。文字和待办会保留。")},confirmButton={TextButton(onClick={cleanup=false;vm.clearAllAudio()}) {Text("清理原音")}},dismissButton={TextButton(onClick={cleanup=false}) {Text("取消")}})
}

@Composable private fun Choice(label:String,selected:Boolean,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {RadioButton(selected,onClick=onClick);TextButton(onClick=onClick) {Text(label,color=MaterialTheme.colorScheme.onSurface)} }
}
@Composable private fun Toggle(label:String,value:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically) {Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge);Switch(value,onChange)}
}
@Composable private fun ServiceForm(config:AiSettings,busy:Boolean,onSave:(AiSettings)->Unit,onTest:(AiSettings)->Unit,onPreset:(ProviderPreset)->Unit) {
    var provider by remember(config) {mutableStateOf(config.provider)}
    var base by remember(config) {mutableStateOf(config.baseUrl)}
    var key by remember(config) {mutableStateOf(config.apiKey)}
    var asr by remember(config) {mutableStateOf(config.asrModel)}
    var llm by remember(config) {mutableStateOf(config.llmModel)}
    var prompt by remember(config) {mutableStateOf(config.prompt)}
    var visible by remember {mutableStateOf(false)}
    fun draft()=AiSettings(provider,base,key,asr,llm,prompt)
    Panel {
        SectionTitle("服务配置")
        ProviderPreset.entries.forEach { preset -> Choice(preset.displayName,provider==preset.id) {
            onPreset(preset)
        } }
        OutlinedTextField(base,{if(base!=it) key="";base=it},Modifier.fillMaxWidth(),label={Text("服务地址")},singleLine=true)
        OutlinedTextField(key,{key=it},Modifier.fillMaxWidth(),label={Text("服务密钥")},singleLine=true,
            visualTransformation=if(visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon={IconButton(onClick={visible=!visible}) {Icon(if(visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,if(visible) "隐藏密钥" else "显示密钥")}})
        Meta("切换地址后单独配置该服务的密钥。云端使用 HTTPS；本地 HTTP 网关可不填密钥。")
        OutlinedTextField(asr,{asr=it},Modifier.fillMaxWidth(),label={Text("转写模型")},singleLine=true)
        OutlinedTextField(llm,{llm=it},Modifier.fillMaxWidth(),label={Text("整理模型")},singleLine=true)
        OutlinedTextField(prompt,{prompt=it},Modifier.fillMaxWidth(),label={Text("整理偏好")},minLines=3,maxLines=6)
        Meta("录音原稿只由转写产生，整理偏好不会改写或替换原稿。转写和提问会发送所需音频或文字到所选服务。")
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick={onTest(draft())},enabled=!busy,modifier=Modifier.weight(1f)) {Text("测试连接")}
            Button(onClick={onSave(draft())},enabled=!busy,modifier=Modifier.weight(1f)) {Text("保存配置")}
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
