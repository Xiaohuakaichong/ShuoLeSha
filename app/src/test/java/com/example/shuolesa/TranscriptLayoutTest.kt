package com.example.shuolesa

import com.example.shuolesa.data.model.EvidenceRef
import com.example.shuolesa.ui.logic.*
import org.junit.Assert.*
import org.junit.Test

/** v4.2 continuous 原话 (design 说了啥 4.2): turns, speaker filter, capture marks, 发言占比, quote labels, export marks. */
class TranscriptLayoutTest {
    private fun t(text:String,start:Long?,end:Long?=null,speaker:String?=null,session:Boolean=true)=TimedSentence(text,start,end,speaker,session)
    private fun seg(id:Long,index:Int,vararg s:TimedSentence,text:String?=null)=TranscriptSegment(id,index,"片段 ${index+1}",
        text ?: s.joinToString("\n") { it.text },SegmentView(if(s.any { it.startMs!=null }) SegmentMode.A_TIMESTAMPS else SegmentMode.B_SEGMENT_START,s.toList()))
    private val meeting=listOf(
        seg(1,0,t("先过排期。",0,2000,"1"),t("好。",2100,3000,"1"),t("我来跟。",3500,5000,"2")),
        seg(2,1,t("材料周三发。",0,4000,"2")))

    @Test fun sentencesOfOneSpeakerFormTurnsAcrossSegments() {
        val rows=TranscriptLayout.rows(meeting)
        assertEquals(listOf("d:1","t:1:0","t:1:2","d:2","t:2:0"),rows.map { it.key })
        val first=rows[1] as TranscriptRow.Turn
        assertEquals("1",first.speakerKey);assertEquals(2,first.sentences.size);assertEquals(0L,first.startMs)
        assertTrue(first.contains(1));assertFalse(first.contains(2))
    }
    @Test fun speakerFilterKeepsOnlyThatSpeakersTurns() {
        assertEquals(listOf("d:1","t:1:2","d:2","t:2:0"),TranscriptLayout.rows(meeting,speakerKey="2").map { it.key })
        assertEquals(listOf("d:1","t:1:0"),TranscriptLayout.rows(meeting,speakerKey="1").map { it.key })
    }
    @Test fun noSpeakersUntimedAndPendingSegments() {
        val segments=listOf(seg(1,0,t("a。",0),t("b。",1000)),seg(2,1,t("c。",null),t("d。",null)),seg(3,2,text=""))
        val rows=TranscriptLayout.rows(segments)
        assertEquals(listOf("d:1","t:1:0","t:1:1","d:2","t:2:0","d:3","p:3"),rows.map { it.key })
        val untimed=rows[4] as TranscriptRow.Turn
        assertFalse(untimed.timed);assertEquals(2,untimed.sentences.size);assertNull(untimed.speakerKey)
        assertFalse((rows[3] as TranscriptRow.Divider).timed)
        assertTrue(TranscriptLayout.speakers(segments).isEmpty())
        assertTrue(TranscriptLayout.share(segments).isEmpty())
        // a filter hides 片段 without that speaker, including pending ones
        assertTrue(TranscriptLayout.rows(segments,speakerKey="1").isEmpty())
    }
    @Test fun marksSitAfterWhatWasBeingSaid() {
        val segments=listOf(seg(1,0,t("一。",0,4000,"1"),t("二。",5000,8000,"2"),t("三。",9000,12000,"1")),seg(2,1,t("四。",null)))
        val rows=TranscriptLayout.rows(segments,mapOf(1L to listOf(9000L,4000L,20000L),2L to listOf(1000L)))
        assertEquals(listOf("d:1","t:1:0","m:1:4000","t:1:1","t:1:2","m:1:9000","m:1:20000","d:2","m:2:1000","t:2:0"),rows.map { it.key })
        // filtering by speaker hides marks
        assertTrue(TranscriptLayout.rows(segments,mapOf(1L to listOf(4000L)),"1").none { it is TranscriptRow.Mark })
    }
    @Test fun perSegmentSpeakerNumbersAreNotOfferedAsFilter() {
        val lifelog=listOf(seg(1,0,t("甲。",0,1000,"1",false)),seg(2,1,t("乙。",0,1000,"1",false)))
        assertTrue(TranscriptLayout.speakers(lifelog).isEmpty())
        assertEquals(mapOf("1/1" to 0,"2/1" to 1),TranscriptLayout.slots(lifelog))
        val one=TranscriptLayout.speakers(lifelog.take(1)).single()
        assertEquals("1/1",one.key);assertEquals("1",one.speaker);assertEquals(0,one.slot)
        val options=TranscriptLayout.speakers(meeting)
        assertEquals(listOf("1","2"),options.map { it.key });assertEquals(listOf(0,1),options.map { it.slot })
    }
    @Test fun speakingShareUsesSentenceTimes() {
        val segments=listOf(seg(1,0,t("一。",0,3000,"1"),t("二。",3000,4000,"2"),t("三。",4000,null,"1")))
        val share=TranscriptLayout.share(segments).associate { it.first.key to it.second }
        assertEquals(5f/6,share.getValue("1"),0.001f);assertEquals(1f/6,share.getValue("2"),0.001f)
        // an open-ended sentence counts until the next one starts, at most 10 s
        val gap=TranscriptLayout.share(listOf(seg(1,0,t("一。",0,null,"1"),t("二。",60_000,61_000,"2")))).associate { it.first.key to it.second }
        assertEquals(10f/11,gap.getValue("1"),0.001f)
    }
    @Test fun rowOfFindsSentenceOrDivider() {
        val rows=TranscriptLayout.rows(meeting)
        assertEquals(2,TranscriptLayout.rowOf(rows,1,2))
        assertEquals(1,TranscriptLayout.rowOf(rows,1,1))
        assertEquals(3,TranscriptLayout.rowOf(rows,2,null))
        assertEquals(-1,TranscriptLayout.rowOf(rows,9,0))
    }
    @Test fun quoteLabelNamesSpeakerSegmentAndTime() {
        val names=mapOf("1" to "张三")
        assertEquals("张三 · 片段 1 · 00:00",TranscriptLayout.refLabel(meeting,names,EvidenceRef(1,0,5,"先过排期。")))
        val text=meeting[0].text;val at=text.indexOf("我来跟")
        assertEquals("说话人 2 · 片段 1 · 00:03",TranscriptLayout.refLabel(meeting,names,EvidenceRef(1,at,at+3,"我来跟")))
        assertEquals("片段 1",TranscriptLayout.refLabel(listOf(seg(1,0,t("一句。",null))),names,EvidenceRef(1,0,3,"一句。")))
        assertEquals("原话来源",TranscriptLayout.refLabel(meeting,names,EvidenceRef(7,0,1,"x")))
    }
    @Test fun marksMapToSegmentsByDuration() {
        val chunks=listOf(1L to 5000L,2L to 5000L)
        assertEquals(mapOf(1L to listOf(1000L),2L to listOf(1000L,5000L)),MarkLayout.toChunks(listOf(12000L,6000L,1000L,6000L,13000L),chunks))
        assertTrue(MarkLayout.toChunks(emptyList(),chunks).isEmpty())
        assertTrue(MarkLayout.toChunks(listOf(1000L),emptyList()).isEmpty())
        assertEquals(0L,MarkLayout.seekFor(3000));assertEquals(4000L,MarkLayout.seekFor(9000))
    }
    @Test fun exportListsMarksUnderSegmentHeader() {
        val text=TranscriptExport.text("t","m",listOf(ExportSegment(1,"片段 1",SegmentView(SegmentMode.B_SEGMENT_START,listOf(TimedSentence("一句。",null))),
            listOf(190_000L,52_000L))),emptyMap())
        assertTrue(text,text.endsWith("片段 1\n标记：00:52、03:10\n一句。\n"))
    }
    @Test fun avatarAndSpeedLabels() {
        assertEquals("张",com.example.shuolesa.ui.screens.avatarText("张三"))
        assertEquals("2",com.example.shuolesa.ui.screens.avatarText("说话人 2"))
        assertEquals("1.5×",com.example.shuolesa.ui.screens.speedLabel(1.5f))
    }
}
