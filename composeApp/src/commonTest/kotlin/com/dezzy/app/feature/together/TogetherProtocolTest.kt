/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.together

import com.dezzy.app.core.model.Track
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Sobre JSON, gzip y parseo de mensajes del servidor (sin red). */
class TogetherProtocolTest {

    @Test fun envelopeRoundTrip() {
        val payload = buildJsonObject { put("username", "Ana"); put("is_public", true) }
        val text = TogetherProtocol.encode("create_room", payload)
        val env = TogetherProtocol.json.parseToJsonElement(text).jsonObject
        assertEquals("create_room", env["type"]!!.jsonPrimitive.content)
        assertEquals("false", env["compressed"]!!.jsonPrimitive.content)
        // Los bytes van como lista de enteros 0..255.
        assertTrue(env["payload"]!!.jsonArray.all { it.jsonPrimitive.content.toInt() in 0..255 })
        val msg = assertNotNull(TogetherProtocol.decode(text))
        assertEquals("create_room", msg.type)
        assertEquals("Ana", msg.payload["username"]!!.jsonPrimitive.content)
    }

    @Test fun envelopeWithUtf8() {
        val msg = assertNotNull(TogetherProtocol.decode(TogetherProtocol.encode("chat", buildJsonObject { put("message", "¡Hola, canción ñ 🎵!") })))
        assertEquals("¡Hola, canción ñ 🎵!", msg.payload["message"]!!.jsonPrimitive.content)
    }

    @Test fun gzipRoundTrip() {
        val data = ("dezzy".repeat(500)).encodeToByteArray()
        val zipped = gzipCompress(data)
        assertTrue(zipped.size < data.size)
        assertEquals(data.decodeToString(), gzipDecompress(zipped).decodeToString())
    }

    @Test fun envelopeCompressed() {
        val payload = buildJsonObject { put("message", "x".repeat(1000)) }
        val text = TogetherProtocol.encode("chat", payload, compress = true)
        assertTrue(TogetherProtocol.json.parseToJsonElement(text).jsonObject["compressed"]!!.jsonPrimitive.content == "true")
        val msg = assertNotNull(TogetherProtocol.decode(text))
        assertEquals(1000, msg.payload["message"]!!.jsonPrimitive.content.length)
    }

    @Test fun decodeBase64PayloadAndEmpty() {
        // {"a":1} en base64
        val b64 = """{"type":"ping","payload":"eyJhIjoxfQ==","compressed":false}"""
        val msg = assertNotNull(TogetherProtocol.decode(b64))
        assertEquals("1", msg.payload["a"]!!.jsonPrimitive.content)
        val empty = assertNotNull(TogetherProtocol.decode("""{"type":"pong","payload":[],"compressed":false}"""))
        assertTrue(empty.payload.isEmpty())
    }

    @Test fun decodeRejectsGarbage() {
        assertNull(TogetherProtocol.decode("no es json"))
        assertNull(TogetherProtocol.decode("""{"payload":[]}"""))
        assertNull(TogetherProtocol.decode("""{"type":"x","payload":[1,2,3],"compressed":true}"""))
    }

    @Test fun roomCodes() {
        assertEquals("ABCD12", TogetherProtocol.parseRoomCode("abcd12"))
        assertEquals("ABCD12", TogetherProtocol.parseRoomCode("dezzy://together?code=abcd12"))
        assertEquals("ABCD12", TogetherProtocol.parseRoomCode(" ab-cd 12 "))
        assertNull(TogetherProtocol.parseRoomCode("ab"))
        assertNull(TogetherProtocol.parseRoomCode("DEMASIADOLARGO123"))
    }

    @Test fun trackParsing() {
        val t = assertNotNull(
            TogetherProtocol.toTrack(
                buildJsonObject {
                    put("id", "abc")
                    put("title", "Canción")
                    put("artist", "Ana")
                    put("duration", 215000)
                    put("thumbnail", "http://inseguro/x.jpg")
                    put("suggested_by", "Luis")
                },
            ),
        )
        assertEquals("abc", t.id)
        assertEquals(215000L, t.duration)
        assertNull(t.thumbnail) // sólo https
        assertEquals("Luis", t.suggestedBy)
        assertNull(TogetherProtocol.toTrack(buildJsonObject { put("title", "sin id") }))
    }

    @Test fun roomParsing() {
        val raw = buildJsonObject {
            put("room_code", "ABCD")
            put("host_id", "h1")
            put("is_playing", true)
            put("position", 1234)
            put(
                "users",
                JsonArray(
                    listOf(
                        buildJsonObject { put("user_id", "h1"); put("username", "Ana"); put("is_host", true) },
                        buildJsonObject { put("user_id", "g1"); put("is_connected", false) },
                        JsonPrimitive("basura"),
                    ),
                ),
            )
            put("current_track", buildJsonObject { put("id", "v1"); put("title", "T") })
            put("settings", buildJsonObject { put("allowGuestsToAddTracks", false); put("autoQueueVotes", 99) })
        }
        val room = assertNotNull(TogetherProtocol.toRoom(raw, now = 42))
        assertEquals("ABCD", room.code)
        assertEquals(2, room.users.size)
        assertEquals("Invitado", room.users[1].name)
        assertFalse(room.users[1].connected)
        assertTrue(room.playing)
        assertEquals(1234L, room.position)
        assertEquals(42L, room.at)
        assertEquals("v1", room.track?.id)
        assertFalse(room.settings.allowGuestsToAddTracks)
        assertEquals(20, room.settings.autoQueueVotes)
        // SYNC_STATE sin room_code no es una sala completa.
        assertNull(TogetherProtocol.toRoom(buildJsonObject { put("position", 5) }, now = 0))
    }

    @Test fun settingsDefaults() {
        val s = TogetherProtocol.toSettings(null)
        assertFalse(s.allowGuestsToControlPlayback)
        assertTrue(s.allowGuestsToAddTracks)
        assertFalse(s.requireHostApprovalToJoin)
        assertEquals(0, s.autoQueueVotes)
    }

    @Test fun suggestionParsing() {
        val raw = buildJsonObject {
            put("suggestion_id", "s1")
            put("from_user_id", "g1")
            put("track_info", buildJsonObject { put("id", "v1") })
            put("votes", 0)
            put("voters", JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"))))
        }
        val s = assertNotNull(TogetherProtocol.toSuggestion(raw))
        assertEquals("Invitado", s.fromName)
        assertEquals(2, s.votes) // al menos tantos como votantes
        assertNull(TogetherProtocol.toSuggestion(buildJsonObject { put("suggestion_id", "x") }))
    }

    @Test fun trackInfoMapping() {
        val info = assertNotNull(TogetherProtocol.trackInfo(Track(videoId = "v1", title = "T", artists = "A", durationSeconds = 200)))
        assertEquals("v1", info["id"]!!.jsonPrimitive.content)
        assertEquals("200000", info["duration"]!!.jsonPrimitive.content)
        assertNull(TogetherProtocol.trackInfo(Track(videoId = null, title = "T", artists = "A")))
        val back = TogetherProtocol.roomTrackToTrack(RoomTrack("v1", "T", "A", duration = 200000))
        assertEquals(200L, back.durationSeconds)
        assertEquals("v1", back.videoId)
    }

    @Test fun publicRooms() {
        val body = """{"rooms":[
            {"code":"abcd","hostName":"Ana","theme":"LOFI","participantCount":3,"currentTrackTitle":"T","currentTrackThumbnail":"https://x/y.jpg"},
            {"code":"ZZZZ","hostName":"Test user"},
            {"code":"PRIV","hostName":"Luis","isPublic":false},
            {"code":"!!","hostName":"Mal"},
            {"code":"EFGH","theme":"NOPE"}
        ]}"""
        val rooms = assertNotNull(parsePublicRooms(body))
        assertEquals(listOf("ABCD", "EFGH"), rooms.map { it.code })
        assertEquals(RoomTheme.LOFI, rooms[0].theme)
        assertEquals(3, rooms[0].participants)
        assertEquals("https://x/y.jpg", rooms[0].thumbnail)
        assertEquals("Anfitrión", rooms[1].hostName)
        assertEquals(RoomTheme.MIXED, rooms[1].theme)
        assertNull(parsePublicRooms("no es json"))
    }

    @Test fun emptyPayloadObject() {
        assertTrue(JsonObject(emptyMap()).isEmpty())
    }
}
