/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.scrobble

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.dezzy.app.core.model.Track
import com.dezzy.app.core.prefs.DezzyPreferences
import com.dezzy.app.feature.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.TimeSource

/** Interruptor general del scrobbling (activado por defecto). */
val ScrobbleEnabledKey = booleanPreferencesKey("scrobble_enabled")

/** Si se avisa a Last.fm de «lo que suena ahora» (activado por defecto). */
val ScrobbleLastFmNowPlayingKey = booleanPreferencesKey("scrobble_lastfm_now_playing")

/**
 * Manda a Last.fm y ListenBrainz lo que se escucha: «ahora suena» al empezar cada canción y un
 * scrobble cuando se ha escuchado de verdad la mitad (o 4 minutos) de una canción de más de 30 s.
 * El tiempo cuenta sólo con la música sonando, así que un salto de posición no suma.
 */
class Scrobbler(
    private val player: PlayerController,
    private val lastFm: LastFmClient,
    private val listenBrainz: ListenBrainzClient,
    private val prefs: DezzyPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    private val queueLock = Mutex()
    private val lastFmQueue = ArrayDeque<Pending>()
    private val listenBrainzQueue = ArrayDeque<Pending>()

    private class Pending(val track: ScrobbleTrack, val timestampSec: Long)

    /** Una reproducción concreta de una canción (si se repite, empieza otra). */
    private class Play(val key: String, val track: Track) {
        var startedAtSec = 0L
        var listenedMs = 0L
        var wasPlaying = false
        var nowPlayingSent = false
        var scrobbled = false
        var lastPositionMs = 0L
    }

    /** Idempotente. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            lastFm.load()
            listenBrainz.load()
        }
        scope.launch { watchPlayer() }
        scope.launch {
            while (true) {
                delay(RETRY_EVERY_MS)
                flushQueues()
            }
        }
    }

    private suspend fun watchPlayer() {
        val clock = TimeSource.Monotonic.markNow()
        var lastTickMs = 0L
        var play: Play? = null
        while (true) {
            delay(TICK_MS)
            val nowMs = clock.elapsedNow().inWholeMilliseconds
            val delta = nowMs - lastTickMs
            lastTickMs = nowMs

            val s = player.state.value
            val track = s.queue.current?.track
            if (track == null) {
                play = null
                continue
            }
            val durationSec = if (s.durationMs > 0) s.durationMs / 1000 else track.durationSeconds ?: 0L
            val key = track.videoId ?: "${track.title}|${track.artists}"
            val current = play
            val looped = current != null && current.key == key && durationSec > 0 &&
                s.positionMs < 5_000 && current.lastPositionMs > durationSec * 1000 * 8 / 10
            val p = if (current == null || current.key != key || looped) Play(key, track) else current
            play = p

            if (s.isPlaying) {
                if (p.startedAtSec == 0L) p.startedAtSec = Clock.System.now().toEpochMilliseconds() / 1000
                if (!p.nowPlayingSent) {
                    p.nowPlayingSent = true
                    val info = track.toScrobbleTrack(durationSec)
                    scope.launch { sendNowPlaying(info) }
                }
                if (p.wasPlaying && delta in 1..MAX_GAP_MS) p.listenedMs += delta
            }
            p.wasPlaying = s.isPlaying
            p.lastPositionMs = s.positionMs

            if (!p.scrobbled && durationSec > MIN_TRACK_SECONDS &&
                p.listenedMs >= min(durationSec * 500, MAX_LISTEN_MS)
            ) {
                p.scrobbled = true
                val info = track.toScrobbleTrack(durationSec)
                val at = p.startedAtSec
                scope.launch { deliver(info, at) }
            }
        }
    }

    private suspend fun enabled(): Boolean = prefs.value(ScrobbleEnabledKey, true).first()

    private suspend fun sendNowPlaying(info: ScrobbleTrack) {
        if (!enabled()) return
        if (prefs.value(ScrobbleLastFmNowPlayingKey, true).first()) lastFm.updateNowPlaying(info)
        listenBrainz.playingNow(info)
    }

    private suspend fun deliver(info: ScrobbleTrack, timestampSec: Long) {
        if (!enabled()) return
        val lastFmResult = lastFm.scrobble(info, timestampSec)
        val listenBrainzResult = listenBrainz.listen(info, timestampSec)
        queueLock.withLock {
            if (lastFmResult == SubmitResult.RETRY) enqueue(lastFmQueue, Pending(info, timestampSec))
            if (listenBrainzResult == SubmitResult.RETRY) enqueue(listenBrainzQueue, Pending(info, timestampSec))
        }
        // Si ha ido bien, aprovecha para vaciar lo que se quedó pendiente.
        if (lastFmResult == SubmitResult.OK || listenBrainzResult == SubmitResult.OK) flushQueues()
    }

    private fun enqueue(queue: ArrayDeque<Pending>, item: Pending) {
        queue.addLast(item)
        while (queue.size > MAX_QUEUE) queue.removeFirst()
    }

    private suspend fun flushQueues() {
        queueLock.withLock {
            drain(lastFmQueue) { lastFm.scrobble(it.track, it.timestampSec) }
            drain(listenBrainzQueue) { listenBrainz.listen(it.track, it.timestampSec) }
        }
    }

    /** Reenvía en orden; para al primer fallo pasajero y descarta lo que el servicio rechaza. */
    private suspend fun drain(queue: ArrayDeque<Pending>, send: suspend (Pending) -> SubmitResult) {
        while (queue.isNotEmpty()) {
            when (send(queue.first())) {
                SubmitResult.OK, SubmitResult.REJECTED -> queue.removeFirst()
                SubmitResult.RETRY -> return
            }
        }
    }

    private fun Track.toScrobbleTrack(durationSec: Long) = ScrobbleTrack(
        artist = firstArtist(artists),
        title = title,
        album = album,
        durationSec = durationSec,
    )

    private fun firstArtist(artists: String): String =
        artists.split(ARTIST_SEPARATORS).firstOrNull()?.trim().orEmpty().ifEmpty { artists }

    private companion object {
        const val TICK_MS = 1_000L
        const val MAX_GAP_MS = 3_000L
        const val MIN_TRACK_SECONDS = 30L
        const val MAX_LISTEN_MS = 4 * 60_000L
        const val MAX_QUEUE = 50
        const val RETRY_EVERY_MS = 5 * 60_000L
        val ARTIST_SEPARATORS = Regex(",| & | y | feat\\.? | ft\\.? ", RegexOption.IGNORE_CASE)
    }
}
