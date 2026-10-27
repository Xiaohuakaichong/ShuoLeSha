package com.example.shuolesa.ui.logic

import org.json.JSONArray

/*
 * v4.1-b ⑥-A / ⑥-B: chosen PER SEGMENT (片段).
 *  - segmentsJson has usable sentence timestamps -> A: tap a sentence to seek, current sentence follows playback.
 *  - segmentsJson null / empty / unparseable -> B: sentences not tappable, "从片段开头回听".
 * v4.2: written by services that return sentence timing (阿里云百炼 qwen-audio-*-asr-flash, see network/DashScopeAsr);
 * other services leave it null, so their 片段 stay B. Built only by data/model/AsrSegments.toJson.
 */

/**
 * One sentence. [startMs]/[endMs] are relative to the segment start; null = missing ("—", tap falls back to segment start).
 * v4.2: [speaker] is the service's 1-based speaker number (null = no diarization); [sessionScoped] = the number holds across
 * the whole recording, otherwise only inside this 片段 (see AsrSegments.SCOPE_*).
 */
data class TimedSentence(val text: String, val startMs: Long?, val endMs: Long? = null,
                         val speaker: String? = null, val sessionScoped: Boolean = false)

fun interface TimestampParser { fun parse(json: String?): List<TimedSentence>? }

/**
 * Storage format (v4.1): JSON array `[{"text":"…","start":5150,"end":9800}, …]`, times in ms relative to the
 * segment. Also accepts seconds as fractional numbers in `start_s`/`end_s` (common ASR SSE shape). Returns null when
 * the field is absent or unusable, which means B.
 * v4.2 adds optional `"speaker":"2"` and `"scope":"session"|"segment"` per sentence.
 */
object DefaultTimestampParser : TimestampParser {
    override fun parse(json: String?): List<TimedSentence>? {
        if (json.isNullOrBlank()) return null
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return null
        val out = ArrayList<TimedSentence>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val text = o.optString("text").trim()
            if (text.isEmpty()) continue
            fun ms(key: String, secKey: String): Long? = when {
                o.has(key) && !o.isNull(key) -> o.optLong(key, -1).takeIf { it >= 0 }
                o.has(secKey) && !o.isNull(secKey) -> (o.optDouble(secKey, -1.0) * 1000).toLong().takeIf { it >= 0 }
                else -> null
            }
            val speaker = if (o.has("speaker") && !o.isNull("speaker")) o.optString("speaker").trim().takeIf { it.isNotEmpty() } else null
            out += TimedSentence(text, ms("start", "start_s"), ms("end", "end_s"), speaker, o.optString("scope") == "session")
        }
        return out.takeIf { list -> list.isNotEmpty() && list.any { it.startMs != null } }
    }
}

enum class SegmentMode { A_TIMESTAMPS, B_SEGMENT_START }

data class SegmentView(val mode: SegmentMode, val sentences: List<TimedSentence>)

object SegmentPlayback {
    /** Split plain 原话 by sentence end / newline (design ⑥: 按句号/换行分段，不再按 1600 字硬切). */
    fun splitSentences(text: String): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder()
        for (ch in text) {
            if (ch == '\n') { sb.toString().trim().takeIf { it.isNotEmpty() }?.let(out::add); sb.clear(); continue }
            sb.append(ch)
            if (ch in "。！？!?；;…") { sb.toString().trim().takeIf { it.isNotEmpty() }?.let(out::add); sb.clear() }
        }
        sb.toString().trim().takeIf { it.isNotEmpty() }?.let(out::add)
        return out
    }

    fun view(text: String, segmentsJson: String?, parser: TimestampParser = DefaultTimestampParser): SegmentView {
        val timed = parser.parse(segmentsJson)
        return if (timed != null) SegmentView(SegmentMode.A_TIMESTAMPS, timed)
        else SegmentView(SegmentMode.B_SEGMENT_START, splitSentences(text).map { TimedSentence(it, null) })
    }

    /**
     * Where to seek (absolute ms in the playable file). [base] = segment start inside the file (imports) or 0.
     * A + sentence with time -> its start (minus [leadMs] when arriving from a quote: "从该句播放前 1 秒开始").
     * B or sentence without time -> segment start.
     */
    fun seekFor(view: SegmentView, sentenceIndex: Int?, base: Long, leadMs: Long = 0): Long {
        if (view.mode == SegmentMode.B_SEGMENT_START || sentenceIndex == null) return base
        val start = view.sentences.getOrNull(sentenceIndex)?.startMs ?: return base
        return base + (start - leadMs).coerceAtLeast(0)
    }

    /** Index of the sentence playing at [positionMs] (relative to segment), A only; null in B. */
    fun current(view: SegmentView, positionMs: Long): Int? {
        if (view.mode != SegmentMode.A_TIMESTAMPS) return null
        var best: Int? = null
        view.sentences.forEachIndexed { i, s -> if (s.startMs != null && s.startMs <= positionMs) best = i }
        return best
    }

    /** Sentence containing a quoted character range of the 原话 text (for "核对原话" entry). */
    fun sentenceForQuote(view: SegmentView, fullText: String, quoteStart: Int): Int? {
        var cursor = 0
        view.sentences.forEachIndexed { i, s ->
            val at = fullText.indexOf(s.text, cursor)
            if (at >= 0) { if (quoteStart in at until at + s.text.length) return i; cursor = at + s.text.length }
        }
        return null
    }

    fun timeLabel(ms: Long?): String = ms?.let { "%02d:%02d".format(it / 60000, it / 1000 % 60) } ?: "—"
}

/** User scrolled manually: pause auto-follow for 5 s (design ⑥-A). */
class FollowPolicy(private val pauseMs: Long = 5000) {
    private var pausedUntil = 0L
    fun onUserScroll(now: Long) { pausedUntil = now + pauseMs }
    fun shouldFollow(now: Long, enabled: Boolean) = enabled && now >= pausedUntil
}
