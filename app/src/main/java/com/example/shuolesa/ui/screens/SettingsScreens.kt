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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.shuolesa.BuildConfig
import com.example.shuolesa.data.prefs.*
import com.example.shuolesa.service.LiveActivity
import com.example.shuolesa.ui.SettingsViewModel
import com.example.shuolesa.ui.components.*
import com.example.shuolesa.util.PermissionHelper

@Composable fun SettingsScreen(vm:SettingsViewModel,page:String,onBack:()->Unit,onPage:(String)->Unit,onOutbound:()->Unit={},focusAddress:Boolean=false) {
    val context=LocalContext.current;val helper=remember { PermissionHelper(context) }
    val busy by vm.busy.collectAsStateWithLifecycle();val config by vm.config.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    var cleanup by remember { mutableStateOf(false) }
    var revoke by remember { mutableStateOf(false) }
    var permissionRefresh by remember { mutableIntStateOf(0) }
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionRefresh++ }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::export) }
    val pending by vm.pendingSave.collectAsStateWithLifecycle()
    val title=when(page) {"privacy"->"隐私与数据外发";"service"->"转写与整理";"record"->"录音与快捷启动";"appearance"->"外观";"data"->"数据与诊断";else->"设置"}
    Page(title,onBack) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(20.dp,8.dp,20.dp,32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            when(page) {
                "home"->{
                    item { Panel(color=MaterialTheme.colorScheme.primaryContainer) { Row(verticalAlignment=Alignment.CenterVertically) {
                        Ceer(CeerPose.IDLE,Modifier.size(84.dp),interactive=true)
                        Column(Modifier.padding(start=16.dp)) {
                            Text("说了啥",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("找回说过的话 · v${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.8f)) } } } }
                    item { GroupCard {
                        ActionRow("转写与整理",when { config?.configured==true && config?.split==true -> "已配置 · 转写和整理分开"
                            config?.configured==true -> "已配置服务"; else -> "还没连接服务，仍可本地录音" },Icons.Outlined.Cloud) {onPage("service")}
                        GroupDivider();ActionRow("录音与快捷启动","随身、会议、音量键与来电处理",Icons.Outlined.Mic) {onPage("record")}
                        GroupDivider();ActionRow("外观","跟随系统或手动切换",Icons.Outlined.Palette) {onPage("appearance")}
                        GroupDivider();ActionRow("数据与诊断","备份、原音清理和处理队列",Icons.Outlined.Storage) {onPage("data")}
                        GroupDivider();ActionRow("隐私","隐私同意、数据外发和撤回",Icons.Outlined.Lock) {onPage("privacy")}
                    } }
                }
                "service"->item {
                    val outboundOk by vm.outboundConsented.collectAsStateWithLifecycle(false)
                    val consentedBase by vm.outboundBase.collectAsStateWithLifecycle(null)
                    config?.let { ServiceForm(it,busy,{c,clear->vm.clearDraftChanged();vm.save(c,clear)},vm::test,vm::preset,outboundOk,onOutbound,focusAddress,
                        consentedBase=consentedBase,onSaveAndConfirm=vm::saveAndConfirm,onLlmPreset=vm::llmPreset,onClearLlmKey=vm::clearLlmKey) }
                        ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                "privacy"->{
                    item { Panel {
                        val outbound by vm.outboundConsented.collectAsStateWithLifecycle(false)
                        val confirmedBase by vm.outboundBase.collectAsStateWithLifecycle(null)
                        SectionTitle("数据外发")
                        // v4.3: the confirmed target may hold two addresses (转写 + 整理)
                        val confirmed=confirmedBase?.let { AppPreferences.targets(it) }.orEmpty()
                        Text(if(outbound) "已开启" else "未开启 · 录音只保存在本机",style=MaterialTheme.typography.bodyLarge)
                        // v4.1-a.2: same tag + warning as ⓪-2/⓪-3 (theme-aware colours)
                        confirmed.forEachIndexed { i,address -> if(confirmed.size>1) Meta(if(i==0) "转写" else "整理与提问")
                            Text(address,style=MaterialTheme.typography.bodyMedium);RegionLine(ServiceRegion.of(address),address) }
                        if(confirmed.isNotEmpty()) RegionWarning(worstRegion(confirmed))
                        Meta("开启后，原音片段和原话文本会发送到所选服务，用于转写、整理和提问。")
                        if(outbound) OutlinedButton(onClick={vm.revokeOutbound()},enabled=!busy) {Text("关闭数据外发")}
                        else Button(onClick=onOutbound,enabled=!busy) {Text("查看并开启")}
                    } }
                    item { Panel {
                        SectionTitle("隐私同意")
                        Meta("撤回后，App 回到首次使用的同意页：不录音、不联网。已保存的记录和原音保留在本机，可在 数据与诊断 中清理。")
                        OutlinedButton(onClick={revoke=true},enabled=!busy) {Text("撤回隐私同意")}
                    } }
                }
                "appearance"->item {
                    val theme by vm.preferences.themeMode.collectAsStateWithLifecycle("system")
                    AppearanceContent(theme,{vm.theme(it)},{vm.restoreAllHints()})
                }
                "record"->{
                    item { LiveActivityCard() }
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
                        val captions by vm.preferences.liveCaptions.collectAsStateWithLifecycle(true)
                        SectionTitle("实时字幕")
                        Toggle("录音时显示文字",captions,vm::liveCaptions)
                        Meta("需要已同意数据外发、设置好转写服务。阿里云百炼会边说边出字；其他服务每隔几秒上传一小段，没有声音的部分不上传。字幕只是预览，保存的原话仍以录完后的转写为准。会额外消耗一些转写额度。")
                    } }
                    item { Panel {
                        val haptic by vm.preferences.hapticEnabled.collectAsStateWithLifecycle(true)
                        val resume by vm.preferences.autoResumeAfterCall.collectAsStateWithLifecycle(false)
                        SectionTitle("反馈与来电")
                        Toggle("录音触觉反馈",haptic,vm::haptic)
                        Toggle("来电结束后自动继续",resume,vm::autoResume)
                        Meta("来电和网络通话（如微信语音）会自动暂停录音，无需电话权限。默认不会在通话后自动继续。")
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
                        Text("${snapshot.sessions.size} 次记录 · ${snapshot.transcripts.size} 段原话",style=MaterialTheme.typography.titleMedium)
                        Meta("${snapshot.tasks.count { it.state=="ACTIVE" }} 项待办 · ${snapshot.jobs.count { it.state!="COMPLETE" }} 项处理任务")
                        Button(onClick={export.launch("说了啥-v${BuildConfig.VERSION_NAME}-备份.zip")},enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text("导出记忆库和原音备份")}
                        Meta("导出前先停止录音。备份包含原音和文字，不包含服务密钥；请妥善保存。")
                    } }
                    item { Panel {
                        SectionTitle("处理队列")
                        snapshot.jobs.filter { it.state!="COMPLETE" }.groupingBy { com.example.shuolesa.ui.logic.UiStatus.jobLabel(it.state) }.eachCount().forEach { (label,count) -> Meta("$label · $count") }
                        OutlinedButton(onClick={vm.retry()},enabled=!busy) {Text("重试未完成任务")}
                        Meta("失败任务保留原话和原音，不会在重试时覆盖已确认待办。")
                    } }
                    item { Panel {SectionTitle("原音占用")
                        val usage by vm.audioUsage.collectAsStateWithLifecycle()
                        LaunchedEffect(snapshot) { vm.refreshAudioUsage(snapshot) }
                        Text(usage?.let { com.example.shuolesa.ui.logic.StorageFormat.bytes(it) } ?: "计算中…",style=MaterialTheme.typography.titleMedium)
                        Meta("只删除结束会话的本机原音，保留原话、要点和待办。正在录音的会话会跳过。")
                        OutlinedButton(onClick={cleanup=true},enabled=!busy) {Text("清理已结束会话的原音")}
                    } }
                    item { Meta("说了啥 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · 记忆库结构 ${com.example.shuolesa.data.db.DB_SCHEMA_VERSION}") }
                }
            }
        }
    }
    pending?.let { p -> ReconfirmBaseUrlSheet(p.config.provider,p.config.baseUrl,p.keyNotice,vm::cancelPendingSave,vm::confirmPendingSave,
        llmProviderId=p.config.llmProvider.takeIf { p.config.split },llmBaseUrl=p.config.llmBaseUrl.takeIf { p.config.split },llmKeyNotice=p.llmKeyNotice) }
    if(revoke) AlertDialog(onDismissRequest={revoke=false},title={Text("撤回隐私同意")},text={Text("撤回后不能录音和转写，直到再次同意。本机已有的记录和原音不会被删除。")},
        confirmButton={TextButton(onClick={revoke=false;vm.revokePrivacy()}) {Text("撤回")}},dismissButton={TextButton(onClick={revoke=false}) {Text("取消")}})
    if(cleanup) AlertDialog(onDismissRequest={cleanup=false},title={Text("清理已结束会话的原音")},text={Text("清理后无法回听。建议先导出备份。文字和待办会保留。")},confirmButton={TextButton(onClick={cleanup=false;vm.clearAllAudio()}) {Text("清理原音")}},dismissButton={TextButton(onClick={cleanup=false}) {Text("取消")}})
}

@Composable private fun Choice(label:String,selected:Boolean,onClick:()->Unit) { OptionRow(label,selected,onClick=onClick) }
@Composable private fun Toggle(label:String,value:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).toggleable(value,role=Role.Switch,onValueChange=onChange),verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge);Switch(value,null) }
}

/**
 * v5.0: 录音实时活动 (OPPO 流体云 / Android 16 实时更新). The notification asks for it, but the system also needs the app's
 * 「实时活动」 switch on, so this shows the switch's state, opens the right system page and can post a 10 s sample.
 */
@Composable private fun LiveActivityCard() {
    val context=LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { refresh++;onPauseOrDispose { } }
    val state=remember(refresh) { LiveActivity.state(context) }
    var sample by remember { mutableStateOf(false) }
    LaunchedEffect(sample) { if(sample) { kotlinx.coroutines.delay(10_000);LiveActivity.clearSample(context);sample=false } }
    Panel {
        SectionTitle("录音实时活动 · 流体云")
        Text(state.title,style=MaterialTheme.typography.bodyLarge)
        Meta(state.hint)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            if(state.canOpenSettings) FilledTonalButton(onClick={ LiveActivity.openSettings(context) }) { Text("去系统设置") }
            OutlinedButton(onClick={ LiveActivity.postSample(context);sample=true },enabled=!sample) { Text(if(sample) "10 秒内看顶部" else "试一下") }
        }
    }
}
@Composable private fun ServiceForm(config:AiSettings,busy:Boolean,onSave:(AiSettings,Boolean)->Unit,onTest:(AiSettings)->Unit,onPreset:(ProviderPreset)->Unit,
                                    outboundConsented:Boolean,onConfirmOutbound:()->Unit,focusAddress:Boolean,
                                    consentedBase:String?=null,onSaveAndConfirm:(AiSettings)->Unit={},
                                    onLlmPreset:(ProviderPreset?)->Unit={},onClearLlmKey:()->Unit={}) {
    var provider by remember(config) {mutableStateOf(config.provider)}
    var base by remember(config) {mutableStateOf(config.baseUrl)}
    var key by remember(config) {mutableStateOf(config.apiKey)}
    var asr by remember(config) {mutableStateOf(config.asrModel)}
    var llm by remember(config) {mutableStateOf(config.llmModel)}
    var prompt by remember(config) {mutableStateOf(config.prompt)}
    var hotwords by remember(config) {mutableStateOf(config.hotwords)}
    var diarize by remember(config) {mutableStateOf(config.diarize)}
    var visible by remember {mutableStateOf(false)}
    // v4.3: 整理与提问 service. The choice itself goes through onLlmPreset (like 转写 presets, it reloads the form).
    val llmProvider=config.llmProvider
    var llmBase by remember(config) {mutableStateOf(config.llmBaseUrl)}
    var llmKey by remember(config) {mutableStateOf(config.llmApiKey)}
    var llmVisible by remember {mutableStateOf(false)}
    fun draft()=AiSettings(provider,base,key,asr,llm,prompt,hotwords,diarize,
        llmProvider,if(llmProvider.isBlank()) "" else llmBase,if(llmProvider.isBlank()) "" else llmKey)
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
    Panel {
        SectionTitle("转写")
        Meta("把原音变成原话。")
        if(provider.isBlank()) Surface(color=MaterialTheme.colorScheme.tertiaryContainer,shape=MaterialTheme.shapes.medium) {
            Text("还没选择服务商。先选一个，再填服务密钥，就能把原音转成原话。",Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onTertiaryContainer) }
        ProviderPreset.asrPresets.forEach { preset -> Choice(preset.displayName,provider==preset.id) {
            onPreset(preset)
        } }
        // v4.1-a: editing the address no longer wipes the key; keys belong to the provider.
        // v4.1-a.1: "去修复" from the 服务地址被拦截 banner -> address field in error state and focused.
        val addressFocus=remember { FocusRequester() }
        var markAddress by remember { mutableStateOf(focusAddress) }
        LaunchedEffect(focusAddress) { if(focusAddress) runCatching { addressFocus.requestFocus() } }
        val blockedHttp=markAddress && base.trim().startsWith("http://",ignoreCase=true) && !AiSettings.isLocal(base)
        val addressSupport:(@Composable ()->Unit)?=if(blockedHttp) { { Text("http 地址不安全，请改用 https 或 localhost") } } else null
        OutlinedTextField(base,{base=it;if(!it.trim().startsWith("http://",ignoreCase=true)) markAddress=false},
            Modifier.fillMaxWidth().focusRequester(addressFocus),label={Text("服务地址")},singleLine=true,
            isError=blockedHttp,supportingText=addressSupport)
        OutlinedTextField(key,{key=it},Modifier.fillMaxWidth(),label={Text("服务密钥")},singleLine=true,
            visualTransformation=if(visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon={IconButton(onClick={visible=!visible}) {Icon(if(visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,if(visible) "隐藏密钥" else "显示密钥")}})
        Meta("密钥按服务商保存：改地址会沿用当前服务商的密钥，留空保存不会清掉已存的密钥。云端请用 HTTPS；HTTP 只允许 localhost / 127.0.0.1 / 10.0.2.2，局域网网关请用 HTTPS 或 adb reverse。")
        if(config.apiKey.isNotBlank()) TextButton(onClick={key="";onSave(draft(),true)},enabled=!busy) {Text("清除已保存的密钥")}
        OutlinedTextField(asr,{asr=it},Modifier.fillMaxWidth(),label={Text("转写模型")},singleLine=true)
        // v4.2: speaker separation + sentence times, and hotwords (both only where the service supports them)
        Toggle("区分说话人",diarize) { diarize=it }
        OutlinedTextField(hotwords,{hotwords=it},Modifier.fillMaxWidth(),label={Text("热词：人名、项目名、术语")},
            placeholder={Text("每行一个，例如：说了啥\n张三")},minLines=2,maxLines=5)
        Meta("阿里云百炼的 qwen-audio-3.1-asr-flash 会给每句话标出说话人和时间，点一句即可回听；会议结束后整场一起转写，说话人编号前后一致。其他服务暂不支持，这两项会被忽略。说话人只按声音区分，不识别是谁，名字由你在原话里设置。")
    }
    Panel {
        SectionTitle("整理与提问")
        Meta("把原话整理成要点和待办，并回答提问。可以和转写用不同的服务商，各自保存密钥。")
        Choice("与转写使用同一服务",llmProvider.isBlank()) { onLlmPreset(null) }
        ProviderPreset.llmPresets.forEach { preset -> Choice(preset.displayName,llmProvider==preset.id) { onLlmPreset(preset) } }
        if(llmProvider.isNotBlank()) {
            ProviderPreset.fromId(llmProvider)?.note?.let { Meta(it) }
            val llmBlocked=llmBase.trim().startsWith("http://",ignoreCase=true) && !AiSettings.isLocal(llmBase)
            val llmSupport:(@Composable ()->Unit)?=if(llmBlocked) { { Text("http 地址不安全，请改用 https 或 localhost") } } else null
            OutlinedTextField(llmBase,{llmBase=it},Modifier.fillMaxWidth(),label={Text("整理服务地址")},singleLine=true,
                isError=llmBlocked,supportingText=llmSupport)
            if(llmBase.isNotBlank()) RegionLine(ServiceRegion.of(llmBase),llmBase)
            OutlinedTextField(llmKey,{llmKey=it},Modifier.fillMaxWidth(),label={Text("整理服务密钥")},singleLine=true,
                visualTransformation=if(llmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon={IconButton(onClick={llmVisible=!llmVisible}) {Icon(if(llmVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,if(llmVisible) "隐藏密钥" else "显示密钥")}})
            if(config.llmApiKey.isNotBlank()) TextButton(onClick={llmKey="";onClearLlmKey()},enabled=!busy) {Text("清除整理服务的密钥")}
        }
        OutlinedTextField(llm,{llm=it},Modifier.fillMaxWidth(),label={Text("整理模型")},singleLine=true)
        if(llm.trim().lowercase().startsWith("gemini")) Meta("Gemini 的思考无法关闭，会以最低档运行；首字较慢，整理会多等一会儿。")
        OutlinedTextField(prompt,{prompt=it},Modifier.fillMaxWidth(),label={Text("整理偏好")},minLines=3,maxLines=6)
        Meta("原话只由转写产生，整理偏好不会改写或替换原话。转写和提问会发送所需音频或文字到所选服务。")
    }
    Panel {
        // Chief-of-staff spec: draft address(es) ≠ consented ones -> yellow banner + 测试连接 disabled.
        // v4.3: compared as a whole target (转写, plus 整理 when separate).
        val target=draft().outboundTarget
        if(TestButtonLogic.draftMismatch(consentedBase,target)) DraftChangedNotice(enabled=!busy) { onSaveAndConfirm(draft()) }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            // v4.1-a.1: disabled before data-outbound consent (the 1 s test WAV is outbound data); also blocked in code.
            OutlinedButton(onClick={onTest(draft())},enabled=TestButtonLogic.enabled(busy,consentedBase,target),modifier=Modifier.weight(1f)) {Text("测试连接")}
            Button(onClick={onSave(draft(),false)},enabled=!busy,modifier=Modifier.weight(1f)) {Text("保存配置")}
        }
        if(consentedBase==null) Row(verticalAlignment=Alignment.CenterVertically) {
            Meta("同意数据外发后才能测试连接")
            TextButton(onClick=onConfirmOutbound) { Text("去确认") }
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    }
}

/**
 * 测试连接 is enabled only when nothing is running and the draft address is the consented one.
 * v4.3: [draftBase] may be a 转写 + 整理 target ([AiSettings.outboundTarget]).
 */
object TestButtonLogic {
    fun enabled(busy:Boolean,consentedBase:String?,draftBase:String)=!busy && com.example.shuolesa.network.ConnectionTester.matchesConsent(consentedBase,draftBase)
    /** Yellow 地址已修改 banner: some address is consented, but not this draft. (No consent at all is a different notice.) */
    fun draftMismatch(consentedBase:String?,draftBase:String)=consentedBase!=null && !com.example.shuolesa.network.ConnectionTester.matchesConsent(consentedBase,draftBase)
}

/** Stateless (previewed): yellow banner under the address field — draft address isn't the consented one. */
@Composable fun DraftChangedNotice(enabled:Boolean=true,onSaveAndConfirm:()->Unit) {
    val c=com.example.shuolesa.theme.LocalRegionColors.current
    Surface(color=c.warnBg,shape=MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(start=12.dp,end=4.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(com.example.shuolesa.network.ConnectionTester.DRAFT_CHANGED_BANNER,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=c.warnText)
            TextButton(onClick=onSaveAndConfirm,enabled=enabled) { Text("保存并确认") }
        }
    }
}

/** Stateless preview helper: 测试连接 / 保存配置 row with the banner, as shown when the address was edited. */
@Composable fun DraftChangedFormPreviewContent() {
    Panel {
        OutlinedTextField("https://gw.example.com/v1",{},Modifier.fillMaxWidth(),label={Text("服务地址")},singleLine=true)
        DraftChangedNotice {}
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick={},enabled=false,modifier=Modifier.weight(1f)) {Text("测试连接")}
            Button(onClick={},modifier=Modifier.weight(1f)) {Text("保存配置")}
        }
    }
}

/**
 * 设置 › 外观（stateless, previewed in V41Previews）。底部「恢复所有提示」：清除所有「以后不提示」，
 * 无确认弹窗，Snackbar「已恢复」由 SettingsViewModel.restoreAllHints 发出。状态横幅本就不可关闭，不受影响。
 */
@Composable fun AppearanceContent(theme:String,onTheme:(String)->Unit,onRestoreHints:()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Panel {
            SectionTitle("主题")
            val keys=listOf("system","light","dark")
            SegmentedTabs(listOf("跟随系统","浅色","深色"),keys.indexOf(theme).coerceAtLeast(0),{ onTheme(keys[it]) },Modifier.fillMaxWidth())
        }
        GroupCard { ActionRow("恢复所有提示","重新显示点过「以后不提示」的提醒",Icons.Outlined.Refresh,onRestoreHints) }
    }
}
