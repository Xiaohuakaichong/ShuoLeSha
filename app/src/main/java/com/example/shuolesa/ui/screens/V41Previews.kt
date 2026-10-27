package com.example.shuolesa.ui.screens

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.shuolesa.audio.CapturePhase
import com.example.shuolesa.audio.CaptureState
import com.example.shuolesa.data.db.TaskEntity
import com.example.shuolesa.data.model.EvidenceRef
import com.example.shuolesa.data.model.TaskState
import com.example.shuolesa.theme.ShuoLeSaTheme
import com.example.shuolesa.ui.MemoryCard
import com.example.shuolesa.ui.RecallState
import com.example.shuolesa.ui.logic.*
import java.time.LocalDate

/* v4.1-b: light + dark @Preview for every new/reworked screen. Fake data only, no ViewModel. */

private object Fake {
    const val T=1_792_000_000_000L
    val cards=listOf(
        MemoryCard("a","s1","t1","和设计对齐首页横幅","失败优先，其次等配置，最后等网络。",T,"meeting",1_860_000,"DONE",2,false),
        MemoryCard("b","s2",null,"随身记录","原音已保存，等网络恢复后自动转写。",T-3_600_000,"lifelog",660_000,"WAITING_NETWORK",0,false))
    val banner=BannerLogic.pick(listOf(BannerInput("s2","WAITING_NETWORK","NETWORK",null),BannerInput("s3","WAITING_NETWORK","NETWORK",null)),false)
    val ref=EvidenceRef(1,0,9,"今天先对一下首页。")
    val tasks=listOf(TaskEntity("k1","s1","周五前把首页横幅文案发给设计",TaskState.CANDIDATE,"[]",T,"周五"),
        TaskEntity("k2","s1","确认 StepFun 套餐价格",TaskState.CANDIDATE,"[]",T))
    val sentences=SegmentPlayback.view("今天先对一下首页。失败的要放最前面。等网络的放最后。",
        """[{"text":"今天先对一下首页。","start":0,"end":2100},{"text":"失败的要放最前面。","start":2100,"end":4800},{"text":"等网络的放最后。","start":4800,"end":7000}]""")
    /** ⑥-B: no timestamps (segmentsJson null). */
    val untimed=SegmentPlayback.view("下午再看一下发布清单。快捷方式只做打开。",null)
}

// UI review P2: Surface so light previews don't render on a transparent/black background.
@Composable private fun Themed(dark:Boolean,content:@Composable ()->Unit)=ShuoLeSaTheme(darkTheme=dark) {
    androidx.compose.material3.Surface(color=androidx.compose.material3.MaterialTheme.colorScheme.background,content=content) }

@Composable private fun MemoryP(dark:Boolean)=Themed(dark) { MemoryContent(MemoryUi(Fake.cards,Fake.banner,false,true),"all",null,{},{},{},{_,_->},{},{},{},{},{},{},{}) }
@Preview(name="② 记忆 浅色") @Composable fun MemoryLightPreview()=MemoryP(false)
@Preview(name="② 记忆 深色",uiMode=Configuration.UI_MODE_NIGHT_YES) @Composable fun MemoryDarkPreview()=MemoryP(true)

@Composable private fun MemoryBlindP(dark:Boolean)=Themed(dark) { MemoryContent(MemoryUi(emptyList(),null,true,false),"all",null,{},{},{},{_,_->},{},{},{},{},{},{},{}) }
@Preview(name="⑦ 盲启失败卡 浅色") @Composable fun BlindLightPreview()=MemoryBlindP(false)
@Preview(name="⑦ 盲启失败卡 深色",uiMode=Configuration.UI_MODE_NIGHT_YES) @Composable fun BlindDarkPreview()=MemoryBlindP(true)

@Composable private fun TodayP(dark:Boolean)=Themed(dark) {
    TodayContent(TodayUi(LocalDate.of(2026,10,14),true,"上午和设计对齐了首页横幅的优先级，下午整理了 v4.1 的发布清单。",Fake.T,5,6,1,false,
        listOf(Fake.ref to "失败的要放最前面"),Fake.cards,2_520_000,null),false,{},{},{},{_,_->},{}) }
@Preview(name="④ 今日 浅色") @Composable fun TodayLightPreview()=TodayP(false)
@Preview(name="④ 今日 深色",uiMode=Configuration.UI_MODE_NIGHT_YES) @Composable fun TodayDarkPreview()=TodayP(true)

@Composable private fun TasksP(dark:Boolean)=Themed(dark) { TasksContent(Fake.tasks,TaskState.CANDIDATE,false,{},{_,_->},{}) }
@Preview(name="待办 浅色") @Composable fun TasksLightPreview()=TasksP(false)
@Preview(name="待办 深色",uiMode=Configuration.UI_MODE_NIGHT_YES) @Composable fun TasksDarkPreview()=TasksP(true)

@Composable private fun RecallP(dark:Boolean)=Themed(dark) { RecallContent(RecallState(query="横幅"),"横幅",{},"all",null,null,{},{},{},{},{},{}) }
@Preview(name="搜索 浅色") @Composable fun RecallLightPreview()=RecallP(false)
@Preview(name="搜索 深色",uiMode=Configuration.UI_MODE_NIGHT_YES) @Composable fun RecallDarkPreview()=RecallP(true)

@Composable private fun CaptureP(dark:Boolean)=Themed(dark) {
    CaptureContent(CaptureState(CapturePhase.RECORDING,"s1","lifelog",754_000,3,0.2f),List(48) { (it%7)/20f },2,{},{},{}) }
@Preview(name="⑤ 录音 浅色") @Composable fun CaptureLightPreview()=CaptureP(false)
@Preview(name="⑤ 录音 深色",uiMode=Configuration.UI_MODE_NIGHT_YES) @Composable fun CaptureDarkPreview()=CaptureP(true)

@Composable private fun SentencesP(dark:Boolean)=Themed(dark) { androidx.compose.foundation.layout.Column {
    Fake.sentences.sentences.forEachIndexed { i,s -> SentenceRow(s,true,i==1,i==2,null,"") {} } } }
@Preview(name="⑥-A 原话句子 浅色") @Composable fun SentencesLightPreview()=SentencesP(false)
@Preview(name="⑥-A 原话句子 深色",uiMode=Configuration.UI_MODE_NIGHT_YES) @Composable fun SentencesDarkPreview()=SentencesP(true)

@Composable private fun ConnectP(dark:Boolean)=Themed(dark) { ConnectServiceContent(1,4,null,"",{},{},{},{}) }
@Preview(name="① 连上转写服务 浅色") @Composable fun ConnectLightPreview()=ConnectP(false)
@Preview(name="① 连上转写服务 深色",uiMode=Configuration.UI_MODE_NIGHT_YES) @Composable fun ConnectDarkPreview()=ConnectP(true)

// v5.0: nothing preselected; picking a provider opens its key field.
@Composable private fun ConnectPickedP(dark:Boolean)=Themed(dark) { ConnectServiceContent(1,4,"aliyun","sk-demo",{},{},{},{}) }
@Preview(name="① 已选服务商 浅色") @Composable fun ConnectPickedLightPreview()=ConnectPickedP(false)
@Preview(name="① 已选服务商 深色",uiMode=Configuration.UI_MODE_NIGHT_YES) @Composable fun ConnectPickedDarkPreview()=ConnectPickedP(true)

// 设置 › 外观 with「恢复所有提示」.
@Composable private fun AppearanceP(dark:Boolean)=Themed(dark) { AppearanceContent(if(dark) "dark" else "system",{},{}) }
@Preview(name="设置 外观 浅色") @Composable fun AppearanceLightPreview()=AppearanceP(false)
@Preview(name="设置 外观 深色",uiMode=Configuration.UI_MODE_NIGHT_YES) @Composable fun AppearanceDarkPreview()=AppearanceP(true)

// qa v4.1-final P1 (final spec): address edited -> yellow banner「地址已修改，需重新确认数据外发 · 保存并确认」, 测试连接 disabled.
@Composable private fun DraftChangedP(dark:Boolean)=Themed(dark) { DraftChangedFormPreviewContent() }
@Preview(name="设置 地址已修改 浅色") @Composable fun DraftChangedLightPreview()=DraftChangedP(false)
@Preview(name="设置 地址已修改 深色",uiMode=Configuration.UI_MODE_NIGHT_YES) @Composable fun DraftChangedDarkPreview()=DraftChangedP(true)

// UI review P2: ⑥-B (no timestamps) and a mixed A/B record.
@Composable private fun SegmentHeader(title:String,b:Boolean) {
    androidx.compose.foundation.layout.Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.material3.Text(title,androidx.compose.ui.Modifier.weight(1f),style=androidx.compose.material3.MaterialTheme.typography.labelLarge)
        if(b) androidx.compose.material3.TextButton(onClick={}) { androidx.compose.material3.Text("▶ 从片段开头回听") } } }
@Composable private fun SegmentB() { SegmentHeader("片段 2 · 14:05–14:11",true)
    Fake.untimed.sentences.forEach { SentenceRow(it,false,false,false,null,"") {} }
    com.example.shuolesa.ui.components.Meta("暂时只能从片段开头回听") }
@Composable private fun SegmentA() { SegmentHeader("片段 1 · 14:00–14:05",false)
    Fake.sentences.sentences.forEachIndexed { i,s -> SentenceRow(s,true,i==1,false,null,"") {} } }
@Composable private fun SegmentBP(dark:Boolean)=Themed(dark) { androidx.compose.foundation.layout.Column { SegmentB() } }
@Preview(name="⑥-B 原话句子 浅色") @Composable fun SegmentBLightPreview()=SegmentBP(false)
@Preview(name="⑥-B 原话句子 深色",uiMode=Configuration.UI_MODE_NIGHT_YES) @Composable fun SegmentBDarkPreview()=SegmentBP(true)
@Composable private fun MixedABP(dark:Boolean)=Themed(dark) { androidx.compose.foundation.layout.Column { SegmentA();SegmentB() } }
@Preview(name="⑥ A/B 混排 浅色") @Composable fun MixedABLightPreview()=MixedABP(false)
@Preview(name="⑥ A/B 混排 深色",uiMode=Configuration.UI_MODE_NIGHT_YES) @Composable fun MixedABDarkPreview()=MixedABP(true)
