/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.together

import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.together.TogetherProtocol.bool
import com.dezzy.app.feature.together.TogetherProtocol.num
import com.dezzy.app.feature.together.TogetherProtocol.str
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.max
import kotlin.time.Clock

/**
 * «Escuchar juntos»: salas con código, anfitrión que manda la reproducción, aprobación de invitados,
 * cola y chat, contra el servidor propio de Dezzy. Equivale a `main/together.ts` del escritorio.
 *
 * Todo el estado vive en un único hilo lógico ([loop]); las llamadas públicas saltan a él.
 */
class TogetherClient(http: HttpClient) {

    private val client: HttpClient = http.config { install(WebSockets) }

    private val loop = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + loop)

    private val _state = MutableStateFlow<TogetherState>(TogetherState.Idle())
    val state: StateFlow<TogetherState> = _state.asStateFlow()

    private val _playback = MutableSharedFlow<RoomPlayback>(extraBufferCapacity = 64)

    /** Órdenes que llegan del anfitrión (también el estado inicial de la sala, como `sync`). */
    val playback: SharedFlow<RoomPlayback> = _playback.asSharedFlow()

    private class Session(val token: String, val me: String, val code: String)

    // --- estado (sólo se toca en [loop]) -----------------------------------
    private var outgoing: SendChannel<Frame>? = null
    private var conn: Job? = null
    private var pingJob: Job? = null
    private var retryJob: Job? = null
    private var generation = 0
    private var session: Session? = null
    private var username = DEFAULT_NAME
    private var retries = 0
    private var wantOpen = false
    private var pendingFirst: (() -> Unit)? = null

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private fun setState(next: TogetherState) {
        _state.value = next
    }

    private fun inRoom(): TogetherState.InRoom? = _state.value as? TogetherState.InRoom

    private fun updateRoom(patch: (RoomSnapshot) -> RoomSnapshot) {
        val s = inRoom() ?: return
        val room = patch(s.room)
        setState(s.copy(room = room, isHost = room.hostId == s.me))
    }

    private fun setSuggestions(next: (List<RoomSuggestion>) -> List<RoomSuggestion>) {
        val s = inRoom() ?: return
        setState(s.copy(suggestions = next(s.suggestions)))
    }

    // --- conexión ------------------------------------------------------------

    private fun send(type: String, payload: JsonObject = JsonObject(emptyMap())) {
        outgoing?.trySend(Frame.Text(TogetherProtocol.encode(type, payload)))
    }

    private fun clearTimers() {
        pingJob?.cancel()
        retryJob?.cancel()
        pingJob = null
        retryJob = null
    }

    private fun connect(onOpen: () -> Unit) {
        clearTimers()
        conn?.cancel()
        outgoing = null
        val mine = ++generation
        conn = scope.launch {
            var opened = false
            try {
                client.webSocket(
                    TogetherProtocol.SERVER,
                    request = {
                        timeout {
                            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                            socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                            connectTimeoutMillis = 10_000
                        }
                    },
                ) {
                    val ws = this
                    withContext(loop) {
                        if (generation != mine) return@withContext
                        this@TogetherClient.outgoing = ws.outgoing
                        opened = true
                        pingJob = scope.launch {
                            while (isActive) {
                                delay(TogetherProtocol.PING_MS)
                                send("ping", buildJsonObject { put("client_time", now()) })
                            }
                        }
                        onOpen()
                    }
                    for (frame in incoming) {
                        val text = when (frame) {
                            is Frame.Text -> frame.readText()
                            is Frame.Binary -> frame.readBytes().decodeToString()
                            else -> continue
                        }
                        withContext(loop) {
                            if (generation != mine) return@withContext
                            TogetherProtocol.decode(text)?.let { onMessage(it.type, it.payload) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Servidor inalcanzable o corte: se reintenta abajo.
            }
            withContext(NonCancellable + loop) { if (generation == mine) onClosed() }
        }
    }

    private fun onClosed() {
        clearTimers()
        outgoing = null
        conn = null
        if (!wantOpen) return
        if (retries++ > 6) {
            leaveInternal("No se pudo conectar con el servidor de «Escuchar juntos»")
            return
        }
        val wait = (1000L shl retries.coerceAtMost(10)).coerceAtMost(15_000L)
        retryJob = scope.launch {
            delay(wait)
            retryJob = null
            connect {
                val s = session
                if (s != null) send("reconnect", buildJsonObject { put("session_token", s.token) }) else pendingFirst?.invoke()
            }
        }
    }

    // --- mensajes del servidor -----------------------------------------------

    private fun enterRoom(me: String, token: String, roomRaw: JsonElement?, code: String) {
        session = Session(token, me, code)
        retries = 0
        val room = TogetherProtocol.toRoom(roomRaw, now()) ?: RoomSnapshot(
            code = code,
            hostId = me,
            users = listOf(RoomUser(me, username, isHost = true, connected = true)),
            track = null,
            playing = false,
            position = 0,
            at = now(),
            queue = emptyList(),
            settings = RoomSettings(),
        )
        val previous = inRoom()
        setState(
            TogetherState.InRoom(
                me = me,
                isHost = room.hostId == me,
                room = room,
                chat = previous?.chat ?: emptyList(),
                pending = previous?.pending ?: emptyList(),
                suggestions = previous?.suggestions ?: emptyList(),
                listeners = previous?.listeners ?: emptyMap(),
            ),
        )
        room.track?.let { emitPlayback(RoomPlayback("sync", it.id, room.position, it, room.queue)) }
    }

    private fun emitPlayback(playback: RoomPlayback) {
        _playback.tryEmit(playback)
    }

    internal fun onMessage(type: String, p: JsonObject) {
        when (type) {
            "room_created" -> enterRoom(p["user_id"].str(80), p["session_token"].str(500), null, p["room_code"].str(12))
            "join_request" -> {
                val s = inRoom()?.takeIf { it.isHost } ?: return
                val id = p["user_id"].str(80)
                if (id.isNotEmpty() && s.pending.none { it.first == id }) {
                    setState(s.copy(pending = s.pending + (id to p["username"].str(80).ifEmpty { "Invitado" })))
                }
            }
            "join_approved" -> enterRoom(p["user_id"].str(80), p["session_token"].str(500), p["state"], p["room_code"].str(12))
            "join_rejected" -> leaveInternal(p["reason"].str(200).ifEmpty { "El anfitrión no te ha dejado entrar" })
            "reconnected" -> session?.let { s ->
                enterRoom(s.me, s.token, p["state"], p["room_code"].str(12).ifEmpty { s.code })
            }
            "sync_state" -> onSyncState(p)
            "user_joined", "user_reconnected" -> {
                val id = p["user_id"].str(80)
                updateRoom { room ->
                    room.copy(
                        users = room.users.filter { it.id != id } +
                            RoomUser(id, p["username"].str(80).ifEmpty { "Invitado" }, isHost = id == room.hostId, connected = true),
                    )
                }
                inRoom()?.let { s -> setState(s.copy(pending = s.pending.filter { it.first != id })) }
            }
            "user_left" -> {
                val id = p["user_id"].str(80)
                updateRoom { room -> room.copy(users = room.users.filter { it.id != id }) }
                inRoom()?.let { s -> if (id in s.listeners) setState(s.copy(listeners = s.listeners - id)) }
            }
            "user_disconnected" -> {
                val id = p["user_id"].str(80)
                updateRoom { room -> room.copy(users = room.users.map { if (it.id == id) it.copy(connected = false) else it }) }
            }
            "host_changed" -> {
                val hostId = listOf("new_host_id", "host_id", "user_id").firstNotNullOfOrNull { p[it].str(80).ifEmpty { null } }
                if (hostId != null) {
                    updateRoom { room -> room.copy(hostId = hostId, users = room.users.map { it.copy(isHost = it.id == hostId) }) }
                }
            }
            "kicked" -> leaveInternal(p["reason"].str(200).ifEmpty { "El anfitrión te ha sacado de la sala" })
            "room_settings_updated" -> updateRoom { room -> room.copy(settings = TogetherProtocol.toSettings(p["settings"] ?: p)) }
            "sync_playback" -> applyPlayback(p)
            "suggestion_received" -> {
                val raw = JsonObject(p + mapOf("votes" to JsonPrimitive(0), "voters" to JsonArray(emptyList())))
                val item = TogetherProtocol.toSuggestion(raw)
                if (item != null) setSuggestions { list -> if (list.any { it.id == item.id }) list else list + item }
            }
            "suggestions_updated" -> setSuggestions { TogetherProtocol.toSuggestions(p["suggestions"]) }
            "room_dashboard" -> {
                if (p["suggestions"] is JsonArray) setSuggestions { TogetherProtocol.toSuggestions(p["suggestions"]) }
                // Informes de los oyentes (panel del anfitrión), si el servidor los manda aquí.
                val reports = p["listeners"] as? JsonArray
                val s = inRoom()
                if (reports != null && s != null) {
                    val t = now()
                    val map = reports.mapNotNull { TogetherProtocol.toListener(it, t) }.associateBy { it.userId }
                    setState(s.copy(listeners = map))
                }
            }
            "listener_report" -> {
                val s = inRoom() ?: return
                val info = TogetherProtocol.toListener(p, now()) ?: return
                setState(s.copy(listeners = s.listeners + (info.userId to info)))
            }
            "suggestion_approved", "suggestion_rejected" -> {
                val id = p["suggestion_id"].str(80)
                setSuggestions { list -> list.filter { it.id != id } }
            }
            "chat" -> {
                val s = inRoom() ?: return
                val msg = RoomChat(
                    id = p["message_id"].str(80).ifEmpty { now().toString() },
                    userId = p["user_id"].str(80),
                    name = p["username"].str(80).ifEmpty { "Invitado" },
                    text = p["message"].str(1000),
                    at = p["timestamp"].num().toLong().takeIf { it != 0L } ?: now(),
                )
                if (msg.text.isEmpty() || s.chat.any { it.id == msg.id }) return
                setState(s.copy(chat = (s.chat + msg).takeLast(TogetherProtocol.MAX_CHAT)))
            }
            "error" -> {
                val message = p["message"].str(300).ifEmpty { "El servidor de «Escuchar juntos» ha dado un error" }
                if (_state.value !is TogetherState.InRoom) leaveInternal(message)
            }
            else -> Unit
        }
    }

    /** SYNC_STATE no trae `room_code`: se aplica sobre la sala actual en vez de descartarlo. */
    private fun onSyncState(p: JsonObject) {
        if (inRoom() == null) return
        val raw = (p["state"] as? JsonObject) ?: p
        val t = now()
        val full = TogetherProtocol.toRoom(raw, t)
        val track = if (full != null) full.track else TogetherProtocol.toTrack(raw["current_track"])
        val queue: List<RoomTrack>? = when {
            full != null -> full.queue
            raw["queue"] is JsonArray -> TogetherProtocol.toTracks(raw["queue"])
            else -> null
        }
        val position = raw["position"].num().toLong()
        updateRoom { room ->
            full ?: room.copy(
                track = track ?: room.track,
                playing = raw["is_playing"].bool() == true,
                position = position,
                at = t,
                queue = queue ?: room.queue,
            )
        }
        if (track != null) emitPlayback(RoomPlayback("sync", track.id, position, track, queue))
    }

    /** Orden de reproducción del anfitrión: se guarda en la sala y se pasa al reproductor. */
    private fun applyPlayback(p: JsonObject) {
        val action = p["action"].str(40)
        val track = TogetherProtocol.toTrack(p["track_info"])
        val queue = (p["queue"] as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { TogetherProtocol.toTracks(it) }
        val position = p["position"].num().toLong()
        val trackId = p["track_id"].str(64)
        val t = now()
        updateRoom { room ->
            reduce(room, action, track, position, queue, p["insert_next"].bool() == true, trackId, t, remote = true)
        }
        emitPlayback(RoomPlayback(action, trackId.ifEmpty { null } ?: track?.id, position, track, queue))
    }

    private fun reduce(
        room: RoomSnapshot,
        action: String,
        track: RoomTrack?,
        position: Long,
        queue: List<RoomTrack>?,
        insertNext: Boolean,
        trackId: String,
        at: Long,
        remote: Boolean,
    ): RoomSnapshot = when (action) {
        "play" -> room.copy(playing = true, position = position, at = at)
        "pause" -> room.copy(playing = false, position = position, at = at)
        "seek" -> room.copy(position = position, at = at)
        "change_track" -> room.copy(track = track ?: room.track, playing = false, position = 0, at = at)
        "queue_add" -> when {
            track == null -> room
            insertNext -> room.copy(queue = listOf(track) + room.queue)
            else -> room.copy(queue = room.queue + track)
        }
        "queue_remove" -> room.copy(queue = room.queue.filter { it.id != trackId })
        "queue_clear" -> room.copy(queue = emptyList())
        "sync_queue" -> if (remote) room.copy(queue = queue ?: emptyList()) else room
        else -> room
    }

    // --- API pública -----------------------------------------------------------

    /** Salas públicas abiertas ahora. null si el servidor no responde. */
    suspend fun publicRooms(): List<PublicRoom>? = try {
        withTimeoutOrNull(10_000) {
            val body = client.get(TogetherProtocol.PUBLIC_ROOMS_URL).bodyAsText()
            parsePublicRooms(body)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }

    fun createRoom(name: String, isPublic: Boolean = false, theme: RoomTheme = RoomTheme.MIXED) {
        scope.launch {
            username = name.trim().take(40).ifEmpty { DEFAULT_NAME }
            wantOpen = true
            session = null
            retries = 0
            setState(TogetherState.Connecting)
            val first: () -> Unit = {
                send(
                    "create_room",
                    buildJsonObject {
                        put("username", username)
                        put("is_public", isPublic)
                        put("theme", theme.name)
                    },
                )
            }
            pendingFirst = first
            connect(first)
        }
    }

    /** Acepta «dezzy://together?code=XXXX» o el código suelto. */
    fun joinRoom(code: String, name: String) {
        scope.launch {
            val clean = TogetherProtocol.parseRoomCode(code)
            if (clean == null) {
                setState(TogetherState.Idle("Ese código no parece de una sala"))
                return@launch
            }
            username = name.trim().take(40).ifEmpty { DEFAULT_NAME }
            wantOpen = true
            session = null
            retries = 0
            setState(TogetherState.Waiting(clean))
            val first: () -> Unit = {
                send(
                    "join_room",
                    buildJsonObject {
                        put("room_code", clean)
                        put("username", username)
                    },
                )
            }
            pendingFirst = first
            connect(first)
        }
    }

    fun leave(error: String? = null) {
        scope.launch { leaveInternal(error) }
    }

    private fun leaveInternal(error: String?) {
        wantOpen = false
        if (session != null) send("leave_room", buildJsonObject { put("reason", "left") })
        session = null
        pendingFirst = null
        clearTimers()
        val old = conn
        conn = null
        outgoing = null
        generation++
        scope.launch {
            delay(200)
            old?.cancel()
        }
        setState(TogetherState.Idle(error))
    }

    fun approve(userId: String, ok: Boolean) {
        scope.launch {
            val s = inRoom()?.takeIf { it.isHost } ?: return@launch
            send(if (ok) "approve_join" else "reject_join", buildJsonObject { put("user_id", userId) })
            setState(s.copy(pending = s.pending.filter { it.first != userId }))
        }
    }

    fun kick(userId: String) {
        scope.launch { if (inRoom()?.isHost == true) send("kick_user", buildJsonObject { put("user_id", userId) }) }
    }

    fun transferHost(userId: String) {
        scope.launch { if (inRoom()?.isHost == true) send("transfer_host", buildJsonObject { put("new_host_id", userId) }) }
    }

    fun chat(text: String) {
        val clean = text.trim().take(1000)
        if (clean.isEmpty()) return
        scope.launch {
            if (inRoom() != null) send("chat", buildJsonObject { put("message", clean); put("type", "text") })
        }
    }

    /** Sólo el anfitrión. [transform] recibe los ajustes actuales y devuelve los nuevos. */
    fun updateSettings(transform: (RoomSettings) -> RoomSettings) {
        scope.launch {
            val s = inRoom()?.takeIf { it.isHost } ?: return@launch
            val next = transform(s.room.settings)
            send(
                "update_room_settings",
                buildJsonObject {
                    put(
                        "settings",
                        buildJsonObject {
                            put("allowGuestsToControlPlayback", next.allowGuestsToControlPlayback)
                            put("allowGuestsToAddTracks", next.allowGuestsToAddTracks)
                            put("requireHostApprovalToJoin", next.requireHostApprovalToJoin)
                            put("isPublic", next.isPublic)
                            put("djModeEnabled", next.djModeEnabled)
                            put("autoQueueVotes", next.autoQueueVotes)
                            put("theme", RoomTheme.MIXED.name)
                        },
                    )
                },
            )
            updateRoom { room -> room.copy(settings = next) }
        }
    }

    /**
     * Orden de reproducción (`play`, `pause`, `seek`, `change_track`, `queue_add`, `queue_remove`,
     * `queue_clear`, `sync_queue`). El anfitrión controla la sala; un invitado sólo puede añadir a
     * la cola (o controlar si el anfitrión lo permite). [positionMs] en milisegundos.
     */
    fun playback(
        action: String,
        track: Track? = null,
        positionMs: Long? = null,
        trackId: String? = null,
        insertNext: Boolean = false,
        queue: List<Track>? = null,
    ) {
        scope.launch {
            val s = inRoom() ?: return@launch
            val guestCanControl = s.room.settings.allowGuestsToControlPlayback
            val isQueue = action.startsWith("queue_")
            val guestCanAdd = s.room.settings.allowGuestsToAddTracks || s.room.settings.djModeEnabled
            if (!s.isHost && !(isQueue && guestCanAdd) && !guestCanControl) return@launch
            val info = track?.let { TogetherProtocol.trackInfo(it) }
            if ((action == "change_track" || action == "queue_add") && info == null) return@launch
            if (!s.isHost && action == "queue_add" && info != null) {
                send("suggest_track", buildJsonObject { put("track_info", info) })
                return@launch
            }
            val position = max(0L, positionMs ?: 0L)
            val payload = buildJsonObject {
                put("action", action)
                put("position", position)
                if (info != null) {
                    put("track_info", info)
                    put("track_id", info["id"] ?: JsonPrimitive(""))
                }
                if (!trackId.isNullOrEmpty()) put("track_id", trackId)
                if (insertNext) put("insert_next", true)
                put("queue", buildJsonArray { queue.orEmpty().mapNotNull { TogetherProtocol.trackInfo(it) }.forEach { add(it) } })
            }
            send("playback_action", payload)
            // El servidor no reenvía al que lo manda: la sala local se actualiza aquí.
            val t = now()
            val localTrack = TogetherProtocol.toTrack(info)
            val localId = payload["track_id"].str(64)
            updateRoom { room ->
                reduce(room, action, localTrack, position, null, insertNext, localId, t, remote = false)
            }
        }
    }

    /** Informe periódico para el panel del anfitrión: qué canción y en qué segundo va este oyente. */
    fun listenerReport(trackId: String, positionMs: Long, playing: Boolean, buffering: Boolean) {
        if (trackId.isEmpty()) return
        scope.launch {
            if (inRoom() == null) return@launch
            send(
                "listener_report",
                buildJsonObject {
                    put("track_id", trackId.take(64))
                    put("position", max(0L, positionMs))
                    put("is_playing", playing)
                    put("buffering", buffering)
                    put("network", "")
                },
            )
        }
    }

    /** El invitado avisa de que ya tiene la canción cargada. */
    fun bufferReady(trackId: String) {
        if (trackId.isEmpty()) return
        scope.launch {
            if (inRoom() != null) send("buffer_ready", buildJsonObject { put("track_id", trackId.take(64)) })
        }
    }

    /** Votar (o quitar el voto) a una propuesta; la lista buena llega con `suggestions_updated`. */
    fun voteSuggestion(id: String, vote: Boolean) {
        if (id.isEmpty()) return
        scope.launch {
            val s = inRoom() ?: return@launch
            send("vote_suggestion", buildJsonObject { put("suggestion_id", id); put("vote", vote) })
            setSuggestions { list ->
                list.map { x ->
                    if (x.id != id) return@map x
                    val has = s.me in x.voters
                    when {
                        vote && !has -> x.copy(votes = x.votes + 1, voters = x.voters + s.me)
                        !vote && has -> x.copy(votes = max(0, x.votes - 1), voters = x.voters.filter { it != s.me })
                        else -> x
                    }
                }
            }
        }
    }

    /** Anfitrión: la propuesta entra en la cola o se descarta. */
    fun decideSuggestion(id: String, ok: Boolean) {
        if (id.isEmpty()) return
        scope.launch {
            if (inRoom()?.isHost != true) return@launch
            send(if (ok) "approve_suggestion" else "reject_suggestion", buildJsonObject { put("suggestion_id", id) })
            setSuggestions { list -> list.filter { it.id != id } }
        }
    }

    /** Proponer una canción al anfitrión (si eres el anfitrión entra directa en la cola). */
    fun suggest(track: Track) = playback("queue_add", track = track)

    fun requestSync() {
        scope.launch { if (inRoom() != null) send("request_sync") }
    }

    /** El anfitrión pide el panel (room_dashboard) al abrirlo; el servidor no reenvía los listener_report. */
    fun requestDashboard() {
        scope.launch { if (inRoom()?.isHost == true) send("request_dashboard") }
    }

    /** El enlace que se comparte; la cámara o el chat lo abren y entran en la sala. */
    fun roomLink(code: String): String = "dezzy://together?code=$code"

    private companion object {
        const val DEFAULT_NAME = "Dezzy"
    }
}

/** Lista de salas públicas del servidor (cuerpo JSON de `/api/v1/public-rooms`). */
internal fun parsePublicRooms(body: String): List<PublicRoom>? {
    val root = runCatching { TogetherProtocol.json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
    fun JsonElement?.clean(): String? = str(200).trim().ifEmpty { null }
    val rooms = ArrayList<PublicRoom>()
    for (el in (root["rooms"] as? JsonArray).orEmpty()) {
        val r = el as? JsonObject ?: continue
        val code = r["code"].clean()?.uppercase() ?: continue
        val hostName = r["hostName"].clean() ?: "Anfitrión"
        if (!TogetherProtocol.ROOM_CODE_RE.matches(code) || r["isPublic"].bool() == false) continue
        // Las salas de prueba del servidor no se enseñan (igual que en el móvil).
        if (hostName.contains("test", ignoreCase = true)) continue
        val theme = RoomTheme.entries.firstOrNull { it.name == r["theme"].str() } ?: RoomTheme.MIXED
        val thumb = r["currentTrackThumbnail"].clean()
        val count = r["participantCount"]
        rooms += PublicRoom(
            code = code,
            hostName = hostName,
            theme = theme,
            participants = if (count is JsonPrimitive && !count.isString) max(0, count.num().toInt()) else 0,
            trackTitle = r["currentTrackTitle"].clean(),
            trackArtist = r["currentTrackArtist"].clean(),
            thumbnail = thumb?.takeIf { it.startsWith("https://") },
        )
    }
    return rooms.take(40)
}
