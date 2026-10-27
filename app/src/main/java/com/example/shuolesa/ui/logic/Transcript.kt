package com.example.shuolesa.ui.logic

import com.example.shuolesa.data.model.EvidenceRef

/*
 * v4.2 continuous 原话 view (design: 记录详情 · 原话). Pure logic, unit tested in TranscriptLayoutTest.
 * All 片段 of a recording read as one transcript: a divider per 片段, consecutive sentences of one speaker grouped
 * into a turn (飞书妙记 / Otter style), optional filter by speaker, and the user's capture marks placed by time.
 */

/** One 片段 as the detail page sees it. [base] = where it starts in the playable file (imports), else 0. */
data class TranscriptSegment(val chunkId: Long, val index: Int, val header: String, val text: String, val view: SegmentView, val base: Long = 0)

sealed interface TranscriptRow {
    val key: String
    val chunkId: Long
    /** 片段 boundary. [timed] = sentences carry times (tap to play); otherwise "从片段开头回听". */
    data class Divider(override val chunkId: Long, val index: Int, val header: String, val timed: Boolean) : TranscriptRow {
        override val key: String get() = "d:$chunkId"
    }
    /**
     * Consecutive sentences of one speaker inside one 片段. [first] = index of the first sentence in the 片段's view,
     * so sentence k of this turn is view.sentences[first + k].
     */
    data class Turn(override val chunkId: Long, val speakerKey: String?, val speaker: String?, val startMs: Long?,
                    val first: Int, val sentences: List<TimedSentence>, val timed: Boolean) : TranscriptRow {
        override val key: String get() = "t:$chunkId:$first"
        fun contains(sentenceIndex: Int) = sentenceIndex >= first && sentenceIndex < first + sentences.size
    }
    /** A moment the user marked while recording; [atMs] relative to the 片段. */
    data class Mark(override val chunkId: Long, val atMs: Long) : TranscriptRow {
        override val key: String get() = "m:$chunkId:$atMs"
    }
    /** 片段 without 原话 yet. */
    data class Pending(override val chunkId: Long) : TranscriptRow {
        override val key: String get() = "p:$chunkId"
    }
}

data class SpeakerOption(val key: String, val speaker: String, val slot: Int)

object TranscriptLayout {
    /** Builds the rows. [marks] = chunkId -> mark times relative to that 片段. [speakerKey] = show only this speaker. */
    fun rows(segments: List<TranscriptSegment>, marks: Map<Long, List<Long>> = emptyMap(), speakerKey: String? = null): List<TranscriptRow> {
        val out = ArrayList<TranscriptRow>()
        for (seg in segments) {
            val timed = seg.view.mode == SegmentMode.A_TIMESTAMPS
            if (seg.text.isBlank() || seg.view.sentences.isEmpty()) {
                if (speakerKey == null) { out += TranscriptRow.Divider(seg.chunkId, seg.index, seg.header, timed); out += TranscriptRow.Pending(seg.chunkId) }
                continue
            }
            val turns = turns(seg, timed).filter { speakerKey == null || it.speakerKey == speakerKey }
            if (turns.isEmpty()) continue
            out += TranscriptRow.Divider(seg.chunkId, seg.index, seg.header, timed)
            val pendingMarks = if (speakerKey == null) marks[seg.chunkId].orEmpty().sorted().toMutableList() else mutableListOf()
            // An untimed 片段 lists its marks under the divider; otherwise a mark sits before the first turn that
            // starts after it, i.e. right after what was being said when the user pressed the button.
            if (!timed) { pendingMarks.forEach { out += TranscriptRow.Mark(seg.chunkId, it) }; pendingMarks.clear() }
            for (turn in turns) {
                val start = turn.startMs
                if (start != null) while (pendingMarks.isNotEmpty() && pendingMarks.first() < start) out += TranscriptRow.Mark(seg.chunkId, pendingMarks.removeAt(0))
                out += turn
            }
            pendingMarks.forEach { out += TranscriptRow.Mark(seg.chunkId, it) }
        }
        return out
    }

    /**
     * Turns of one 片段: consecutive sentences with the same speaker key. Timed sentences without a speaker stay one
     * per turn (each shows its own time); an untimed 片段 without speakers is one turn.
     */
    private fun turns(seg: TranscriptSegment, timed: Boolean): List<TranscriptRow.Turn> {
        val sentences = seg.view.sentences
        val out = ArrayList<TranscriptRow.Turn>()
        var i = 0
        while (i < sentences.size) {
            val key = SpeakerNames.key(seg.chunkId, sentences[i])
            var j = i + 1
            if (key != null || !timed) while (j < sentences.size && SpeakerNames.key(seg.chunkId, sentences[j]) == key) j++
            out += TranscriptRow.Turn(seg.chunkId, key, sentences[i].speaker, sentences[i].startMs, i, sentences.subList(i, j).toList(), timed)
            i = j
        }
        return out
    }

    /**
     * Speakers in order of first appearance, with a stable colour slot. Empty when filtering would mislead: per-片段
     * numbers (each 片段 diarized on its own) in more than one 片段 do not name the same person across the recording.
     */
    fun speakers(segments: List<TranscriptSegment>): List<SpeakerOption> {
        val seen = LinkedHashMap<String, String>()
        val withSpeakers = HashSet<Long>()
        var allSession = true
        for (seg in segments) for (s in seg.view.sentences) {
            val key = SpeakerNames.key(seg.chunkId, s) ?: continue
            withSpeakers += seg.chunkId
            if (!s.sessionScoped) allSession = false
            if (key !in seen) seen[key] = s.speaker!!
        }
        if (seen.isEmpty() || (!allSession && withSpeakers.size > 1)) return emptyList()
        return seen.entries.mapIndexed { i, (key, speaker) -> SpeakerOption(key, speaker, i) }
    }

    /** Colour slot for every speaker key of the recording (first appearance order), also when filtering is off. */
    fun slots(segments: List<TranscriptSegment>): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        for (seg in segments) for (s in seg.view.sentences) SpeakerNames.key(seg.chunkId, s)?.let { if (it !in out) out[it] = out.size }
        return out
    }

    /**
     * Share of speaking time per speaker (发言占比), from sentence start/end. Only for speakers that [speakers] lists;
     * a sentence without an end time counts until the next sentence starts (at most 10 s).
     */
    fun share(segments: List<TranscriptSegment>): List<Pair<SpeakerOption, Float>> {
        val options = speakers(segments)
        if (options.isEmpty()) return emptyList()
        val totals = HashMap<String, Long>()
        for (seg in segments) {
            val sentences = seg.view.sentences
            sentences.forEachIndexed { i, s ->
                val key = SpeakerNames.key(seg.chunkId, s) ?: return@forEachIndexed
                val start = s.startMs ?: return@forEachIndexed
                val end = s.endMs ?: sentences.getOrNull(i + 1)?.startMs?.coerceAtMost(start + 10_000) ?: (start + 2_000)
                totals[key] = (totals[key] ?: 0L) + (end - start).coerceAtLeast(0)
            }
        }
        val sum = totals.values.sum()
        if (sum <= 0) return emptyList()
        return options.map { it to (totals[it.key] ?: 0L).toFloat() / sum }
    }

    /** Index of the row holding sentence [sentenceIndex] of 片段 [chunkId], or of its divider; -1 when absent. */
    fun rowOf(rows: List<TranscriptRow>, chunkId: Long, sentenceIndex: Int?): Int {
        if (sentenceIndex != null) {
            val at = rows.indexOfFirst { it is TranscriptRow.Turn && it.chunkId == chunkId && it.contains(sentenceIndex) }
            if (at >= 0) return at
        }
        return rows.indexOfFirst { it is TranscriptRow.Divider && it.chunkId == chunkId }
    }

    /** "张三 · 片段 1 · 00:40" for an evidence quote; falls back to "片段 N" or "原话来源". */
    fun refLabel(segments: List<TranscriptSegment>, names: Map<String, String>, ref: EvidenceRef): String {
        val seg = segments.find { it.chunkId == ref.chunkId } ?: return "原话来源"
        val index = SegmentPlayback.sentenceForQuote(seg.view, seg.text, ref.start)
        val sentence = index?.let { seg.view.sentences.getOrNull(it) }
        val parts = ArrayList<String>()
        sentence?.let { s -> SpeakerNames.label(names, SpeakerNames.key(seg.chunkId, s), s.speaker)?.let(parts::add) }
        parts += "片段 ${seg.index + 1}"
        sentence?.startMs?.let { parts += SegmentPlayback.timeLabel(it) }
        return parts.joinToString(" · ")
    }
}

/** v4.2 capture marks: stored as offsets in the recording; shown per 片段. */
object MarkLayout {
    /** [marks] = offsets from the start of the recording; [chunks] = (chunkId, duration) in playback order. */
    fun toChunks(marks: List<Long>, chunks: List<Pair<Long, Long>>): Map<Long, List<Long>> {
        if (marks.isEmpty() || chunks.isEmpty()) return emptyMap()
        val out = LinkedHashMap<Long, MutableList<Long>>()
        for (mark in marks.sorted().distinct()) {
            var start = 0L
            var placed = false
            for ((id, duration) in chunks) {
                if (mark < start + duration) { out.getOrPut(id) { mutableListOf() } += mark - start; placed = true; break }
                start += duration
            }
            // A mark after the last sealed audio (e.g. pressed while stopping) belongs to the end of the last 片段.
            if (!placed) { val (id, duration) = chunks.last(); out.getOrPut(id) { mutableListOf() } += duration }
        }
        // Distinct per 片段: rows are keyed by time, and several late marks collapse onto the last 片段's end.
        return out.mapValues { (_, list) -> list.distinct() }
    }
    /** Playback starts a little before the mark, because people mark after hearing the moment. */
    fun seekFor(atMs: Long, leadMs: Long = 5_000) = (atMs - leadMs).coerceAtLeast(0)
}
