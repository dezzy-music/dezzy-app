/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core

import com.dezzy.app.core.model.LyricLine
import com.dezzy.app.core.model.Lyrics
import com.dezzy.app.core.model.SessionProvider
import com.dezzy.app.core.model.Track
import com.dezzy.app.core.model.UserSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ModelSerializationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun testTrackSerialization() {
        val original = Track(
            videoId = "dQw4w9WgXcQ",
            title = "Never Gonna Give You Up",
            artists = "Rick Astley",
            album = "Whenever You Need Somebody",
            thumbnail = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            durationSeconds = 213,
            spotifyId = "4cOdK2wGLETKBW3PvgPWqT"
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<Track>(encoded)

        assertEquals(original.videoId, decoded.videoId)
        assertEquals(original.title, decoded.title)
        assertEquals(original.artists, decoded.artists)
        assertEquals(original.album, decoded.album)
        assertEquals(original.durationSeconds, decoded.durationSeconds)
        assertEquals(original.spotifyId, decoded.spotifyId)
    }

    @Test
    fun testLyricsSerialization() {
        val original = Lyrics(
            synced = listOf(
                LyricLine(timeSeconds = 12.5, text = "We're no strangers to love"),
                LyricLine(timeSeconds = 16.8, text = "You know the rules and so do I")
            ),
            plain = "We're no strangers to love\nYou know the rules and so do I",
            source = "LRCLIB"
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<Lyrics>(encoded)

        assertNotNull(decoded.synced)
        assertEquals(2, decoded.synced?.size)
        assertEquals("We're no strangers to love", decoded.synced?.first()?.text)
        assertEquals("LRCLIB", decoded.source)
    }

    @Test
    fun testUserSessionSerialization() {
        val session = UserSession(
            provider = SessionProvider.YOUTUBE,
            name = "Dezzy",
            handle = "@dezzydesign",
            avatarUrl = "https://example.com/avatar.png",
            connectedAtEpochMs = 1700000000000L,
            isValid = true
        )

        val encoded = json.encodeToString(session)
        val decoded = json.decodeFromString<UserSession>(encoded)

        assertEquals(SessionProvider.YOUTUBE, decoded.provider)
        assertEquals("Dezzy", decoded.name)
        assertEquals(true, decoded.isValid)
    }
}
