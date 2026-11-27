/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.together

import com.dezzy.app.core.model.Track
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.roundToInt

/** Gzip sin red: `actual` en jvmShared. */
internal expect fun gzipCompress(data: ByteArray): ByteArray
internal expect fun gzipDecompress(data: ByteArray): ByteArray

/** Mensaje ya abierto del servidor: tipo y carga JSON. */
internal class TogetherMessage(val type: String, val payload: JsonObject)

/**
 * Protocolo de «Escuchar juntos»: sobre JSON `{type, payload: [bytes del JSON], compressed}`;
 * si `compressed`, los bytes van en gzip. Igual que `main/together.ts` de Dezzy Desktop.
 */
internal object TogetherProtocol {
    const val SERVER = "wss://dezzyyyy-listen-together.hf.space/ws"
    const val PUBLIC_ROOMS_URL = "https://dezzyyyy-listen-together.hf.space/api/v1/public-rooms"
    const val PING_MS = 25_000L
    const val MAX_CHAT = 200

    val ROOM_CODE_RE = Regex("^[A-Z0-9]{4,12}$")

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── Sobre ──────────────────────────────────────────────────────────────────

    fun encode(type: String, payload: JsonObject = JsonObject(emptyMap()), compress: Boolean = false): String {
        var bytes = payload.toString().encodeToByteArray()
        if (compress) bytes = gzipCompress(bytes)
        return buildJsonObject {
            put("type", type)
            put("payload", JsonArray(bytes.map { JsonPrimitive(it.toInt() and 0xFF) }))
            put("compressed", compress)
        }.toString()
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun decode(text: String): TogetherMessage? = try {
        val env = json.parseToJsonElement(text) as? JsonObject
        val type = (env?.get("type") as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (env == null || type == null) {
            null
        } else {
            var raw: ByteArray = when (val p = env["payload"]) {
                is JsonPrimitive -> if (p.isString) Base64.decode(p.content) else ByteArray(0)
                is JsonArray -> ByteArray(p.size) { i -> ((p[i] as? JsonPrimitive)?.doubleOrNull?.toInt() ?: 0).toByte() }
                else -> ByteArray(0)
            }
            val compressed = (env["compressed"] as? JsonPrimitive)?.booleanOrNull == true
            if (compressed && raw.isNotEmpty()) raw = gzipDecompress(raw)
            val payload = if (raw.isEmpty()) JsonObject(emptyMap()) else json.parseToJsonElement(raw.decodeToString()) as JsonObject
            TogetherMessage(type, payload)
        }
    } catch (_: Throwable) {
        null
    }

    // ── Conversión (cada campo se sanea, como en together.ts) ──────────────────

    fun JsonElement?.str(max: Int = 300): String {
        val p = this as? JsonPrimitive ?: return ""
        return if (p.isString) p.content.take(max) else ""
    }

    fun JsonElement?.num(): Double {
        val p = this as? JsonPrimitive ?: return 0.0
        if (p is JsonNull || p.isString) return 0.0
        val d = p.doubleOrNull ?: return 0.0
        return if (d.isNaN() || d.isInfinite()) 0.0 else d
    }

    fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    private fun JsonElement?.list(): List<JsonElement> = (this as? JsonArray) ?: emptyList()

    fun toTrack(raw: JsonElement?): RoomTrack? {
        val t = raw as? JsonObject ?: return null
        val id = t["id"].str(64)
        if (id.isEmpty()) return null
        val thumb = t["thumbnail"].str(1000)
        return RoomTrack(
            id = id,
            title = t["title"].str().ifEmpty { "Canción" },
            artist = t["artist"].str(),
            album = t["album"].str().ifEmpty { null },
            duration = t["duration"].num().toLong(),
            thumbnail = if (thumb.startsWith("https://")) thumb else null,
            suggestedBy = t["suggested_by"].str(80).ifEmpty { null },
        )
    }

    fun toTracks(raw: JsonElement?): List<RoomTrack> = raw.list().mapNotNull { toTrack(it) }

    fun toUser(raw: JsonElement?): RoomUser? {
        val u = raw as? JsonObject ?: return null
        val id = (u["user_id"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        return RoomUser(
            id = id,
            name = u["username"].str(80).ifEmpty { "Invitado" },
            isHost = u["is_host"].bool() == true,
            connected = u["is_connected"].bool() != false,
        )
    }

    fun toSettings(raw: JsonElement?): RoomSettings {
        val s = raw as? JsonObject ?: JsonObject(emptyMap())
        return RoomSettings(
            allowGuestsToControlPlayback = s["allowGuestsToControlPlayback"].bool() == true,
            allowGuestsToAddTracks = s["allowGuestsToAddTracks"].bool() != false,
            requireHostApprovalToJoin = s["requireHostApprovalToJoin"].bool() == true,
            isPublic = s["isPublic"].bool() == true,
            djModeEnabled = s["djModeEnabled"].bool() == true,
            autoQueueVotes = s["autoQueueVotes"].num().roundToInt().coerceIn(0, 20),
        )
    }

    fun toSuggestion(raw: JsonElement?): RoomSuggestion? {
        val s = raw as? JsonObject ?: return null
        val track = toTrack(s["track_info"]) ?: return null
        val id = (s["suggestion_id"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        val voters = s["voters"].list().mapNotNull { v -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content }.take(200)
        return RoomSuggestion(
            id = id.take(80),
            fromId = s["from_user_id"].str(80),
            fromName = s["from_username"].str(80).ifEmpty { "Invitado" },
            track = track,
            votes = maxOf(s["votes"].num().toInt(), voters.size),
            voters = voters,
        )
    }

    fun toSuggestions(raw: JsonElement?): List<RoomSuggestion> = raw.list().mapNotNull { toSuggestion(it) }.take(100)

    /** Estado completo de la sala; null si no trae `room_code`. */
    fun toRoom(raw: JsonElement?, now: Long): RoomSnapshot? {
        val r = raw as? JsonObject ?: return null
        val code = (r["room_code"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        return RoomSnapshot(
            code = code,
            hostId = r["host_id"].str(80),
            users = r["users"].list().mapNotNull { toUser(it) },
            track = toTrack(r["current_track"]),
            playing = r["is_playing"].bool() == true,
            position = r["position"].num().toLong(),
            at = now,
            queue = toTracks(r["queue"]),
            settings = toSettings(r["settings"]),
        )
    }

    /** Informe de un oyente (el servidor lo reenvía al anfitrión). */
    fun toListener(raw: JsonElement?, now: Long): ListenerInfo? {
        val l = raw as? JsonObject ?: return null
        val id = l["user_id"].str(80)
        if (id.isEmpty()) return null
        return ListenerInfo(
            userId = id,
            trackId = l["track_id"].str(64).ifEmpty { null },
            positionMs = l["position"].num().toLong().coerceAtLeast(0),
            playing = l["is_playing"].bool() == true,
            buffering = l["buffering"].bool() == true,
            at = now,
        )
    }

    /** Canción de Dezzy → TrackInfo del servidor (sólo las que tienen videoId). */
    fun trackInfo(track: Track): JsonObject? {
        val id = track.videoId?.takeIf { it.isNotEmpty() } ?: return null
        return buildJsonObject {
            put("id", id)
            put("title", track.title.take(300))
            put("artist", track.artists.take(300))
            put("album", track.album)
            put("duration", (track.durationSeconds ?: 0L) * 1000)
            put("thumbnail", track.thumbnail)
        }
    }

    /** Canción de la sala → canción del reproductor (la duración llega en ms). */
    fun roomTrackToTrack(t: RoomTrack): Track = Track(
        videoId = t.id,
        title = t.title,
        artists = t.artist,
        album = t.album,
        thumbnail = t.thumbnail,
        durationSeconds = if (t.duration > 0) t.duration / 1000 else null,
    )

    /** Saca el código de una sala de «dezzy://together?code=XXXX» o de un código suelto; null si no vale. */
    fun parseRoomCode(input: String): String? {
        val trimmed = input.trim()
        val raw = if (trimmed.contains("code=", ignoreCase = true)) {
            trimmed.substring(trimmed.indexOf("code=", ignoreCase = true) + 5).substringBefore('&').substringBefore('#')
        } else {
            trimmed
        }
        val clean = raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }
        return clean.takeIf { ROOM_CODE_RE.matches(it) }
    }
}
