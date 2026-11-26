/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Traducción de letras con el endpoint público `gtx` de Google Translate (sin clave; el idioma de
 * origen se detecta solo). Sólo viaja la letra.
 */
class LyricsTranslator(private val http: HttpClient) {
    private val cache = LinkedHashMap<String, List<String>>()
    private val lock = Mutex()

    private suspend fun translateChunk(text: String, lang: String): String {
        val res = http.post("https://translate.googleapis.com/translate_a/single") {
            timeout { requestTimeoutMillis = 15_000 }
            parameter("client", "gtx")
            parameter("sl", "auto")
            parameter("tl", lang)
            parameter("dt", "t")
            setBody(TextContent("q=${text.encodeURLParameter()}", ContentType.parse("application/x-www-form-urlencoded;charset=UTF-8")))
        }
        if (!res.status.isSuccess()) throw IllegalStateException("HTTP ${res.status.value}")
        val parts = (Json.parseToJsonElement(res.bodyAsText()) as? JsonArray)?.firstOrNull() as? JsonArray ?: return ""
        return parts.joinToString("") { part ->
            ((part as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
        }
    }

    /** Traduce línea a línea; devuelve tantas líneas como entran (las vacías se quedan vacías). */
    suspend fun translate(lines: List<String>, lang: String): List<String> {
        val key = lang + "\u0000" + lines.joinToString("\n")
        lock.withLock { cache.remove(key)?.let { cache[key] = it; return it } }
        val out = MutableList(lines.size) { "" }
        var chunk = ArrayList<Int>()
        var size = 0
        suspend fun flush() {
            val idx = chunk.filter { lines[it].isNotBlank() }
            chunk = ArrayList()
            size = 0
            if (idx.isEmpty()) return
            val translated = translateChunk(idx.joinToString("\n") { lines[it].replace('\n', ' ') }, lang).split("\n")
            idx.forEachIndexed { k, i -> out[i] = (translated.getOrNull(k) ?: "").trim() }
        }
        for (i in lines.indices) {
            if (size + lines[i].length + 1 > MAX_CHARS) flush()
            chunk.add(i)
            size += lines[i].length + 1
        }
        flush()
        lock.withLock {
            cache[key] = out
            while (cache.size > 50) cache.remove(cache.keys.first())
        }
        return out
    }

    private companion object {
        const val MAX_CHARS = 4500
    }
}
