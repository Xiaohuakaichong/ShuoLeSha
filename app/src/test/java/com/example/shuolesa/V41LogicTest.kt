package com.example.shuolesa

import com.example.shuolesa.ui.logic.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class V41LogicTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun b(id: String, state: String, code: String? = null, msg: String? = null) = BannerInput(id, state, code, msg)

    @Test fun bannerPriorityFailedThenConfigThenNetwork() {
        val all = listOf(b("n", "WAITING_NETWORK"), b("c", "WAITING_CONFIG", "KEY_INVALID", "服务密钥已失效（401）"), b("f", "FAILED", null, "音频格式不支持"))
        assertEquals(BannerKind.FAILED, BannerLogic.pick(all, false)!!.kind)
        assertEquals(BannerAction.RETRY, BannerLogic.pick(all, false)!!.action)
        val noFail = all.filter { it.processingState != "FAILED" }
        BannerLogic.pick(noFail, false)!!.let { assertEquals(BannerKind.CONFIG, it.kind); assertEquals("去修复", it.actionLabel); assertTrue(it.body.startsWith("服务密钥已失效（401）")) }
        BannerLogic.pick(listOf(b("n", "WAITING_NETWORK"), b("m", "WAITING_NETWORK")), false)!!.let {
            assertEquals(BannerKind.NETWORK, it.kind); assertEquals(2, it.count); assertEquals("查看", it.actionLabel) }
    }
    @Test fun consentBeatsEverythingAndQuietStatesHaveNoBanner() {
        assertEquals(BannerKind.CONSENT, BannerLogic.pick(listOf(b("f", "FAILED")), true)!!.kind)
        assertNull(BannerLogic.pick(listOf(b("a", "READY"), b("b", "TRANSCRIBING"), b("c", "DERIVING")), false))
        assertNull(BannerLogic.pick(emptyList(), false))
    }
    @Test fun cleartextIsItsOwnConfigBanner() {
        val banner = BannerLogic.pick(listOf(b("c", "WAITING_CONFIG", "CLEARTEXT_BLOCKED")), false)!!
        assertEquals(BannerKind.CLEARTEXT, banner.kind); assertEquals("服务地址被拦截", banner.title); assertEquals(BannerAction.FIX_ADDRESS, banner.action)
    }
    @Test fun uiStatusNeverLeaksCodes() {
        assertEquals(UiStatus.DONE, UiStatus.of(null, "READY"))
        assertEquals(UiStatus.RECORDING, UiStatus.of("PAUSED", "READY"))
        assertEquals(UiStatus.PROCESSING, UiStatus.of("COMPLETE", "DERIVING"))
        assertEquals("失败", UiStatus.jobLabel("FAILED")); assertEquals("等配置", UiStatus.jobLabel("WAITING_CONFIG"))
        assertEquals("处理中", UiStatus.jobLabel("WAITING_DEPENDENCY"))
        for (s in listOf("PENDING", "RUNNING", "FAILED", "WAITING_CONFIG", "WAITING_NETWORK", "COMPLETE"))
            assertFalse(UiStatus.jobLabel(s).any { it in 'A'..'Z' })
    }
    @Test fun filters() {
        val today = LocalDate.of(2026, 10, 14)
        val r = FilterLogic.preset(DatePreset.LAST_7, today, zone)
        assertEquals("10/8–10/14", FilterLogic.label(r, zone))
        assertEquals("10/13", FilterLogic.label(FilterLogic.preset(DatePreset.YESTERDAY, today, zone), zone))
        assertEquals("10/1–10/14", FilterLogic.label(FilterLogic.preset(DatePreset.THIS_MONTH, today, zone), zone))
        val reversed = FilterLogic.range(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 12), zone)
        assertEquals("10/12–10/14", FilterLogic.label(reversed, zone))
        val inside = LocalDate.of(2026, 10, 14).atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
        val after = LocalDate.of(2026, 10, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        assertTrue(FilterLogic.matches("meeting", inside, "all", reversed))
        assertTrue(FilterLogic.matches("meeting", inside, "meeting", reversed))
        assertFalse(FilterLogic.matches("lifelog", inside, "meeting", null))
        assertFalse(FilterLogic.matches("meeting", after, "all", reversed))
        assertEquals(listOf("全部", "随身", "会议"), FilterLogic.MODES.map { it.second })
    }
    @Test fun searchRanking() {
        val hits = listOf(SearchHit(1, "周五 发布", 300), SearchHit(2, "周五的版本先定下来，周五发布", 100),
            SearchHit(3, "下周五再说 发布 发布", 200), SearchHit(2, "dup", 999), SearchHit(4, "无关", 999))
        val ranked = SearchRanking.rank("周五 发布", hits).map { it.chunkId }
        assertEquals(listOf(1L, 3L, 2L, 4L), ranked)
        assertEquals(listOf(2L, 1L, 3L), SearchRanking.rank("周五", hits.take(3)).map { it.chunkId }) // more occurrences, then newest
        val range = SearchRanking.snippet("a".repeat(100) + "目标" + "b".repeat(400), "目标")
        assertEquals(40, range.first); assertEquals(260, range.count())
    }
    @Test fun tileDebounce() {
        var t = 0L; val d = TapDebouncer(1000) { t }
        assertTrue(d.accept()); t = 500; assertFalse(d.accept()); t = 999; assertFalse(d.accept()); t = 1000; assertTrue(d.accept())
    }
    @Test fun misc() {
        assertEquals("1.5 MB", StorageFormat.bytes(1572864)); assertEquals("512 B", StorageFormat.bytes(512)); assertEquals("2.00 GB", StorageFormat.bytes(2L shl 30))
        assertEquals("10月14日 周三", TodayLogic.header(LocalDate.of(2026, 10, 14)))
        assertEquals(1, TodayLogic.newSinceDigest(listOf(1, 2, 3), listOf(1L), 100, mapOf(1L to 50L, 2L to 150L, 3L to 90L)))
        assertEquals(0, TodayLogic.newSinceDigest(listOf(1, 2), emptyList(), null, emptyMap()))
        assertNull(BadgeLogic.text(0)); assertEquals("3", BadgeLogic.text(3)); assertEquals("99+", BadgeLogic.text(120))
    }
}
