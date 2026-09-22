package com.example.shuolesa.data.model

import org.json.JSONArray
import org.json.JSONObject

data class TimelineHighlight(
    val timePeriod: String,
    val title: String,
    val content: String,
)

data class LifeLogResult(
    val title: String,
    val summary: String,
    val timelineHighlights: List<TimelineHighlight> = emptyList(),
    val socialAndChats: String? = null,
    val workAndDecisions: String? = null,
    val unifiedActionItems: List<ActionItemModel> = emptyList(),
    val dailyQuote: String? = null,
    val rawJson: String = "",
) {
    fun toMarkdown(dateStr: String): String = buildString {
        appendLine("# 🌿 $title")
        appendLine("- **日期**：$dateStr")
        appendLine()
        appendLine("## 📖 全天生活心境综述")
        appendLine(summary)
        appendLine()

        if (!dailyQuote.isNullOrBlank()) {
            appendLine("> 💡 *“$dailyQuote”*")
            appendLine()
        }

        if (timelineHighlights.isNotEmpty()) {
            appendLine("## ⏱️ 生活轨迹时间轴")
            timelineHighlights.forEach { h ->
                appendLine("- **【${h.timePeriod}】${h.title}**：${h.content}")
            }
            appendLine()
        }

        if (!socialAndChats.isNullOrBlank()) {
            appendLine("## 💬 朋友与人际闲聊亮点")
            appendLine(socialAndChats)
            appendLine()
        }

        if (!workAndDecisions.isNullOrBlank()) {
            appendLine("## 💼 工作推进与关键决策")
            appendLine(workAndDecisions)
            appendLine()
        }

        if (unifiedActionItems.isNotEmpty()) {
            appendLine("## ☑️ 今日聚合待办清单")
            unifiedActionItems.forEach { item ->
                val mark = if (item.isDone) "[x]" else "[ ]"
                appendLine("- $mark ${item.text}")
            }
            appendLine()
        }
    }

    companion object {
        fun fromJson(rawContent: String): LifeLogResult {
            val clean = rawContent.trim()
            val fenceRegex = Regex("""```(?:json|JSON)?\s*([\s\S]*?)\s*```""")
            val fenceMatch = fenceRegex.find(clean)
            val jsonCandidate = if (fenceMatch != null) {
                fenceMatch.groupValues[1].trim()
            } else {
                val firstBrace = clean.indexOf('{')
                val lastBrace = clean.lastIndexOf('}')
                if (firstBrace != -1 && lastBrace > firstBrace) {
                    clean.substring(firstBrace, lastBrace + 1).trim()
                } else {
                    clean
                }
            }

            return try {
                val json = JSONObject(jsonCandidate)
                val title = json.optString("title", "今日生活手记")
                val summary = json.optString("summary", "今天度过了充实的一天。")
                val dailyQuote = json.optString("daily_quote", "").ifBlank { null }
                val socialAndChats = json.optString("social_and_chats", "").ifBlank { null }
                val workAndDecisions = json.optString("work_and_decisions", "").ifBlank { null }

                val highlights = mutableListOf<TimelineHighlight>()
                val hlArray = json.optJSONArray("timeline_highlights")
                if (hlArray != null) {
                    for (i in 0 until hlArray.length()) {
                        val item = hlArray.optJSONObject(i)
                        if (item != null) {
                            val period = item.optString("time", item.optString("timePeriod", "时段"))
                            val t = item.optString("title", "")
                            val c = item.optString("content", "")
                            if (t.isNotEmpty() || c.isNotEmpty()) {
                                highlights.add(TimelineHighlight(period, t, c))
                            }
                        }
                    }
                }

                val actionItems = mutableListOf<ActionItemModel>()
                val actionsArray = json.optJSONArray("unified_action_items")
                    ?: json.optJSONArray("action_items")
                    ?: json.optJSONArray("todos")
                if (actionsArray != null) {
                    for (i in 0 until actionsArray.length()) {
                        val item = actionsArray.opt(i)
                        when (item) {
                            is JSONObject -> {
                                val text = item.optString("text", item.optString("task", "")).trim()
                                val done = item.optBoolean("done", false)
                                if (text.isNotEmpty()) actionItems.add(ActionItemModel(text, done))
                            }
                            is String -> {
                                val text = item.trim()
                                if (text.isNotEmpty()) actionItems.add(ActionItemModel(text, false))
                            }
                        }
                    }
                }

                LifeLogResult(
                    title = title,
                    summary = summary,
                    timelineHighlights = highlights,
                    socialAndChats = socialAndChats,
                    workAndDecisions = workAndDecisions,
                    unifiedActionItems = actionItems,
                    dailyQuote = dailyQuote,
                    rawJson = clean,
                )
            } catch (_: Exception) {
                LifeLogResult(
                    title = "今日生活手记",
                    summary = rawContent,
                    timelineHighlights = emptyList(),
                    socialAndChats = null,
                    workAndDecisions = null,
                    unifiedActionItems = emptyList(),
                    dailyQuote = null,
                    rawJson = rawContent,
                )
            }
        }
    }
}
