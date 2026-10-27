package com.example.shuolesa

import com.example.shuolesa.data.model.AsrSegment
import com.example.shuolesa.data.model.AsrSegments
import com.example.shuolesa.data.prefs.AiSettings
import com.example.shuolesa.data.prefs.ServiceRegion
import com.example.shuolesa.ui.logic.*
import org.junit.Assert.*
import org.junit.Test

/** v4.2: sentence/speaker storage, splitting a meeting back into 片段, speaker names, export, region, hotwords. */
class SpeakerSegmentsTest {
    @Test fun storageRoundTripsSpeakersAndScope() {
        val json=AsrSegments.toJson(listOf(AsrSegment(" 先过排期。 ",1000,4000,"1"),AsrSegment("",5000,6000,"2"),
            AsrSegment("好的。",6100,7000,"2")),AsrSegments.SCOPE_SESSION)!!
        val text=AsrSegments.text(listOf(AsrSegment(" 先过排期。 ",1000,4000,"1"),AsrSegment("",5000,6000,"2"),AsrSegment("好的。",6100,7000,"2")))
        assertEquals("先过排期。\n好的。",text)
        val view=SegmentPlayback.view(text,json)
        assertEquals(SegmentMode.A_TIMESTAMPS,view.mode)
        assertEquals(listOf("先过排期。","好的。"),view.sentences.map { it.text })
        assertEquals(listOf("1","2"),view.sentences.map { it.speaker })
        assertTrue(view.sentences.all { it.sessionScoped })
        assertEquals(1,SegmentPlayback.sentenceForQuote(view,text,6))
    }
    @Test fun noTimesMeansNoTimestampStorage() {
        assertNull(AsrSegments.toJson(listOf(AsrSegment("一句话。",null,null)),null))
        assertEquals(SegmentMode.B_SEGMENT_START,SegmentPlayback.view("一句话。",null).mode)
    }
    @Test fun olderTimestampFormatStillParsesWithoutSpeakers() {
        val sentence=SegmentPlayback.view("a","""[{"text":"a","start":5}]""").sentences.single()
        assertNull(sentence.speaker);assertFalse(sentence.sessionScoped);assertEquals(5L,sentence.startMs)
    }
    @Test fun meetingSentencesSplitBackIntoSegments() {
        val split=AsrSegments.splitByChunk(listOf(AsrSegment("一",1000,2000,"1"),AsrSegment("跨界",299_000,302_000,"2"),
            AsrSegment("二",300_500,301_000,"1"),AsrSegment("无时间",null,null,"1")),listOf(10L to 0L,11L to 300_000L))
        assertEquals(listOf("一","跨界","无时间"),split.getValue(10).map { it.text })
        assertEquals(299_000L,split.getValue(10)[1].startMs)
        assertEquals(500L,split.getValue(11).single().startMs)
        assertEquals("1",split.getValue(11).single().speaker)
        assertTrue(AsrSegments.splitByChunk(emptyList(),listOf(1L to 0L)).getValue(1).isEmpty())
    }
    @Test fun speakerNamesAreScopedAndRemovable() {
        assertEquals("7/2",SpeakerNames.key(7,TimedSentence("x",0,null,"2",sessionScoped=false)))
        assertEquals("2",SpeakerNames.key(7,TimedSentence("y",0,null,"2",sessionScoped=true)))
        assertNull(SpeakerNames.key(7,TimedSentence("z",0)))
        val json=SpeakerNames.set(null,"2","  张三  ")
        assertEquals(mapOf("2" to "张三"),SpeakerNames.parse(json))
        assertEquals("张三",SpeakerNames.label(SpeakerNames.parse(json),"2","2"))
        assertEquals("说话人 2",SpeakerNames.label(emptyMap(),"7/2","2"))
        assertNull(SpeakerNames.label(emptyMap(),null,null))
        assertNull(SpeakerNames.set(json,"2"," "))
        assertEquals(emptyMap<String,String>(),SpeakerNames.parse("not json"))
    }
    @Test fun exportListsTimesAndSpeakerTurns() {
        val view=SegmentView(SegmentMode.A_TIMESTAMPS,listOf(TimedSentence("先过排期。",1000,null,"1",true),
            TimedSentence("接着说。",3000,null,"1",true),TimedSentence("好的。",65_000,null,"2",true)))
        val text=TranscriptExport.text("周会","10月27日 10:00 · 会议 · 30 分钟",listOf(ExportSegment(9,"片段 1 · 10:00–10:05",view)),mapOf("1" to "张三"))
        assertTrue(text,text.contains("[00:01] 张三：先过排期。\n[00:03] 接着说。\n[01:05] 说话人 2：好的。\n"))
        assertTrue(text.contains(TranscriptExport.NOTE))
        val plain=TranscriptExport.text("t","m",listOf(ExportSegment(1,"片段 1",
            SegmentView(SegmentMode.B_SEGMENT_START,listOf(TimedSentence("一句。",null))))),emptyMap())
        assertTrue(plain.endsWith("片段 1\n一句。\n"))
        assertFalse(plain.contains(TranscriptExport.NOTE))
    }
    @Test fun aliyunSingaporeIsOverseasAndBeijingDomestic() {
        assertEquals(ServiceRegion.OVERSEAS,ServiceRegion.of("https://dashscope-intl.aliyuncs.com/compatible-mode/v1"))
        assertEquals(ServiceRegion.OVERSEAS,ServiceRegion.of("https://ws-1.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1"))
        assertEquals(ServiceRegion.DOMESTIC,ServiceRegion.of("https://dashscope.aliyuncs.com/compatible-mode/v1"))
        assertEquals(ServiceRegion.DOMESTIC,ServiceRegion.of("https://ws-1.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"))
    }
    @Test fun hotwordsAreCleaned() {
        val s=AiSettings("aliyun","https://dashscope.aliyuncs.com/compatible-mode/v1","","a","b","",
            "张三\n 李四 ,张三、${"长".repeat(31)}；王五")
        assertEquals(listOf("张三","李四","王五"),s.hotwordList)
        assertTrue(AiSettings("x","https://a.example/v1","","a","b","").diarize)
    }
}
