package com.example.shuolesa.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一次录音里按话题切开的一段记忆。说话人只使用匿名编号。
 */
data class MemorySegment(
    val title: String,
    val summary: String,
    val transcript: String,
    val speakers: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val openQuestions: List<String> = emptyList(),
    val actionItems: List<ActionItemModel> = emptyList(),
) {
    companion object {
        fun parse(raw: String?): List<MemorySegment> {
            if (raw.isNullOrBlank()) return emptyList()
            val json = unwrap(raw) ?: return emptyList()
            val array = when {
                json.trim().startsWith("[") -> JSONArray(json)
                else -> JSONObject(json).optJSONArray("segments") ?: return emptyList()
            }
            return buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val title = obj.optString("title").trim().ifBlank { "片段 ${i + 1}" }
                    val summary = obj.optString("summary").trim()
                    val transcript = obj.optString("transcript").trim()
                    if (summary.isBlank() && transcript.isBlank() && title.isBlank()) continue
                    add(
                        MemorySegment(
                            title = title,
                            summary = summary,
                            transcript = transcript,
                            speakers = stringList(obj.optJSONArray("speakers")),
                            decisions = stringList(obj.optJSONArray("decisions")),
                            openQuestions = stringList(obj.optJSONArray("open_questions") ?: obj.optJSONArray("openQuestions")),
                            actionItems = ActionItemModel.fromJsonString(obj.optJSONArray("action_items")?.toString()
                                ?: obj.optJSONArray("actionItems")?.toString()),
                        ),
                    )
                }
            }
        }

        fun toJson(segments: List<MemorySegment>): String {
            val array = JSONArray()
            segments.forEach { segment ->
                array.put(
                    JSONObject().apply {
                        put("title", segment.title)
                        put("summary", segment.summary)
                        put("transcript", segment.transcript)
                        put("speakers", JSONArray(segment.speakers))
                        put("decisions", JSONArray(segment.decisions))
                        put("open_questions", JSONArray(segment.openQuestions))
                        put("action_items", JSONArray(ActionItemModel.toJsonString(segment.actionItems)))
                    },
                )
            }
            return array.toString()
        }

        fun parseAnswer(raw: String): MemoryAnswer? {
            val json = unwrap(raw) ?: return null
            return try {
                val obj = JSONObject(json)
                val answer = obj.optString("answer").trim()
                if (answer.isEmpty()) return null
                val ids = obj.optJSONArray("record_ids")
                val recordIds = buildList {
                    if (ids != null) {
                        for (i in 0 until ids.length()) {
                            val id = ids.optLong(i)
                            if (id > 0) add(id)
                        }
                    }
                }
                MemoryAnswer(answer, recordIds)
            } catch (_: Exception) {
                null
            }
        }

        fun fallback(title: String, summary: String, transcript: String, actions: List<ActionItemModel>): MemorySegment {
            return MemorySegment(
                title = title.ifBlank { "这段录音" },
                summary = summary.ifBlank { transcript.take(120) },
                transcript = transcript,
                speakers = listOf("说话人 1"),
                actionItems = actions,
            )
        }

        private fun stringList(array: JSONArray?): List<String> {
            if (array == null) return emptyList()
            return buildList {
                for (i in 0 until array.length()) {
                    val value = when (val item = array.opt(i)) {
                        is String -> item
                        is JSONObject -> item.optString("text", item.optString("title", ""))
                        else -> array.optString(i, "")
                    }.trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        }

        private fun unwrap(raw: String): String? {
            val clean = raw.trim()
            val fence = Regex("""```(?:json|JSON)?\s*([\s\S]*?)\s*```""").find(clean)
            val body = fence?.groupValues?.get(1)?.trim() ?: clean
            val startObj = body.indexOf('{')
            val startArr = body.indexOf('[')
            val start = listOf(startObj, startArr).filter { it >= 0 }.minOrNull() ?: return null
            val end = if (body[start] == '{') body.lastIndexOf('}') else body.lastIndexOf(']')
            if (end <= start) return null
            return body.substring(start, end + 1)
        }
    }
}

data class MemoryAnswer(
    val answer: String,
    val recordIds: List<Long>,
)
