/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.youtube

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Un comentario público de un vídeo de YouTube. */
data class VideoComment(
    val id: String,
    val author: String,
    val avatar: String?,
    val text: String,
    val published: String,
    val likes: String,
    val replies: String,
)

/** Una página de comentarios y el token para pedir la siguiente (null si no hay más). */
data class VideoCommentsPage(val comments: List<VideoComment>, val next: String?)

/**
 * Comentarios públicos de un vídeo (cliente web de YouTube, sin sesión): la primera llamada
 * a «next» con el vídeo trae el token de la sección de comentarios; con él llegan de 20 en 20.
 */
class YouTubeComments(private val http: HttpClient) {

    suspend fun first(videoId: String, hl: String = "es", gl: String = "US"): VideoCommentsPage {
        val watch = post(buildJsonObject { put("context", context(hl, gl)); put("videoId", videoId) })
        val token = commentsToken(watch) ?: return VideoCommentsPage(emptyList(), null)
        return page(token, hl, gl)
    }

    suspend fun page(token: String, hl: String = "es", gl: String = "US"): VideoCommentsPage {
        val res = post(buildJsonObject { put("context", context(hl, gl)); put("continuation", token) })
        val mutations = (res.obj("frameworkUpdates")?.obj("entityBatchUpdate")?.get("mutations") as? JsonArray).orEmpty()
        val comments = mutations.mapNotNull { m ->
            val p = (m as? JsonObject)?.obj("payload")?.obj("commentEntityPayload") ?: return@mapNotNull null
            val props = p.obj("properties") ?: return@mapNotNull null
            if ((props.str("replyLevel")?.toIntOrNull() ?: 0) > 0) return@mapNotNull null
            val author = p.obj("author")
            val toolbar = p.obj("toolbar")
            VideoComment(
                id = props.str("commentId") ?: return@mapNotNull null,
                author = author?.str("displayName").orEmpty(),
                avatar = author?.str("avatarThumbnailUrl"),
                text = props.obj("content")?.str("content").orEmpty(),
                published = props.str("publishedTime").orEmpty(),
                likes = toolbar?.str("likeCountNotliked").orEmpty(),
                replies = toolbar?.str("replyCount").orEmpty(),
            )
        }
        // El token de la página siguiente es el último continuationCommand de los endpoints.
        val next = res["onResponseReceivedEndpoints"]?.let { findTokens(it).lastOrNull() }
        return VideoCommentsPage(comments, next.takeIf { comments.isNotEmpty() })
    }

    private fun commentsToken(watch: JsonObject): String? {
        var found: String? = null
        fun walk(e: JsonElement) {
            if (found != null) return
            when (e) {
                is JsonObject -> {
                    val section = e.obj("itemSectionRenderer")
                    if (section != null && section.str("sectionIdentifier") == "comment-item-section") {
                        found = findTokens(section).firstOrNull()
                        return
                    }
                    e.values.forEach { walk(it) }
                }
                is JsonArray -> e.forEach { walk(it) }
                else -> Unit
            }
        }
        walk(watch)
        return found
    }

    private fun findTokens(e: JsonElement): List<String> {
        val out = ArrayList<String>()
        fun walk(x: JsonElement) {
            when (x) {
                is JsonObject -> {
                    x.obj("continuationCommand")?.str("token")?.let { out += it }
                    x.values.forEach { walk(it) }
                }
                is JsonArray -> x.forEach { walk(it) }
                else -> Unit
            }
        }
        walk(e)
        return out
    }

    private fun context(hl: String, gl: String) = buildJsonObject {
        putJsonObject("client") {
            put("clientName", "WEB")
            put("clientVersion", CLIENT_VERSION)
            put("hl", hl)
            put("gl", gl)
        }
    }

    private suspend fun post(payload: JsonObject): JsonObject =
        http.post(URL) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Origin, "https://www.youtube.com")
            header(HttpHeaders.Referrer, "https://www.youtube.com/")
            setBody(payload)
        }.body()

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content

    private companion object {
        const val URL = "https://www.youtube.com/youtubei/v1/next?prettyPrint=false"
        const val CLIENT_VERSION = "2.20251120.01.00"
    }
}
