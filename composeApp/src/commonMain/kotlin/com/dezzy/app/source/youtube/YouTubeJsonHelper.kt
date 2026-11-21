/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.youtube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object YouTubeJsonHelper {

    fun runsText(element: JsonElement?): String {
        if (element == null) return ""
        val runs = when {
            element is JsonObject && element.containsKey("runs") -> element["runs"]?.jsonArray
            element is JsonObject && element.containsKey("simpleText") -> return element["simpleText"]?.jsonPrimitive?.content ?: ""
            element is JsonArray -> element
            else -> null
        } ?: return ""

        return runs.joinToString("") { run ->
            (run as? JsonObject)?.get("text")?.jsonPrimitive?.content ?: ""
        }
    }

    fun pickThumb(element: JsonElement?): String? {
        val list = when {
            element is JsonObject && element.containsKey("thumbnails") -> element["thumbnails"]?.jsonArray
            element is JsonObject && element.containsKey("thumbnail") -> {
                val inner = element["thumbnail"]?.jsonObject
                inner?.get("thumbnails")?.jsonArray ?: inner?.get("contents")?.jsonArray
            }
            element is JsonArray -> element
            else -> null
        } ?: return null

        val parsed = list.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val url = obj["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val width = obj["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            width to url
        }

        if (parsed.isEmpty()) return null

        // Misma heurística que en Dezzy Desktop: la miniatura más pequeña que siga siendo nítida a 240px
        val sorted = parsed.sortedBy { it.first }
        return sorted.firstOrNull { it.first >= 240 }?.second ?: sorted.last().second
    }

    fun parseDurationSeconds(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val parts = text.trim().split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }
}
