/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.together

import com.dezzy.app.core.prefs.DezzyPreferences
import com.dezzy.app.feature.player.PlaybackState
import com.dezzy.app.feature.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Clock

/**
 * Une [TogetherClient] con el [PlayerController] (equivale a `renderer/store/together.ts`):
 *  - el anfitrión publica a la sala sus cambios de canción, pausa, play y salto;
 *  - el invitado aplica las órdenes con reloj compartido, sin eco, de forma idempotente y con
 *    cortacircuitos contra tormentas de play/pausa;
 *  - cada 3 s informa de su posición real (`listener_report`) y, de invitado, corrige la deriva.
 *
 * Todo se ejecuta en el hilo principal (el reproductor sólo admite llamadas desde ahí).
 */
class TogetherSession(
    private val client: TogetherClient,
    private val player: PlayerController,
    private val prefs: DezzyPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Avisos para enseñar al usuario («Ana quiere entrar», errores de la sala…). */
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    /** Nombre visible guardado ("" si aún no se ha elegido). */
    suspend fun savedName(): String = prefs.value(TogetherNameKey, "").first()

    suspend fun saveName(name: String) = prefs.set(TogetherNameKey, name.trim().take(40))

    private class Pending(val videoId: String, val position: Long, val playing: Boolean, val at: Long)

    /** Cortacircuitos: abre si hay [MAX] cambios de play/pausa en [WINDOW_MS]. */
    private class Breaker {
        private val times = ArrayDeque<Long>()
        var openUntil = 0L

        fun isOpen(now: Long) = now < openUntil

        /** Anota un cambio; devuelve true si acaba de abrirse. */
        fun hit(now: Long): Boolean {
            times.addLast(now)
            while (times.isNotEmpty() && now - times.first() > WINDOW_MS) times.removeFirst()
            if (times.size >= MAX) {
                times.clear()
                openUntil = now + OPEN_MS
                return true
            }
            return false
        }

        companion object {
            const val MAX = 6
            const val WINDOW_MS = 3_000L
            const val OPEN_MS = 5_000L
        }
    }

    private var pending: Pending? = null
    private var expectedRemoteId: String? = null
    private var awaitPlayId: String? = null
    private var remoteUntil = 0L
    private var lastFix = 0L
    private val inBreaker = Breaker()
    private val outBreaker = Breaker()

    // Línea base del reproductor para detectar lo que hace el usuario.
    private var lastId: String? = null
    private var lastPlaying = false
    private var lastPos = 0L
    private var lastAt = 0L

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private fun inRoom(): TogetherState.InRoom? = client.state.value as? TogetherState.InRoom

    private fun currentId(s: PlaybackState): String? = s.queue.current?.track?.videoId

    private fun markRemote() {
        remoteUntil = now() + REMOTE_WINDOW_MS
    }

    /** Idempotente: arrancar varias veces no duplica nada. */
    fun start() {
        if (started) return
        started = true

        scope.launch {
            var previous: TogetherState? = null
            client.state.collect { next ->
                onClientState(previous, next)
                previous = next
            }
        }
        scope.launch {
            client.playback.collect { pb -> applyPlayback(pb, inRoom()?.room?.playing ?: true) }
        }
        scope.launch {
            player.state.collect { onPlayer(it) }
        }
        scope.launch {
            // Cada 3 s, como el móvil: el panel del anfitrión ve la posición real de cada oyente.
            while (true) {
                delay(REPORT_MS)
                tick()
            }
        }
    }

    // --- estado de la sala ------------------------------------------------------

    private fun onClientState(prev: TogetherState?, next: TogetherState) {
        val before = prev as? TogetherState.InRoom
        val after = next as? TogetherState.InRoom
        if (after == null) {
            pending = null
            expectedRemoteId = null
            awaitPlayId = null
            if (next is TogetherState.Idle && next.error != null && prev !is TogetherState.Idle) _toasts.tryEmit(next.error)
            return
        }
        // Al crear la sala, al pasar a ser anfitrión o cuando entra alguien.
        if (after.isHost && (before == null || !before.isHost || after.room.users.size > before.room.users.size)) {
            publishHostTrack(after.room.track?.id)
        }
        if (before != null) {
            if (after.isHost && !before.isHost && before.me == after.me) _toasts.tryEmit("Ahora eres el anfitrión de la sala")
            after.pending.filter { p -> before.pending.none { it.first == p.first } }.forEach {
                _toasts.tryEmit("${it.second} quiere entrar en la sala")
            }
            after.room.users.filter { u -> u.id != after.me && before.room.users.none { it.id == u.id } }.forEach {
                _toasts.tryEmit("${it.name} se ha unido")
            }
            before.room.users.filter { u -> u.id != after.me && after.room.users.none { it.id == u.id } }.forEach {
                _toasts.tryEmit("${it.name} ha salido")
            }
        }
    }

    /**
     * Anfitrión: si la sala no tiene su canción (acaba de crearla sonando algo, o entra alguien y el
     * servidor no la sabe), la publica con su posición para que los invitados la sigan.
     */
    private fun publishHostTrack(roomTrackId: String?) {
        val s = player.state.value
        val track = s.queue.current?.track ?: return
        val id = track.videoId ?: return
        if (roomTrackId == id) return
        client.playback("change_track", track = track, queue = nextTracks(s))
        val playing = s.isPlaying || s.isBuffering
        client.playback(if (playing) "play" else "pause", positionMs = s.positionMs)
        awaitPlayId = null
        lastId = id
    }

    private fun nextTracks(s: PlaybackState) =
        s.queue.items.drop(s.queue.index + 1).take(MAX_QUEUE).map { it.track }

    // --- órdenes de la sala hacia el reproductor ---------------------------------

    private fun loaded(s: PlaybackState) = s.durationMs > 0 && !s.isBuffering

    private fun seekIfFar(s: PlaybackState, positionMs: Long, toleranceMs: Long) {
        if (abs(s.positionMs - positionMs) > toleranceMs) player.seekTo(positionMs.coerceAtLeast(0))
    }

    private fun setPlaying(s: PlaybackState, playing: Boolean) {
        if (playing == s.isPlaying) return
        if (inBreaker.isOpen(now())) return
        if (inBreaker.hit(now())) {
            // Tormenta de play/pausa: se deja de obedecer unos segundos y se pide el estado limpio.
            _toasts.tryEmit("Muchos cambios seguidos, resincronizando…")
            client.requestSync()
            return
        }
        if (playing) player.play() else player.pause()
    }

    /** Orden que llega de la sala: el reproductor la sigue sin reenviarla. */
    private fun applyPlayback(pb: RoomPlayback, playingNow: Boolean) {
        val s = player.state.value
        val track = pb.track
        markRemote()
        if ((pb.action == "change_track" || pb.action == "sync") && track != null) {
            val playing = if (pb.action == "change_track") true else playingNow
            if (currentId(s) == track.id) {
                // Misma canción pero sin cargar: al estar lista se coloca con el tiempo que haya pasado.
                if (playing && !loaded(s)) pending = Pending(track.id, pb.position, true, now())
                seekIfFar(s, pb.position, SEEK_TOLERANCE_MS)
                setPlaying(s, playing)
                return
            }
            pending = Pending(track.id, pb.position, playing, now())
            expectedRemoteId = track.id
            val queue = (listOf(track) + (pb.queue ?: emptyList())).map { TogetherProtocol.roomTrackToTrack(it) }
            player.playTracks(queue, 0, "together")
            return
        }
        when (pb.action) {
            "play" -> {
                seekIfFar(s, pb.position, SEEK_TOLERANCE_MS)
                setPlaying(s, true)
            }
            "pause" -> {
                setPlaying(s, false)
                seekIfFar(s, pb.position, SEEK_TOLERANCE_MS)
            }
            "seek" -> player.seekTo(pb.position.coerceAtLeast(0))
        }
    }

    // --- reproductor hacia la sala ------------------------------------------------

    private fun canControl(room: TogetherState.InRoom) = room.isHost || room.room.settings.allowGuestsToControlPlayback

    private fun onPlayer(s: PlaybackState) {
        val t = now()
        val id = currentId(s)
        val room = inRoom()
        try {
            if (room == null) return

            pending?.let { p -> if (t - p.at > PENDING_TTL_MS) pending = null }

            // Canción de la sala cargada: avisa y se coloca donde va la sala.
            val p = pending
            if (p != null && p.videoId == id && loaded(s)) {
                pending = null
                markRemote()
                client.bufferReady(p.videoId)
                val elapsed = if (p.playing) t - p.at else 0L
                player.seekTo((p.position + elapsed).coerceAtLeast(0))
                setPlaying(s, p.playing)
                return
            }

            val remote = t < remoteUntil || pending != null
            if (remote) return

            // Anfitrión: la canción que él puso ya suena (el servidor la tiene en pausa tras change_track).
            if (room.isHost && awaitPlayId != null && awaitPlayId == id && loaded(s) && s.isPlaying) {
                awaitPlayId = null
                client.playback("play", positionMs = s.positionMs, trackId = id)
                return
            }

            if (id == null) return
            if (id != lastId) {
                if (expectedRemoteId == id) {
                    expectedRemoteId = null
                } else if (canControl(room)) {
                    val track = s.queue.current?.track ?: return
                    client.playback("change_track", track = track, queue = nextTracks(s))
                    if (room.isHost) awaitPlayId = id
                }
                return
            }

            if (!canControl(room)) return
            if (s.isPlaying != lastPlaying) {
                if (outBreaker.isOpen(t)) return
                if (outBreaker.hit(t)) {
                    _toasts.tryEmit("Muchos cambios seguidos, se pausa el envío un momento")
                    return
                }
                client.playback(if (s.isPlaying) "play" else "pause", positionMs = s.positionMs)
            } else if (loaded(s)) {
                // Salto del usuario: la posición se aleja de la que tocaba por el reloj.
                val expected = lastPos + if (lastPlaying) t - lastAt else 0L
                if (abs(s.positionMs - expected) > SEEK_JUMP_MS) client.playback("seek", positionMs = s.positionMs)
            }
        } finally {
            lastId = id
            lastPlaying = s.isPlaying
            lastPos = s.positionMs
            lastAt = t
        }
    }

    private fun tick() {
        val room = inRoom() ?: return
        val s = player.state.value
        val id = currentId(s) ?: return
        client.listenerReport(id, s.positionMs, s.isPlaying, s.isBuffering)
        // Invitado: el primer salto a mitad de canción llega ~1 s tarde (descarga del tramo);
        // si se aleja más de 0,9 s de la sala, se recoloca (como mucho cada 10 s).
        val t = now()
        if (!room.isHost && room.room.playing && room.room.track?.id == id && s.isPlaying && pending == null && t - lastFix > FIX_EVERY_MS) {
            val expected = room.room.position + (t - room.room.at)
            if (abs(expected - s.positionMs) > DRIFT_MS) {
                lastFix = t
                markRemote()
                player.seekTo(expected.coerceAtLeast(0))
            }
        }
    }

    private companion object {
        const val REPORT_MS = 3_000L
        const val REMOTE_WINDOW_MS = 1_500L
        const val PENDING_TTL_MS = 30_000L
        const val SEEK_TOLERANCE_MS = 400L
        const val SEEK_JUMP_MS = 1_500L
        const val DRIFT_MS = 900L
        const val FIX_EVERY_MS = 10_000L
        const val MAX_QUEUE = 50
    }
}
