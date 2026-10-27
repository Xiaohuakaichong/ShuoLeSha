package com.example.shuolesa

import com.example.shuolesa.ui.logic.*
import org.junit.Assert.*
import org.junit.Test

class SegmentPlaybackTest {
    private val text = "好，那我们先说结论。我们把周五的版本先定下来，这次优先做好录音和查找。\n搜索结果需要能回到对应的原话。待办先确认"
    private val json = """[{"text":"好，那我们先说结论。","start":312000},{"text":"我们把周五的版本先定下来。","start":315000,"end":320000},
        {"text":"没有时间的句子。"},{"text":"秒为单位","start_s":326.5}]"""

    @Test fun nullOrBadJsonMeansB() {
        for (j in listOf(null, "", "  ", "not json", "[]", "{}", """[{"text":"x"}]""", """[{"start":1}]"""))
            assertEquals(j, SegmentMode.B_SEGMENT_START, SegmentPlayback.view(text, j).mode)
    }
    @Test fun bSplitsBySentenceAndNewline() {
        val v = SegmentPlayback.view(text, null)
        assertEquals(listOf("好，那我们先说结论。", "我们把周五的版本先定下来，这次优先做好录音和查找。", "搜索结果需要能回到对应的原话。", "待办先确认"), v.sentences.map { it.text })
        assertEquals(5000L, SegmentPlayback.seekFor(v, 2, base = 5000)) // B: always segment start
        assertNull(SegmentPlayback.current(v, 999_999))
    }
    @Test fun aParsesMsSecondsAndMissing() {
        val v = SegmentPlayback.view(text, json)
        assertEquals(SegmentMode.A_TIMESTAMPS, v.mode)
        assertEquals(listOf(312000L, 315000L, null, 326500L), v.sentences.map { it.startMs })
        assertEquals("05:12", SegmentPlayback.timeLabel(312000)); assertEquals("—", SegmentPlayback.timeLabel(null))
    }
    @Test fun aSeekAndFollow() {
        val v = SegmentPlayback.view(text, json)
        assertEquals(315000L, SegmentPlayback.seekFor(v, 1, 0))
        assertEquals(1_000_000L + 314000L, SegmentPlayback.seekFor(v, 1, 1_000_000, leadMs = 1000)) // from quote: 1 s earlier
        assertEquals(1_000L, SegmentPlayback.seekFor(v, 2, 1_000)) // missing time -> segment start, no error
        assertEquals(0L, SegmentPlayback.seekFor(SegmentView(SegmentMode.A_TIMESTAMPS, listOf(TimedSentence("a", 500))), 0, 0, 1000))
        assertNull(SegmentPlayback.current(v, 100))
        assertEquals(0, SegmentPlayback.current(v, 313000)); assertEquals(1, SegmentPlayback.current(v, 320000)); assertEquals(3, SegmentPlayback.current(v, 400000))
    }
    @Test fun customParserIsPluggable() {
        val v = SegmentPlayback.view(text, "anything") { listOf(TimedSentence("x", 0)) }
        assertEquals(SegmentMode.A_TIMESTAMPS, v.mode)
    }
    @Test fun quoteToSentence() {
        val v = SegmentPlayback.view(text, null)
        assertEquals(1, SegmentPlayback.sentenceForQuote(v, text, text.indexOf("周五")))
        assertEquals(0, SegmentPlayback.sentenceForQuote(v, text, 0))
    }
    @Test fun followPausesFiveSecondsAfterManualScroll() {
        val p = FollowPolicy()
        assertTrue(p.shouldFollow(0, true)); p.onUserScroll(1000)
        assertFalse(p.shouldFollow(5999, true)); assertTrue(p.shouldFollow(6000, true)); assertFalse(p.shouldFollow(9000, false))
    }
}
