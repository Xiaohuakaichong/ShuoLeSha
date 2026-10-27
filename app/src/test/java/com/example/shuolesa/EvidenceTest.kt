package com.example.shuolesa

import com.example.shuolesa.data.model.*
import com.example.shuolesa.network.AiClient
import com.example.shuolesa.network.AiException
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import com.example.shuolesa.data.prefs.AiSettings

class EvidenceTest {
    /** v4.1-a: HTTP only for the network_security_config whitelist; LAN IPs must use HTTPS / adb reverse. */
    @Test fun localHttpRequiresAnActualPrivateAddress() {
        fun config(url:String)=AiSettings("custom",url,"","asr","llm","")
        assertTrue(config("http://localhost:8000/v1").local)
        assertTrue(config("http://127.0.0.1:8000/v1").local)
        assertTrue(config("http://10.0.2.2:8000/v1").local)
        assertFalse(config("http://192.168.1.8/v1").local)
        val lan=assertThrows(IllegalArgumentException::class.java) {config("http://192.168.1.8/v1").validate()}
        assertTrue(lan.message!!.contains("adb reverse"))
        assertThrows(IllegalArgumentException::class.java) {config("http://api.example.com/v1").validate()}
        config("https://192.168.1.8/v1").validate()
        assertFalse(config("http://10.evil.example/v1").local)
        assertFalse(config("http://192.168.evil.example/v1").local)
        assertThrows(IllegalArgumentException::class.java) {config("http://10.evil.example/v1").validate()}
    }
    @Test fun exactRangesRoundTripAndRejectParaphrases() {
        val text="甲说：周五交付第一版。乙说：先确认需求。"
        val ref=EvidenceRef.locate(1,text,"周五交付第一版。")!!
        assertTrue(ref.isValidFor(text));assertFalse(ref.isValidFor(text.replace("周五","周六")))
        assertEquals(listOf(ref),EvidenceRef.fromJson(EvidenceRef.toJson(listOf(ref))))
        assertNull(EvidenceRef.locate(1,text,"下周交付"))
    }
    @Test fun splitCoversTheWholeLongTranscriptIncludingTail() {
        val text="原稿".repeat(20000)+"唯一的尾部事实"
        val pieces=splitSource(SourceText(8,text,0))
        assertEquals(text,pieces.joinToString("") {it.first.text})
        assertEquals(text.length,pieces.last().second+pieces.last().first.text.length)
        val last=pieces.last()
        val refs=AiClient.readQuotes(JSONArray("""[{"chunk_id":8,"quote":"唯一的尾部事实"},{"chunk_id":999,"quote":"原稿"}]"""),listOf(last))
        assertEquals(1,refs.size);assertTrue(refs.single().isValidFor(text))
    }
    @Test fun retrievalWindowsKeepOriginalOffsets() {
        val text="前".repeat(500)+"可核对的原句"+"后".repeat(500)
        val window=SourceText(9,text.substring(400,800),0,startOffset=400)
        val refs=AiClient.readQuotes(JSONArray("""[{"chunk_id":9,"quote":"可核对的原句"},{"chunk_id":9,"quote":"编造的引用"}]"""),listOf(window to window.startOffset))
        assertEquals(500,refs.single().start);assertTrue(refs.single().isValidFor(text))
    }
    @Test fun finalSseOverridesDeltasAndEmptyDoneMeansSilence() {
        assertEquals("最终文字",AiClient.parseSse("data: {\"type\":\"transcript.text.delta\",\"delta\":\"最\"}\n\ndata: {\"type\":\"transcript.text.done\",\"text\":\"最终文字\"}"))
        assertEquals("",AiClient.parseSse("data: {\"type\":\"transcript.text.done\",\"text\":\"\"}"))
    }
    @Test fun malformedOrTruncatedSseDoesNotBecomeSilence() {
        listOf("{\"text\":\"x\"}","data: {\"type\":\"transcript.text.delta\",\"delta\":\"半句话\"}","data: {\"type\":\"error\"}").forEach { body ->
            assertThrows(AiException::class.java) {AiClient.parseSse(body)}
        }
    }
}
