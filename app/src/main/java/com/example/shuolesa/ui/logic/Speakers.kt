package com.example.shuolesa.ui.logic

import org.json.JSONObject

/**
 * v4.2: user-given speaker names for one recording, stored in recording_sessions.speakerNamesJson as
 * `{"<key>":"名字"}`. Names stay on the device; they are display labels, not voiceprints.
 * Key = the speaker number when it holds across the recording, otherwise "<chunkId>/<number>" because
 * per-片段 numbers can belong to different people in different 片段.
 */
object SpeakerNames {
    fun key(chunkId: Long, sentence: TimedSentence): String? = sentence.speaker?.let { if (sentence.sessionScoped) it else "$chunkId/$it" }

    fun parse(json: String?): Map<String, String> = runCatching {
        val o = JSONObject(json ?: return emptyMap())
        o.keys().asSequence().mapNotNull { k -> o.optString(k).trim().takeIf { it.isNotEmpty() }?.let { k to it } }.toMap()
    }.getOrDefault(emptyMap())

    /** Returns the new JSON with [key] renamed to [name]; a blank name restores the default label. Null when empty. */
    fun set(json: String?, key: String, name: String): String? {
        val names = parse(json).toMutableMap()
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty()) names.remove(key) else names[key] = clean
        return if (names.isEmpty()) null else JSONObject(names.toMap()).toString()
    }

    fun label(names: Map<String, String>, key: String?, speaker: String?): String? =
        speaker?.let { names[key] ?: "说话人 $it" }

    const val MAX_NAME = 20
}

/** One 片段 for export. [marks] = the user's capture marks in this 片段 (ms from its start). */
data class ExportSegment(val chunkId: Long, val header: String, val view: SegmentView, val marks: List<Long> = emptyList())

/** v4.2: plain-text export of the 原话 with times and speaker names (导出原话). Pure, unit tested. */
object TranscriptExport {
    const val NOTE = "说话人和时间由转写服务自动生成，可能有误，请以原音为准。"

    fun text(title: String, meta: String, segments: List<ExportSegment>, names: Map<String, String>): String = buildString {
        append(title).append('\n').append(meta).append('\n')
        if (segments.any { s -> s.view.sentences.any { it.speaker != null } }) append(NOTE).append('\n')
        segments.forEach { seg ->
            append('\n').append(seg.header).append('\n')
            if (seg.marks.isNotEmpty()) append("标记：").append(seg.marks.sorted().joinToString("、") { SegmentPlayback.timeLabel(it) }).append('\n')
            val timed = seg.view.mode == SegmentMode.A_TIMESTAMPS
            var previous: String? = null
            seg.view.sentences.forEach { s ->
                val label = SpeakerNames.label(names, SpeakerNames.key(seg.chunkId, s), s.speaker)
                if (timed) append('[').append(SegmentPlayback.timeLabel(s.startMs)).append("] ")
                if (label != null && label != previous) append(label).append("：")
                append(s.text).append('\n')
                previous = label
            }
        }
    }
}
