package com.example.shuolesa.ui.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/*
 * v4.1-b pure UI logic (no Android). Unit tested in V41LogicTest / SegmentPlaybackTest.
 */

// ---------- ⑧ 四种处理状态 ----------
enum class UiStatus(val label: String) {
    PROCESSING("处理中"), DONE("已整理"), WAITING_NETWORK("等网络"), WAITING_CONFIG("等配置"), FAILED("失败"),
    RECORDING("录音中"), EMPTY("未录到音频");

    companion object {
        /** Maps session capture/processing state to the ⑧ vocabulary. Never shows raw codes (FAILED etc.). */
        fun of(captureState: String?, processingState: String): UiStatus = when {
            captureState in listOf("STARTING", "RECORDING", "PAUSED", "STOPPING") -> RECORDING
            processingState == "READY" -> DONE
            processingState == "FAILED" -> FAILED
            processingState == "WAITING_CONFIG" -> WAITING_CONFIG
            processingState == "WAITING_NETWORK" -> WAITING_NETWORK
            processingState == "EMPTY" -> EMPTY
            else -> PROCESSING
        }
        /** Processing-queue job states (settings › data) in Chinese. */
        fun jobLabel(state: String): String = when (state) {
            "PENDING", "RUNNING", "WAITING_DEPENDENCY" -> PROCESSING.label
            "COMPLETE" -> "已完成"
            "FAILED" -> FAILED.label
            "WAITING_CONFIG" -> WAITING_CONFIG.label
            "WAITING_NETWORK" -> WAITING_NETWORK.label
            else -> PROCESSING.label
        }
    }
}

/** ② / ⑧ home banner. At most one; priority 等确认 > 失败 > 等配置 > 等网络. 处理中/已完成 never show a banner. */
enum class BannerKind { CONSENT, FAILED, CONFIG, CLEARTEXT, NETWORK }
enum class BannerAction { CONFIRM, RETRY, FIX, FIX_ADDRESS, VIEW }
data class BannerInput(val sessionId: String, val processingState: String, val reasonCode: String?, val message: String?)
data class Banner(val kind: BannerKind, val tag: String, val title: String, val body: String, val action: BannerAction,
                  val actionLabel: String, val count: Int, val sessionIds: List<String>)

object BannerLogic {
    fun pick(items: List<BannerInput>, consentPending: Boolean): Banner? {
        if (consentPending) return Banner(BannerKind.CONSENT, "等确认", "转写还没开启",
            "原音保存在本机。确认数据外发后会自动开始转写。", BannerAction.CONFIRM, "去确认", 0, emptyList())
        val failed = items.filter { it.processingState == "FAILED" }
        val config = items.filter { it.processingState == "WAITING_CONFIG" }
        val network = items.filter { it.processingState == "WAITING_NETWORK" }
        return when {
            failed.isNotEmpty() -> Banner(BannerKind.FAILED, "失败", "${failed.size} 条记录处理失败",
                (failed.first().message ?: "处理失败") + "。原音保留。", BannerAction.RETRY, "重试", failed.size, failed.map { it.sessionId })
            config.isNotEmpty() -> {
                val first = config.first()
                if (first.reasonCode == "CLEARTEXT_BLOCKED") Banner(BannerKind.CLEARTEXT, "等配置", "服务地址被拦截",
                    "http 地址不安全，请改用 https 或 localhost。原音都在手机里，改好后会自动继续。", BannerAction.FIX_ADDRESS, "去修复", config.size, config.map { it.sessionId })
                else Banner(BannerKind.CONFIG, "等配置", "${config.size} 条记录在等配置",
                    (first.message ?: when (first.reasonCode) { "KEY_INVALID" -> "服务密钥已失效"; "ADDRESS_INVALID" -> "服务地址无法访问"; else -> "还没配置转写服务" }) +
                        "。原音都在手机里，修好后自动继续。", BannerAction.FIX, "去修复", config.size, config.map { it.sessionId })
            }
            network.isNotEmpty() -> Banner(BannerKind.NETWORK, "等网络", "${network.size} 条记录等网络",
                "原音等网络恢复后自动转写。", BannerAction.VIEW, "查看", network.size, network.map { it.sessionId })
            else -> null
        }
    }
}

// ---------- ③ 筛选：全部 / 随身 / 会议 + 日期 ----------
data class DateRange(val start: Long, val endExclusive: Long)
enum class DatePreset(val label: String) { TODAY("今天"), YESTERDAY("昨天"), LAST_7("近 7 天"), THIS_MONTH("本月") }

object FilterLogic {
    val MODES = listOf("all" to "全部", "lifelog" to "随身", "meeting" to "会议")
    fun range(from: LocalDate, toInclusive: LocalDate, zone: ZoneId): DateRange {
        val (a, b) = if (from <= toInclusive) from to toInclusive else toInclusive to from
        return DateRange(a.atStartOfDay(zone).toInstant().toEpochMilli(), b.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
    }
    fun preset(p: DatePreset, today: LocalDate, zone: ZoneId): DateRange = when (p) {
        DatePreset.TODAY -> range(today, today, zone)
        DatePreset.YESTERDAY -> today.minusDays(1).let { range(it, it, zone) }
        DatePreset.LAST_7 -> range(today.minusDays(6), today, zone)
        DatePreset.THIS_MONTH -> range(today.with(TemporalAdjusters.firstDayOfMonth()), today, zone)
    }
    fun matches(mode: String, time: Long, selectedMode: String, range: DateRange?): Boolean =
        (selectedMode == "all" || mode == selectedMode) && (range == null || (time >= range.start && time < range.endExclusive))
    /** Chip / button label, e.g. "10/12–10/14", or "10/14" for one day. */
    fun label(range: DateRange, zone: ZoneId): String {
        val a = java.time.Instant.ofEpochMilli(range.start).atZone(zone).toLocalDate()
        val b = java.time.Instant.ofEpochMilli(range.endExclusive - 1).atZone(zone).toLocalDate()
        fun f(d: LocalDate) = "${d.monthValue}/${d.dayOfMonth}"
        return if (a == b) f(a) else "${f(a)}–${f(b)}"
    }
}

// ---------- 搜索合一：排序 ----------
data class SearchHit(val chunkId: Long, val text: String, val createdAt: Long)
object SearchRanking {
    /** Exact phrase hits first, then by number of occurrences of the query terms, then newest first. */
    fun rank(query: String, hits: List<SearchHit>): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return hits
        val terms = q.split(Regex("\\s+")).filter { it.isNotBlank() }
        fun count(text: String, term: String): Int { var n = 0; var i = text.indexOf(term, ignoreCase = true)
            while (i >= 0) { n++; i = text.indexOf(term, i + term.length, ignoreCase = true) }; return n }
        return hits.distinctBy { it.chunkId }.sortedWith(
            compareByDescending<SearchHit> { it.text.contains(q, ignoreCase = true) }
                .thenByDescending { h -> terms.count { h.text.contains(it, true) } }
                .thenByDescending { h -> terms.sumOf { count(h.text, it) } }
                .thenByDescending { it.createdAt })
    }
    /** Snippet window around the first hit. */
    fun snippet(text: String, query: String, before: Int = 60, length: Int = 260): IntRange {
        val at = text.indexOf(query.trim(), ignoreCase = true).coerceAtLeast(0)
        val start = (at - before).coerceAtLeast(0)
        return start until minOf(text.length, start + length)
    }
}

// ---------- 磁贴防连点 ----------
class TapDebouncer(private val windowMs: Long = 1000, private val now: () -> Long = System::currentTimeMillis) {
    private var last = Long.MIN_VALUE / 2
    @Synchronized fun accept(): Boolean { val t = now(); if (t - last < windowMs) return false; last = t; return true }
}

// ---------- 数据页 ----------
object StorageFormat {
    fun bytes(n: Long): String = when {
        n < 1024 -> "$n B"
        n < 1024L * 1024 -> "%.0f KB".format(n / 1024.0)
        n < 1024L * 1024 * 1024 -> "%.1f MB".format(n / 1024.0 / 1024)
        else -> "%.2f GB".format(n / 1024.0 / 1024 / 1024)
    }
}

// ---------- ④ 今日 ----------
object TodayLogic {
    private val WEEK = mapOf(DayOfWeek.MONDAY to "周一", DayOfWeek.TUESDAY to "周二", DayOfWeek.WEDNESDAY to "周三",
        DayOfWeek.THURSDAY to "周四", DayOfWeek.FRIDAY to "周五", DayOfWeek.SATURDAY to "周六", DayOfWeek.SUNDAY to "周日")
    fun header(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日 ${WEEK.getValue(date.dayOfWeek)}"
    /** Number of transcribed chunks of the day that the digest does not cite yet ("有 N 段新原话还没算进来"). */
    fun newSinceDigest(transcribedChunkIds: Collection<Long>, citedChunkIds: Collection<Long>, digestCreatedAt: Long?,
                       transcribedAt: Map<Long, Long>): Int {
        if (digestCreatedAt == null) return 0
        return transcribedChunkIds.count { id -> id !in citedChunkIds && (transcribedAt[id] ?: 0) > digestCreatedAt }
    }
}

/** 待办候选角标：0 不显示，>99 显示 "99+". */
object BadgeLogic { fun text(count: Int): String? = when { count <= 0 -> null; count > 99 -> "99+"; else -> count.toString() } }

/**
 * v4.1 product decision: only informational cards may be dismissed for good ("以后不提示", persisted).
 * Status banners (失败 / 等配置 / 等确认 / 等网络 / 地址不安全) can never be dismissed; they disappear
 * by themselves once the underlying problem is resolved.
 */
enum class NoticeKind(val dismissible: Boolean, /** DataStore boolean key of its persisted「以后不提示」. */ val prefKey: String? = null) {
    BLIND_SYSTEM_LIMIT(true, "blind_fallback_hint_off"),   // ⑦ notification「录音没能在后台开始」
    HOME_BLIND_FALLBACK(true, "home_blind_card_off"),      // ⑦ home card「刚才的快捷录音没有开始」
    BANNER_CONSENT(false), BANNER_FAILED(false), BANNER_CONFIG(false), BANNER_CLEARTEXT(false), BANNER_NETWORK(false);
    companion object {
        fun of(kind: BannerKind): NoticeKind = when (kind) {
            BannerKind.CONSENT -> BANNER_CONSENT; BannerKind.FAILED -> BANNER_FAILED; BannerKind.CONFIG -> BANNER_CONFIG
            BannerKind.CLEARTEXT -> BANNER_CLEARTEXT; BannerKind.NETWORK -> BANNER_NETWORK
        }
    }
}
object NoticePolicy {
    /** [active]: the condition behind the notice still holds. A "dismissed" flag is ignored for status banners. */
    fun visible(kind: NoticeKind, active: Boolean, dismissedForever: Boolean): Boolean =
        active && !(kind.dismissible && dismissedForever)
    fun canDismissForever(kind: NoticeKind): Boolean = kind.dismissible
    /** 设置 › 外观「恢复所有提示」clears exactly these keys. */
    val dismissalKeys: List<String> get() = NoticeKind.entries.mapNotNull { it.prefKey }
    /** Pure form of the clear, for tests: every dismissal flag removed, nothing else touched. */
    fun cleared(prefs: Map<String, Any?>): Map<String, Any?> = prefs - dismissalKeys.toSet()
}

/** v4.1 ① 测试连接. A failure never blocks onboarding: the user can always continue (「稍后再说」/「下一步」). */
enum class ConnTestPhase { IDLE, TESTING, OK, FAILED }
data class ConnTestUi(val phase: ConnTestPhase = ConnTestPhase.IDLE, val error: String? = null,
                     /** Test refused locally: draft address ≠ consented (shown as the yellow banner, not an error). */ val draftChanged: Boolean = false) {
    val canTest: Boolean get() = phase != ConnTestPhase.TESTING
}
object ConnectionTestLogic {
    const val OK_TEXT = "连接成功，转写和整理服务都能用"
    const val FALLBACK_ERROR = "连接失败，请检查密钥或网络"
    fun testing() = ConnTestUi(ConnTestPhase.TESTING)
    fun ok() = ConnTestUi(ConnTestPhase.OK)
    /** Strips a leading "[CODE] " (ProcessingReason format) so users never see raw codes. */
    fun failed(message: String?): ConnTestUi {
        val text = message.orEmpty().replace(Regex("^\\s*\\[[A-Z0-9_]+]\\s*"), "").trim()
        return ConnTestUi(ConnTestPhase.FAILED, text.ifEmpty { FALLBACK_ERROR })
    }
    /** Key must be non-blank to test; a running test can't be restarted. */
    fun canStart(key: String, state: ConnTestUi): Boolean = key.isNotBlank() && state.canTest
}
