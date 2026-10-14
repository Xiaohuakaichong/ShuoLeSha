package com.example.shuolesa.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** A range in an immutable transcript. Audio times are present only when measured. */
data class EvidenceRef(
    val chunkId: Long,
    val start: Int,
    val end: Int,
    val quote: String,
    val audioStartMs: Long? = null,
    val audioEndMs: Long? = null,
) {
    fun isValidFor(text: String): Boolean = start >= 0 && end > start && end <= text.length &&
        quote.isNotBlank() && text.substring(start, end) == quote

    companion object {
        fun locate(chunkId: Long, text: String, quote: String): EvidenceRef? {
            if (quote.isBlank()) return null
            val start = text.indexOf(quote)
            return if (start < 0) null else EvidenceRef(chunkId, start, start + quote.length, quote)
        }

        fun fromJson(raw: String?): List<EvidenceRef> = runCatching {
            val array = JSONArray(raw ?: "[]")
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optLong("chunk_id")
                val start = obj.optInt("start", -1)
                val end = obj.optInt("end", -1)
                if (id <= 0 || start < 0 || end <= start) return@mapNotNull null
                EvidenceRef(id, start, end, obj.optString("quote"),
                    if (obj.has("audio_start_ms")) obj.optLong("audio_start_ms") else null,
                    if (obj.has("audio_end_ms")) obj.optLong("audio_end_ms") else null)
            }
        }.getOrDefault(emptyList())

        fun toJson(refs: List<EvidenceRef>): String = JSONArray().apply {
            refs.forEach { ref -> put(JSONObject().apply {
                put("chunk_id", ref.chunkId); put("start", ref.start); put("end", ref.end)
                put("quote", ref.quote)
                ref.audioStartMs?.let { put("audio_start_ms", it) }
                ref.audioEndMs?.let { put("audio_end_ms", it) }
            }) }
        }.toString()
    }
}

data class SourceText(val chunkId: Long, val text: String, val createdAt: Long, val provenance: String = "ASR", val startOffset: Int = 0)
data class RecallAnswer(val answer: String, val sources: List<EvidenceRef>)
data class TopicDraft(val title: String, val summary: String, val sources: List<EvidenceRef>,
    val actions: List<ActionDraft> = emptyList())
data class ActionDraft(val text: String, val sources: List<EvidenceRef>, val whenHint: String? = null)
data class SessionDraft(val title: String, val summary: String, val topics: List<TopicDraft>)

object ActionDraftCodec {
    fun encode(items: List<ActionDraft>) = JSONArray().apply { items.forEach { item ->
        put(JSONObject().apply { put("text", item.text); put("sources",JSONArray(EvidenceRef.toJson(item.sources)))
            put("when",item.whenHint ?: "") })
    } }.toString()
    fun decode(raw: String): List<ActionDraft> = runCatching {
        val items=JSONArray(raw)
        (0 until items.length()).map { i -> items.getJSONObject(i).let { item ->
            ActionDraft(item.getString("text"),EvidenceRef.fromJson(item.optJSONArray("sources")?.toString()),
                item.optString("when").ifBlank { null })
        } }
    }.getOrDefault(emptyList())
}

object TopicDraftCodec {
    fun encode(topics: List<TopicDraft>) = JSONArray().apply { topics.forEach { t ->
        put(JSONObject().put("title",t.title).put("summary",t.summary)
            .put("sources",JSONArray(EvidenceRef.toJson(t.sources)))
            .put("actions",JSONArray(ActionDraftCodec.encode(t.actions))))
    } }.toString()
    fun decode(raw: String): List<TopicDraft> {
        val array=JSONArray(raw)
        return (0 until array.length()).map { i -> array.getJSONObject(i).let {
            TopicDraft(it.getString("title"),it.getString("summary"),
                EvidenceRef.fromJson(it.getJSONArray("sources").toString()),
                ActionDraftCodec.decode(it.getJSONArray("actions").toString()))
        } }
    }
}

fun stableId(vararg values: Any?): String = MessageDigest.getInstance("SHA-256")
    .digest(values.joinToString("\u001f").toByteArray()).take(16).joinToString("") { "%02x".format(it) }

/** Bound every request while keeping every character, including the tail of long imports. */
fun splitSource(source: SourceText, maxCharacters: Int = 6000): List<Pair<SourceText, Int>> {
    require(maxCharacters > 0)
    return source.text.chunked(maxCharacters).mapIndexed { index, text ->
        source.copy(text = text) to index * maxCharacters
    }
}

object TaskState {
    const val CANDIDATE = "CANDIDATE"
    const val ACTIVE = "ACTIVE"
    const val DONE = "DONE"
    const val IGNORED = "IGNORED"
}
