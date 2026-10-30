package com.example.shuolesa

import com.example.shuolesa.network.CaptionAudio
import com.example.shuolesa.network.CaptionMode
import com.example.shuolesa.network.DashScopeRealtime
import com.example.shuolesa.network.LiveCaption
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** v5.2: the pure parts of 实时字幕 — caption state, audio helpers and the DashScope realtime protocol. */
class LiveCaptionsTest {
    @Test fun partialTextIsReplacedAndFinishedSentencesStay() {
        var c = LiveCaption(mode = CaptionMode.REALTIME)
        c = c.withText("今天先", false).withText("今天先对一下首页", false)
        assertEquals("今天先对一下首页", c.partial); assertTrue(c.lines.isEmpty())
        c = c.withText("今天先对一下首页。", true)
        assertEquals(listOf("今天先对一下首页。"), c.lines); assertEquals("", c.partial)
        assertEquals("今天先对一下首页。", c.latest)
        c = c.withText("  ", true)
        assertEquals("a blank result adds nothing", 1, c.lines.size)
    }

    @Test fun onlyTheLastLinesAreKept() {
        var c = LiveCaption()
        repeat(LiveCaption.MAX_LINES + 5) { c = c.withText("句子 $it", true) }
        assertEquals(LiveCaption.MAX_LINES, c.lines.size); assertEquals("句子 ${LiveCaption.MAX_LINES + 4}", c.lines.last())
    }

    @Test fun silenceIsNotSent() {
        assertTrue(CaptionAudio.isSilent(ShortArray(16_000)))
        assertTrue(CaptionAudio.isSilent(ShortArray(16_000) { if (it % 2 == 0) 50 else -50 }))
        assertFalse(CaptionAudio.isSilent(ShortArray(16_000) { if (it % 2 == 0) 4000 else -4000 }))
    }

    @Test fun pcmIsLittleEndian() {
        assertArrayEquals(byteArrayOf(0x34, 0x12, 0xFF.toByte(), 0xFF.toByte()), CaptionAudio.bytes(shortArrayOf(0x1234, -1), 2))
    }

    @Test fun runTaskAsksForSixteenKilohertzPcm() {
        val task = JSONObject(DashScopeRealtime.runTask("abc"))
        assertEquals("run-task", task.getJSONObject("header").getString("action"))
        assertEquals("duplex", task.getJSONObject("header").getString("streaming"))
        val payload = task.getJSONObject("payload")
        assertEquals(DashScopeRealtime.MODEL, payload.getString("model"))
        assertEquals("pcm", payload.getJSONObject("parameters").getString("format"))
        assertEquals(16000, payload.getJSONObject("parameters").getInt("sample_rate"))
        assertEquals("finish-task", JSONObject(DashScopeRealtime.finishTask("abc")).getJSONObject("header").getString("action"))
    }

    @Test fun serverEventsAreUnderstood() {
        assertEquals(DashScopeRealtime.Event.Started, DashScopeRealtime.parse("""{"header":{"event":"task-started","task_id":"abc"},"payload":{}}"""))
        assertEquals(DashScopeRealtime.Event.Text("今天先对", false), DashScopeRealtime.parse(
            """{"header":{"event":"result-generated"},"payload":{"output":{"sentence":{"begin_time":0,"end_time":null,"text":"今天先对","sentence_end":false}}}}"""))
        assertEquals(DashScopeRealtime.Event.Text("今天先对一下首页。", true), DashScopeRealtime.parse(
            """{"header":{"event":"result-generated"},"payload":{"output":{"sentence":{"text":"今天先对一下首页。","sentence_end":true}}}}"""))
        assertEquals(DashScopeRealtime.Event.Other, DashScopeRealtime.parse(
            """{"header":{"event":"result-generated"},"payload":{"output":{"sentence":{"heartbeat":true,"text":""}}}}"""))
        assertEquals(DashScopeRealtime.Event.Finished, DashScopeRealtime.parse("""{"header":{"event":"task-finished"}}"""))
        assertEquals(DashScopeRealtime.Event.Failed("InvalidApiKey", "Invalid API-key provided."),
            DashScopeRealtime.parse("""{"header":{"event":"task-failed","error_code":"InvalidApiKey","error_message":"Invalid API-key provided."}}"""))
        assertEquals(DashScopeRealtime.Event.Other, DashScopeRealtime.parse("not json"))
    }
}
