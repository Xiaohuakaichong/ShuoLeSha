package com.example.shuolesa.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 结构化待办事项模型，支持完成状态标记与向下兼容的 JSON 序列化。
 */
data class ActionItemModel(
    val text: String,
    val isDone: Boolean = false,
    val quote: String? = null,
    val whenHint: String? = null,
) {
    companion object {
        fun fromJsonString(jsonStr: String?): List<ActionItemModel> {
            if (jsonStr.isNullOrBlank()) return emptyList()
            val list = mutableListOf<ActionItemModel>()
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val item = array.opt(i)
                    when (item) {
                        is JSONObject -> {
                            val text = item.optString("text", item.optString("task", item.optString("title", ""))).trim()
                            val done = item.optBoolean("done", item.optBoolean("isDone", false))
                            val quote = item.optString("quote", "").trim().ifBlank { null }
                            val whenHint = item.optString("when", item.optString("whenHint", "")).trim().ifBlank { null }
                            if (text.isNotEmpty()) {
                                list.add(ActionItemModel(text = text, isDone = done, quote = quote, whenHint = whenHint))
                            }
                        }
                        is String -> {
                            val text = item.trim()
                            if (text.isNotEmpty()) {
                                list.add(ActionItemModel(text = text, isDone = false))
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                jsonStr.lines().forEach { line ->
                    val clean = line.trim().removePrefix("-").removePrefix("*").trim()
                    if (clean.isNotEmpty()) {
                        list.add(ActionItemModel(text = clean, isDone = false))
                    }
                }
            }
            return list
        }

        fun toJsonString(items: List<ActionItemModel>): String {
            val array = JSONArray()
            for (item in items) {
                val obj = JSONObject().apply {
                    put("text", item.text)
                    put("done", item.isDone)
                    if (!item.quote.isNullOrBlank()) put("quote", item.quote)
                    if (!item.whenHint.isNullOrBlank()) put("when", item.whenHint)
                }
                array.put(obj)
            }
            return array.toString()
        }
    }
}
