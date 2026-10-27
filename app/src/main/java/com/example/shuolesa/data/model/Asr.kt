package com.example.shuolesa.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * v4.2: one recognised sentence. [startMs]/[endMs] are relative to the audio that was sent to the service
 * (a 片段, or the merged meeting before it is split back into 片段). [speaker] is a 1-based label from the
 * service's diarization ("1", "2", …); null when the service did not separate speakers.
 */
data class AsrSegment(val text: String, val startMs: Long?, val endMs: Long?, val speaker: String? = null)

/** What a transcription returns. [text] becomes the immutable 原话; [segments] are optional timing/speaker data. */
data class AsrResult(val text: String, val segments: List<AsrSegment> = emptyList(), val speakerScope: String? = null)

object AsrSegments {
    /** Speaker numbers only hold inside one 5-minute 片段 (each 片段 was diarized on its own). */
    const val SCOPE_SEGMENT = "segment"
    /** Speaker numbers hold across the whole recording (the meeting was diarized in one pass). */
    const val SCOPE_SESSION = "session"

    private fun clean(segments: List<AsrSegment>) = segments.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }

    /**
     * The 原话 text for a list of sentences: one sentence per line. The detail page finds sentences inside the
     * text by exact match, and evidence quotes are ranges of this text, so it must be built only here.
     */
    fun text(segments: List<AsrSegment>): String = clean(segments).joinToString("\n") { it.text }

    /**
     * transcripts.segmentsJson (format documented in ui/logic/SegmentPlayback): `[{"text","start","end","speaker","scope"}]`.
     * Null when nothing has a usable start time, which keeps the 片段 in "从片段开头回听" mode.
     */
    fun toJson(segments: List<AsrSegment>, scope: String?): String? {
        val usable = clean(segments)
        if (usable.none { it.startMs != null }) return null
        return JSONArray().apply {
            usable.forEach { s -> put(JSONObject().apply {
                put("text", s.text)
                s.startMs?.let { put("start", it) }
                s.endMs?.let { put("end", it) }
                s.speaker?.let { put("speaker", it) }
                if (s.speaker != null && scope != null) put("scope", scope)
            }) }
        }.toString()
    }

    /**
     * Splits sentences timed against a merged recording back into its 片段.
     * [offsets] = (chunkId, start of that 片段 in the merged audio, ms), in playback order. A sentence belongs to
     * the 片段 its start falls into (a sentence that runs over a boundary stays whole in the first one); its
     * times become relative to that 片段. Sentences without a start time go to the first 片段.
     */
    fun splitByChunk(segments: List<AsrSegment>, offsets: List<Pair<Long, Long>>): Map<Long, List<AsrSegment>> {
        if (offsets.isEmpty()) return emptyMap()
        val out = LinkedHashMap<Long, MutableList<AsrSegment>>()
        offsets.forEach { out[it.first] = mutableListOf() }
        for (s in segments) {
            val start = s.startMs
            val owner = if (start == null) offsets.first() else offsets.lastOrNull { it.second <= start } ?: offsets.first()
            val base = owner.second
            out.getValue(owner.first) += s.copy(startMs = start?.let { (it - base).coerceAtLeast(0) },
                endMs = s.endMs?.let { (it - base).coerceAtLeast(0) })
        }
        return out
    }
}
