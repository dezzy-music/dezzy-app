/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.abs

/** Consulta ya limpia que reciben las fuentes. */
internal class LyricsLookup(
    val videoId: String?,
    val title: String,
    val artist: String,
    val album: String?,
    val duration: Int?,
)

internal typealias LyricsProvider = suspend (HttpClient, LyricsLookup) -> LyricsResult?

private fun provider(name: String, f: LyricsProvider): Pair<String, LyricsProvider> = name to f

/** Orden: LRCLIB, SimpMusic, BetterLyrics, KuGou. */
internal val lyricsProviders: List<Pair<String, LyricsProvider>> = listOf(
    provider("LRCLIB") { http, q -> fromLrclib(http, q) },
    provider("SimpMusic") { http, q -> fromSimpMusic(http, q) },
    provider("BetterLyrics") { http, q -> fromBetterLyrics(http, q) },
    provider("KuGou") { http, q -> fromKuGou(http, q) },
)

private const val LRCLIB = "https://lrclib.net/api"
private const val LRCLIB_AGENT = "Dezzy/1.0 (lrclib client)"
private const val TOLERANCE_SEC = 8

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
private fun JsonObject.dbl(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

private suspend fun HttpClient.getTimed(
    url: String,
    timeoutMs: Long,
    block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
): HttpResponse = get(url) {
    timeout { requestTimeoutMillis = timeoutMs }
    block()
}

// LRCLIB ---------------------------------------------------------------------

private suspend fun lrclib(http: HttpClient, path: String, params: Map<String, String>): JsonElement? {
    val res = http.getTimed("$LRCLIB/$path", 8000) {
        header(HttpHeaders.UserAgent, LRCLIB_AGENT)
        params.forEach { (k, v) -> parameter(k, v) }
    }
    if (res.status.value == 404) return null
    if (!res.status.isSuccess()) throw IllegalStateException("LRCLIB respondió ${res.status.value}")
    return json.parseToJsonElement(res.bodyAsText())
}

private fun lrclibEntry(entry: JsonObject?): LyricsResult? {
    if (entry == null) return null
    val synced = entry.str("syncedLyrics")?.let { parseLrc(it) }
    val plain = entry.str("plainLyrics")?.trim()?.takeIf { it.isNotEmpty() }
    if (!synced.isNullOrEmpty() || plain != null) {
        return LyricsResult(synced?.takeIf { it.isNotEmpty() }, plain, "LRCLIB")
    }
    return null
}

private suspend fun fromLrclib(http: HttpClient, q: LyricsLookup): LyricsResult? {
    val params = mutableMapOf("track_name" to q.title, "artist_name" to q.artist)
    if (!q.album.isNullOrEmpty()) params["album_name"] = q.album
    if (q.duration != null) params["duration"] = q.duration.toString()
    lrclibEntry(lrclib(http, "get", params).obj())?.let { return it }
    // Sin coincidencia exacta: se busca y se elige la de duración más parecida.
    val list = lrclib(http, "search", mapOf("track_name" to q.title, "artist_name" to q.artist)).arr()
        ?.mapNotNull { it.obj() }.orEmpty()
    fun diff(e: JsonObject): Double {
        val d = e.dbl("duration")
        return if (q.duration != null && d != null && d != 0.0) abs(d - q.duration) else 99.0
    }
    val best = list
        .filter { !it.str("syncedLyrics").isNullOrEmpty() || !it.str("plainLyrics").isNullOrEmpty() }
        .sortedWith(compareBy<JsonObject>({ if (it.str("syncedLyrics").isNullOrEmpty()) 1 else 0 }, { diff(it) }))
        .firstOrNull() ?: return null
    val bestDur = best.dbl("duration")
    if (q.duration == null || bestDur == null || bestDur == 0.0 || abs(bestDur - q.duration) <= TOLERANCE_SEC) {
        return lrclibEntry(best)
    }
    return null
}

// Utilidades comunes ----------------------------------------------------------

private fun fromText(text: String?, source: String): LyricsResult? {
    val t = text?.trim()
    if (t.isNullOrEmpty()) return null
    val synced = parseLrc(t)
    if (synced.size >= 3) return LyricsResult(synced, null, source)
    val plain = stripWordStamps(t)
    return if (plain.isNotEmpty()) LyricsResult(null, plain, source) else null
}

// SimpMusic -------------------------------------------------------------------

private suspend fun fromSimpMusic(http: HttpClient, q: LyricsLookup): LyricsResult? {
    val videoId = q.videoId ?: return null
    val res = http.getTimed("https://api-lyrics.simpmusic.org/v1/${videoId.encodeURLParameter()}", 10_000) {
        header(HttpHeaders.Accept, "application/json")
        header(HttpHeaders.UserAgent, "SimpMusicLyrics/1.0")
    }
    if (!res.status.isSuccess()) return null
    val body = json.parseToJsonElement(res.bodyAsText()).obj() ?: return null
    if (body.str("type") != "success") return null
    val data = body["data"].arr()?.mapNotNull { it.obj() }.orEmpty()
    if (data.isEmpty()) return null
    val d = q.duration
    val list = if (d != null) data.sortedBy { abs((it.dbl("durationSeconds") ?: 0.0) - d) } else data
    val best = list.first()
    val bestDur = best.dbl("durationSeconds")
    if (d != null && bestDur != null && bestDur != 0.0 && abs(bestDur - d) > TOLERANCE_SEC) return null
    return fromText(best.str("syncedLyrics"), "SimpMusic") ?: fromText(best.str("plainLyric"), "SimpMusic")
}

// BetterLyrics (TTML) ---------------------------------------------------------

private suspend fun fromBetterLyrics(http: HttpClient, q: LyricsLookup): LyricsResult? {
    for (path in listOf("getLyrics", "kugou/getLyrics")) {
        try {
            val res = http.getTimed("https://lyrics-api.boidu.dev/$path", 15_000) {
                parameter("s", q.title)
                parameter("a", q.artist)
                if (!q.album.isNullOrEmpty()) parameter("al", q.album)
                if (q.duration != null) parameter("d", q.duration.toString())
            }
            if (!res.status.isSuccess()) continue
            val raw = res.bodyAsText().trim()
            val ttml = if (raw.startsWith("<")) raw else json.parseToJsonElement(raw).obj()?.str("ttml").orEmpty()
            val synced = if (ttml.trim().startsWith("<")) parseTtml(ttml) else parseLrc(ttml)
            if (synced.size >= 3) return LyricsResult(synced, null, "BetterLyrics")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Se prueba el siguiente punto de la API.
        }
    }
    return null
}

// KuGou -----------------------------------------------------------------------

/** Líneas de créditos de KuGou («[00:01.00]作词：…»). */
private val KUGOU_CREDIT = Regex("""^.+\].+[:：].+$""")
private val KUGOU_LINE = Regex("""^\[\d\d:\d\d\.\d{2,3}\].*""")
private val KUGOU_TITLE = Regex("""\(.*?\)|（.*?）|「.*?」|『.*?』|<.*?>|《.*?》|〈.*?〉|＜.*?＞""")
private val KUGOU_ARTIST_PARENS = Regex("""\(.*?\)|（.*?）""")
private val KUGOU_ARTIST_SEP = Regex(""", | & """)

/** Quita los créditos del principio y del final. */
private fun cleanKugou(lrc: String): String {
    val lines = lrc.replace("&apos;", "'").split(Regex("""\r?\n""")).filter { KUGOU_LINE.matches(it) }
    var head = 0
    for (i in minOf(30, lines.size - 1) downTo 0) {
        if (KUGOU_CREDIT.matches(lines[i])) {
            head = i + 1
            break
        }
    }
    var tail = 0
    for (i in minOf(lines.size - 30, lines.size - 1) downTo 0) {
        if (KUGOU_CREDIT.matches(lines[lines.size - 1 - i])) {
            tail = i + 1
            break
        }
    }
    val end = if (tail > 0) lines.size - tail else lines.size
    return if (head >= end) "" else lines.subList(head, end).joinToString("\n")
}

private suspend fun kugouJson(http: HttpClient, url: String): JsonObject? {
    val res = http.getTimed(url, 10_000)
    if (!res.status.isSuccess()) return null
    // KuGou a veces antepone texto antes del JSON.
    val text = res.bodyAsText()
    val at = text.indexOf('{')
    return if (at < 0) null else json.parseToJsonElement(text.substring(at)).obj()
}

private suspend fun kugouJsonOrNull(http: HttpClient, url: String): JsonObject? =
    try {
        kugouJson(http, url)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

private fun JsonObject.firstCandidate(): Pair<String, String>? {
    val c = this["candidates"].arr()?.firstOrNull().obj() ?: return null
    val id = (c["id"] as? JsonPrimitive)?.content ?: return null
    val key = c.str("accesskey") ?: return null
    return id to key
}

@OptIn(ExperimentalEncodingApi::class)
private suspend fun fromKuGou(http: HttpClient, q: LyricsLookup): LyricsResult? {
    val title = q.title.replace(KUGOU_TITLE, "").trim()
    val artist = q.artist.replace(KUGOU_ARTIST_SEP, "、").replace(".", "").replace(KUGOU_ARTIST_PARENS, "").trim()
    val keyword = "$title - $artist".encodeURLParameter()
    val dur = q.duration
    var candidate: Pair<String, String>? = null
    val songs = kugouJsonOrNull(
        http,
        "https://mobileservice.kugou.com/api/v3/search/song?version=9108&plat=0&pagesize=8&showtype=0&keyword=$keyword",
    )
    val infos = songs?.get("data").obj()?.get("info").arr()?.mapNotNull { it.obj() }.orEmpty()
    for (song in infos) {
        val songDur = song.dbl("duration")
        if (dur != null && songDur != null && abs(songDur - dur) > TOLERANCE_SEC) continue
        val hash = song.str("hash") ?: continue
        val byHash = kugouJsonOrNull(
            http,
            "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=${hash.encodeURLParameter()}",
        )
        candidate = byHash?.firstCandidate()
        if (candidate != null) break
    }
    if (candidate == null) {
        val durPart = if (dur != null) "&duration=${dur * 1000}" else ""
        val byWord = kugouJson(http, "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc$durPart&keyword=$keyword")
        candidate = byWord?.firstCandidate()
    }
    val (id, accessKey) = candidate ?: return null
    val dl = kugouJson(
        http,
        "https://lyrics.kugou.com/download?fmt=lrc&charset=utf8&client=pc&ver=1&id=${id.encodeURLParameter()}&accesskey=${accessKey.encodeURLParameter()}",
    )
    val content = dl?.str("content")?.takeIf { it.isNotEmpty() } ?: return null
    val decoded = Base64.decode(content).decodeToString()
    val synced = parseLrc(cleanKugou(decoded))
    return if (synced.size >= 3) LyricsResult(synced, null, "KuGou") else null
}
