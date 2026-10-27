package com.example.shuolesa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.shuolesa.data.prefs.ProviderPreset
import com.example.shuolesa.data.prefs.ServiceRegion
import com.example.shuolesa.data.prefs.KeyNotice
import com.example.shuolesa.theme.LocalRegionColors
import com.example.shuolesa.ui.components.*

/**
 * v4.1-a compliance screens (design: docs/design/v4.1 补充稿 ⓪-1 / ⓪-2 / ⓪-3).
 * Before privacy consent the app does not record, does not send anything and requests no permission.
 * v5.0: no service is preselected. A fresh install picks one (连上转写服务), then confirms sending data to *that* address.
 */

/** Thin progress bar: one capsule per step, filled up to the current one. */
@Composable private fun StepBar(step:Int,total:Int) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        repeat(total) { i -> Box(Modifier.weight(1f).height(4.dp).clip(CircleShape)
            .background(if(i<=step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) }
    }
}

/** An icon, a short statement and one line of detail; the consent pages' unit of information. */
@Composable private fun Fact(icon:ImageVector,title:String,body:String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.Top) {
        IconTile(icon,size=44.dp)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(title,style=MaterialTheme.typography.titleSmall)
            Meta(body)
        }
    }
}

@Composable private fun ConsentFrame(step:Int,total:Int,content:@Composable ColumnScope.()->Unit,actions:@Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=24.dp)) {
            StepBar(step,total)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top=28.dp,bottom=12.dp),
                verticalArrangement=Arrangement.spacedBy(18.dp)) { content() }
            Column(Modifier.fillMaxWidth().padding(vertical=16.dp),horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(4.dp),content=actions)
        }
    }
}
@Composable private fun PrimaryAction(text:String,enabled:Boolean=true,onClick:()->Unit) {
    Button(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(text,style=MaterialTheme.typography.titleSmall) }
}

/** ⓪-1 隐私同意 */
@Composable fun PrivacyConsentScreen(step:Int,total:Int,onAgree:()->Unit,onExit:()->Unit,fromShortcut:Boolean=false) {
    var policy by remember { mutableStateOf<String?>(null) }
    ConsentFrame(step,total,content={
        // v4.1-a.1: opened from the tile / volume-key trigger before consent. No recording, no error.
        if(fromShortcut) Meta("使用快捷录音前，需要先同意隐私政策")
        Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).align(Alignment.CenterHorizontally),contentAlignment=Alignment.Center) {
            Ceer(CeerPose.IDLE,Modifier.size(60.dp)) }
        Text("先说清楚你的数据",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.align(Alignment.CenterHorizontally))
        Fact(Icons.Outlined.Lock,"原音只存在这台手机里","录音文件不会自动上传，删除后无法恢复。")
        Fact(Icons.Outlined.CloudUpload,"转写时才会发送","开启转写后，原音和原话会发给你选择的服务，之后单独确认。")
        Fact(Icons.Outlined.VerifiedUser,"随时可撤回","在 设置 › 隐私 中撤回同意，或清除全部数据。")
        Row(Modifier.align(Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically) {
            Meta("请阅读")
            TextButton(onClick={policy="privacy"}) { Text("《隐私政策》") }
            Meta("和")
            TextButton(onClick={policy="terms"}) { Text("《用户协议》") }
        }
    },actions={
        PrimaryAction("同意并继续",onClick=onAgree)
        TextButton(onClick=onExit) { Text("不同意，退出") }
    })
    policy?.let { kind -> AlertDialog(onDismissRequest={policy=null},
        title={Text(if(kind=="privacy") "隐私政策（摘要）" else "用户协议（摘要）")},
        text={Text(if(kind=="privacy") PRIVACY_SUMMARY else TERMS_SUMMARY)},
        confirmButton={TextButton(onClick={policy=null}) {Text("知道了")}}) }
}

/**
 * ⓪-2 数据外发确认. [baseUrl] is the current service address (upgrade users see their configured one).
 * v4.3: [llmProviderId] / [llmBaseUrl] = a separate 整理与提问 service; then both addresses are listed.
 */
@Composable fun OutboundConsentScreen(step:Int,total:Int,providerId:String,baseUrl:String,onAgree:()->Unit,onLater:()->Unit,onBack:(()->Unit)?=null,
    llmProviderId:String?=null,llmBaseUrl:String?=null) {
    val provider=ProviderPreset.nameOf(providerId)
    val llm=llmBaseUrl?.takeIf { it.isNotBlank() && llmProviderId!=null }
    val llmProvider=llmProviderId?.let { ProviderPreset.nameOf(it) }
    ConsentFrame(step,total,content={
        onBack?.let { TextButton(onClick=it) { Text("返回") } }
        Text("转写需要把数据发出去",style=MaterialTheme.typography.headlineSmall)
        Meta(if(llm==null) "开启后，以下内容会发送到「$provider」处理：" else "开启后，以下内容会发送到两个服务处理：")
        Panel(Modifier.fillMaxWidth()) {
            Row { Text("原音片段",Modifier.weight(1f));Meta(if(llm==null) "用于转写成原话" else "「$provider」转写成原话") }
            Row { Text("原话文本",Modifier.weight(1f));Meta(if(llm==null) "用于要点、复盘、提问" else "「$llmProvider」整理要点、复盘、回答提问") }
        }
        AddressPanel(if(llm==null) "服务地址" else "转写服务地址",baseUrl)
        llm?.let { AddressPanel("整理服务地址",it) }
        RegionWarning(worstRegion(listOfNotNull(baseUrl,llm)))
        Meta("AI 生成的要点和复盘会标注「AI 生成」。未开启前，录音只保存在本机。")
    },actions={
        PrimaryAction("同意发送并开启转写",onClick=onAgree)
        TextButton(onClick=onLater) { Text("暂不开启，只录音") }
    })
}

@Composable internal fun RegionLine(region:ServiceRegion,baseUrl:String) {
    val secure=baseUrl.trim().startsWith("https://",ignoreCase=true)
    // 境内 ok 绿 / 局域网 run 蓝 / 未知、境外 warn 黄. v4.1-a.2: light/dark from theme LocalRegionColors.
    val colors=LocalRegionColors.current
    val (container,content)=when(region.tone) {
        ServiceRegion.Tone.OK -> colors.okBg to colors.okText
        ServiceRegion.Tone.RUN -> colors.runBg to colors.runText
        ServiceRegion.Tone.WARN -> colors.warnBg to colors.warnText
    }
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Surface(color=container,shape=CircleShape) { Text(region.tag,Modifier.padding(horizontal=10.dp,vertical=3.dp),style=MaterialTheme.typography.labelMedium,color=content) }
        Meta(region.note ?: if(secure) "HTTPS 加密传输" else "仅本机 / 模拟器调试地址")
    }
}

@Composable private fun AddressPanel(label:String,address:String) {
    Panel(Modifier.fillMaxWidth()) {
        Meta(label)
        Text(address,style=MaterialTheme.typography.bodyLarge)
        RegionLine(ServiceRegion.of(address),address)
    }
}

/** v4.3: the region that needs the strongest warning among several addresses (境外 > 未知 > the rest). */
internal fun worstRegion(addresses:List<String>):ServiceRegion {
    val regions=addresses.map { ServiceRegion.of(it) }
    return regions.find { it==ServiceRegion.OVERSEAS } ?: regions.find { it==ServiceRegion.UNKNOWN } ?: regions.firstOrNull() ?: ServiceRegion.UNKNOWN
}

/** Yellow box only for 境外 and 未知; 局域网 / 境内 show nothing. */
@Composable internal fun RegionWarning(region:ServiceRegion) {
    val text=region.warning ?: return
    val colors=LocalRegionColors.current
    Surface(color=colors.warningBoxBg,shape=MaterialTheme.shapes.medium) {
        Text(text,Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall,color=colors.warningBoxText)
    }
}

/** ⓪-3 改地址后重新确认. Shown before saving a base URL the user has not confirmed yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReconfirmBaseUrlSheet(providerId:String,baseUrl:String,keyNotice:KeyNotice,onCancel:()->Unit,onConfirm:()->Unit,
    llmProviderId:String?=null,llmBaseUrl:String?=null,llmKeyNotice:KeyNotice?=null) {
    val region=ServiceRegion.of(baseUrl)
    val provider=ProviderPreset.nameOf(providerId)
    // v4.3: a separate 整理与提问 service is listed (and its key checked) too.
    val llm=llmBaseUrl?.takeIf { it.isNotBlank() && llmProviderId!=null }
    val llmProvider=llmProviderId?.let { ProviderPreset.nameOf(it) }.orEmpty()
    val blocked=keyNotice.blocksConfirm || (llm!=null && llmKeyNotice?.blocksConfirm==true)
    ModalBottomSheet(onDismissRequest=onCancel) {
        Column(Modifier.padding(horizontal=24.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if(llm==null) "确认新的服务地址" else "确认转写与整理服务",style=MaterialTheme.typography.titleLarge)
            Meta(if(llm==null) "之后的原音和原话将发送到：" else "之后的原音发送到转写服务，原话发送到整理服务：")
            Panel(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surfaceContainerLow) {
                if(llm!=null) Meta("转写 · $provider")
                Text(baseUrl,style=MaterialTheme.typography.bodyLarge);RegionLine(region,baseUrl)
                KeyLine(keyNotice,provider)
            }
            llm?.let { Panel(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surfaceContainerLow) {
                Meta("整理与提问 · $llmProvider")
                Text(it,style=MaterialTheme.typography.bodyLarge);RegionLine(ServiceRegion.of(it),it)
                llmKeyNotice?.let { n -> KeyLine(n,llmProvider) }
            } }
            RegionWarning(worstRegion(listOfNotNull(baseUrl,llm)))
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick=onCancel,modifier=Modifier.weight(1f).heightIn(min=48.dp)) { Text("取消") }
                Button(onClick=onConfirm,enabled=!blocked,modifier=Modifier.weight(1f).heightIn(min=48.dp)) { Text("确认并保存") }
            }
        }
    }
}

/** v4.1-a.1 ⓪-3: only the address changed -> reuse this provider's saved key; provider changed -> 新服务需要填写密钥. */
@Composable private fun KeyLine(keyNotice:KeyNotice,provider:String) {
    if(keyNotice.blocksConfirm) Text(keyNotice.text,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.error)
    else Meta(if(keyNotice==KeyNotice.REUSE_SAVED) "会沿用「$provider」已保存的密钥" else keyNotice.text)
}

/**
 * v5.0 第 2 步「连上转写服务」：不预选任何服务。选一个服务商并填密钥，或选「自建网关 / 其他」去设置页填写，
 * 也可以先只录音。下一步在没选或密钥为空时不可点。数据外发的确认在这之后，针对选中的服务地址。
 */
@Composable fun ConnectServiceStep(step:Int,total:Int,onSaveKey:(ProviderPreset,String)->Unit,onConfigure:()->Unit,onSkip:()->Unit) {
    var choice by rememberSaveable { mutableStateOf<String?>(null) }
    var key by rememberSaveable { mutableStateOf("") }
    ConnectServiceContent(step,total,choice,key,{choice=it},{key=it},
        onNext={ val preset=choice?.let { ProviderPreset.fromId(it) }; if(preset!=null) onSaveKey(preset,key.trim()) else onConfigure() },onSkip=onSkip)
}

/** Providers a fresh install can pick here; the overseas and local entries live in 设置 › 转写与整理. */
private val ConnectChoices get()=ProviderPreset.asrPresets.filter { it!=ProviderPreset.LOCAL_OR_CUSTOM && it!=ProviderPreset.ALIYUN_INTL }
const val OTHER_SERVICE="other"

@Composable fun ConnectServiceContent(step:Int,total:Int,choice:String?,key:String,onChoice:(String)->Unit,onKey:(String)->Unit,onNext:()->Unit,onSkip:()->Unit) {
    ConsentFrame(step,total,content={
        Text("连上转写服务",style=MaterialTheme.typography.headlineSmall)
        Meta("录音会一直存在手机里。连上服务后，才能把原音转成原话，用来搜索、提问和生成复盘。")
        ConnectChoices.forEach { preset ->
            ServiceOption(choice==preset.id,preset.displayName,ServiceRegion.of(preset.defaultBaseUrl).tag,"填一个密钥即可，地址和模型自动配置",{onChoice(preset.id)}) {
                if(choice==preset.id) OutlinedTextField(key,onKey,Modifier.fillMaxWidth(),label={Text("服务密钥")},singleLine=true,visualTransformation=PasswordVisualTransformation())
            }
        }
        ServiceOption(choice==OTHER_SERVICE,"自建网关 / 其他服务",null,"在设置里填地址、密钥和模型",{onChoice(OTHER_SERVICE)}) {}
    },actions={
        PrimaryAction("下一步",enabled=choice==OTHER_SERVICE || (choice!=null && key.isNotBlank()),onClick=onNext)
        TextButton(onClick=onSkip) { Text("先只录音，稍后再连") }
    })
}

@Composable private fun ServiceOption(selected:Boolean,title:String,tag:String?,body:String,onClick:()->Unit,extra:@Composable ColumnScope.()->Unit) {
    Panel(Modifier.fillMaxWidth(),onClick,color=if(selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.45f) else MaterialTheme.colorScheme.surface,padding=16.dp) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            RadioButton(selected,null);Text(title,Modifier.weight(1f).padding(start=12.dp),style=MaterialTheme.typography.titleSmall)
            tag?.let { Text(it,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
        Meta(body);extra()
    }
}

/** Fresh install last step: 权限。只在用户点击时申请。 */
@Composable fun PermissionStep(step:Int,total:Int,micGranted:Boolean,notificationNeeded:Boolean,notificationGranted:Boolean,
                               onRequest:()->Unit,onDone:()->Unit) {
    ConsentFrame(step,total,content={
        Text("最后，开两个权限",style=MaterialTheme.typography.headlineSmall)
        Fact(Icons.Outlined.Mic,"麦克风${if(micGranted) " · 已授予" else ""}","用于录音。只在你开始录音时使用。")
        if(notificationNeeded) Fact(Icons.Outlined.Notifications,"录音通知${if(notificationGranted) " · 已授予" else ""}","录音时在通知栏和流体云显示计时，方便随时暂停或停止。")
        Meta("来电和网络通话会自动暂停录音，不需要电话权限。")
    },actions={
        if(!micGranted || (notificationNeeded && !notificationGranted)) PrimaryAction("授予权限",onClick=onRequest)
        TextButton(onClick=onDone) { Text(if(micGranted) "完成" else "稍后再说") }
    })
}

// TODO(产品)：替换为正式隐私政策 / 用户协议的 HTTPS 链接（上架前必须）。
private const val PRIVACY_SUMMARY = """说了啥会在你主动开始时录音，原音保存在本机。
仅当你开启转写后，原音片段和原话文本才会发送到你在设置中选择的服务用于转写、整理和问答；服务方按其隐私政策处理。
App 不收集设备标识、通讯录、位置或电话状态。服务密钥加密保存在本机，不进入备份。
你可以随时在 设置 › 隐私 中撤回同意、关闭数据外发，或清理原音。"""
private const val TERMS_SUMMARY = """请仅在合法、已告知相关人员的场合录音。AI 生成的要点和复盘可能有误，请以原话和原音为准。"""
