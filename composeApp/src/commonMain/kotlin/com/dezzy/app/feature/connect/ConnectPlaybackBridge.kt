/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.connect

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dezzy.app.core.model.Track
import com.dezzy.app.core.prefs.DezzyPreferences
import com.dezzy.app.feature.player.PlaybackState
import com.dezzy.app.feature.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Clock

private val ControlAllKey = booleanPreferencesKey("connect_control_all")
private val OffsetsKey = stringPreferencesKey("connect_offsets")

/**
 * Une Dezzy Connect con el reproductor. Es el equivalente de `store/connect.ts` de Dezzy Desktop:
 * decide quién es el «centro» (quien reparte la reproducción), obedece las órdenes de los demás y
 * mantiene sincronizados a los que escuchan juntos.
 *
 * El reproductor no avisa de lo que hace el usuario, así que sus gestos (pausa, salto, cambio de
 * canción) se detectan comparando estados; las llamadas propias se ignoran durante unos instantes.
 * Todo corre en el hilo principal, como pide [PlayerController].
 */
class ConnectPlaybackBridge(
    private val service: ConnectService,
    private val player: PlayerController,
    private val prefs: DezzyPreferences,
) {
    /**
     * NONE: sin sesión. REMOTE: controlo otro dispositivo al que me uní. LEADER: mandé la música a otros y
     * aquí no suena. HOST: suena aquí y otros la controlan o la siguen. FOLLOWER: suena aquí igual que en el centro.
     */
    enum class Role { NONE, REMOTE, HOST, LEADER, FOLLOWER }

    /** Un dispositivo de la sesión. [host]: reproduce audio (si no, sólo controla). */
    data class Member(
        val id: String,
        val name: String,
        val kind: DeviceKind,
        val host: Boolean,
        val drift: Double? = null,
        val volume: Double? = null,
        val offset: Double = 0.0,
    )

    /** El dispositivo al que estoy unido (centro de la sesión). */
    data class Hub(val id: String, val name: String, val kind: DeviceKind)

    private companion object {
        const val SOURCE = "connect:Dezzy Connect"
        const val QUEUE_BEFORE = 20
        const val QUEUE_AFTER = 200
        const val MAX_QUEUE = 400
        const val APPLY_MS = 900L
        const val PENDING_MS = 20_000L
        const val JOIN_WAIT_MS = 8_000L
        const val HOLD_MS = 1_500L
        const val NEAR_END_MS = 20_000L
        const val JUMP_MS = 1_500L
        const val DRIFT_FIX_MS = 250.0
        val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
    }

    private class Pending(val id: String, val pb: ConnectPlayback, val deadline: Long, val offsetMs: Double)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false

    private val _role = MutableStateFlow(Role.NONE)
    val role: StateFlow<Role> = _role.asStateFlow()
    private val _members = MutableStateFlow<List<Member>>(emptyList())
    val members: StateFlow<List<Member>> = _members.asStateFlow()
    private val _hub = MutableStateFlow<Hub?>(null)
    val hub: StateFlow<Hub?> = _hub.asStateFlow()
    private val _controlAll = MutableStateFlow(true)

    /** true: cualquiera de la sesión puede controlar; false: sólo quien la empezó. */
    val controlAll: StateFlow<Boolean> = _controlAll.asStateFlow()
    private val _offset = MutableStateFlow(0.0)

    /** Mi ajuste manual de sincronía con el centro, en ms. */
    val offset: StateFlow<Double> = _offset.asStateFlow()
    private val _drift = MutableStateFlow<Double?>(null)

    /** Diferencia medida con el centro, en ms (cuando soy seguidor). */
    val drift: StateFlow<Double?> = _drift.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** Lo que suena en el otro dispositivo (soy REMOTE o LEADER). */
    private val _remote = MutableStateFlow<ConnectPlayback?>(null)
    val remote: StateFlow<ConnectPlayback?> = _remote.asStateFlow()
    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Avisos breves («Ana cambió de canción»). Quien cablea la app los muestra como toast. */
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    private val offsets = HashMap<String, Double>()
    private var owner: String? = null
    private var sentFrom = 0
    private var applyingUntil = 0L
    private var pending: Pending? = null
    private var expectingHandoff: String? = null
    private var joinWait: Job? = null
    private var knownPeers = emptySet<String>()

    private var prevInit = false
    private var prevPlaying = false
    private var prevPos = 0L
    private var prevDur = 0L
    private var prevAt = 0L
    private var prevId: String? = null
    private var publishedKey = ""

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    /** Arranca el servicio y empieza a escuchar. Repetirlo no hace nada. */
    fun start() {
        if (started) return
        started = true
        service.start()
        scope.launch {
            _controlAll.value = prefs.value(ControlAllKey, true).first()
            runCatching {
                ConnectJson.json.decodeFromString(MapSerializer(String.serializer(), Double.serializer()), prefs.value(OffsetsKey, "{}").first())
            }.getOrNull()?.let { offsets.putAll(it) }
        }
        scope.launch { service.events.collect { onEvent(it) } }
        scope.launch { player.state.collect { onLocal(it) } }
        scope.launch {
            var tick = 0
            while (isActive) {
                delay(1_000)
                tick++
                val t = nowMs()
                pending?.let { if (t > it.deadline) pending = null }
                if (_role.value == Role.HOST && player.state.value.isPlaying && tick % 5 == 0) broadcastState()
            }
        }
    }

    /* ── Acciones de la hoja ── */

    fun search() = service.search()

    fun clearNotice() {
        _notice.value = null
    }

    /** Hay algo en la cola de este dispositivo que se pueda compartir. */
    fun hasLivePlayback(): Boolean = player.state.value.queue.current != null

    /** Suena aquí la música de otro (seguidor) o la controlo desde aquí (remoto). */
    fun describe(): String? {
        val hubName = _hub.value?.name
        val names = _members.value.joinToString(", ") { it.name }
        return when (_role.value) {
            Role.NONE -> null
            Role.REMOTE -> hubName?.let { "Controlando $it" }
            Role.FOLLOWER -> hubName?.let { "Escuchando con $it" }
            Role.HOST -> if (names.isEmpty()) null else "Suena aquí y en $names"
            Role.LEADER -> if (names.isEmpty()) null else "Suena en $names"
        }
    }

    fun setControlAll(all: Boolean) {
        _controlAll.value = all
        scope.launch { prefs.set(ControlAllKey, all) }
        if (_role.value == Role.HOST || _role.value == Role.LEADER) {
            val ctl = ctl()
            localPb()?.let { service.send(ConnectMessage.State(it, ctl)) }
        }
    }

    /** Pasa la música de este dispositivo a [id]: allí suena sola y aquí se calla. */
    fun moveTo(id: String) {
        scope.launch {
            if (!ensure(id)) return@launch
            val pb = localPb() ?: return@launch run { _notice.value = "No hay nada sonando" }
            prepareToShare()
            service.send(ConnectMessage.Take(ConnectMode.there, pb, ctl(), offsets[id]), to = id)
            addMember(memberOf(id, host = true))
            _hub.value = null
            _role.value = Role.LEADER
            _remote.value = pb
            applyingUntil = nowMs() + APPLY_MS
            player.pause()
        }
    }

    /** Suena aquí y también en [id]. */
    fun alsoHere(id: String) {
        scope.launch {
            if (!ensure(id)) return@launch
            val pb = localPb() ?: return@launch run { _notice.value = "No hay nada sonando" }
            prepareToShare()
            service.send(ConnectMessage.Take(ConnectMode.both, pb, ctl(), offsets[id]), to = id)
            addMember(memberOf(id, host = true))
            _hub.value = null
            _remote.value = null
            _role.value = Role.HOST
        }
    }

    /** Controla lo que ya suena en [id] sin que suene aquí. */
    fun joinRemote(id: String) {
        scope.launch {
            if (!ensure(id)) return@launch
            clearSession(stopOthers = true)
            service.send(ConnectMessage.Join(ConnectJoinMode.remote), to = id)
            _hub.value = hubOf(id)
            _role.value = Role.REMOTE
            applyingUntil = nowMs() + APPLY_MS
            player.pause()
            waitForJoin(id)
        }
    }

    /** Se trae aquí lo que suena en [id] (cola y posición) y allí se calla. */
    fun pullFrom(id: String) {
        scope.launch {
            if (!ensure(id)) return@launch
            expectingHandoff = id
            service.send(ConnectMessage.Join(ConnectJoinMode.pull), to = id)
            _busy.value = true
            delay(JOIN_WAIT_MS)
            if (expectingHandoff == id) {
                expectingHandoff = null
                _notice.value = "No hay nada sonando en ${nameOf(id)}"
            }
            _busy.value = false
        }
    }

    /** «Reproducir aquí»: trae a este dispositivo lo que suena en el otro. */
    fun bringHere() {
        val id = _hub.value?.id ?: _members.value.firstOrNull { it.host }?.id ?: return
        pullFrom(id)
    }

    /** Escucha lo mismo que [id], sincronizado. */
    fun followDevice(id: String) {
        scope.launch {
            if (!ensure(id)) return@launch
            clearSession(stopOthers = true)
            service.send(ConnectMessage.Join(ConnectJoinMode.follow), to = id)
            _hub.value = hubOf(id)
            _role.value = Role.FOLLOWER
            waitForJoin(id)
        }
    }

    /** Deja de reproducir aquí y avisa a los demás. */
    fun stopHere() {
        when (_role.value) {
            Role.HOST, Role.LEADER -> {
                service.send(ConnectMessage.Stop)
                applyingUntil = nowMs() + APPLY_MS
                if (_role.value == Role.HOST) player.pause()
            }
            Role.FOLLOWER, Role.REMOTE -> {
                _hub.value?.let { service.send(ConnectMessage.Stop, to = it.id) }
                if (_role.value == Role.FOLLOWER) {
                    applyingUntil = nowMs() + APPLY_MS
                    player.pause()
                }
            }
            Role.NONE -> Unit
        }
        clearRole()
    }

    /** Deja de controlar/seguir sin pausar lo de aquí. */
    fun release() {
        _hub.value?.let { service.send(ConnectMessage.Stop, to = it.id) }
        clearRole()
    }

    /** Saca a [id] de la sesión y cierra la conexión con él. */
    fun dropPeer(id: String) {
        service.send(ConnectMessage.Stop, to = id)
        removeMember(id)
        scope.launch {
            delay(300)
            service.close(id)
        }
    }

    fun forget(id: String) {
        removeMember(id)
        service.forget(id)
    }

    /** Ajuste de sincronía en ms. Sin [id], el mío con el centro; con [id], el de ese miembro. */
    fun setOffset(ms: Double, id: String? = null) {
        if (id == null) {
            _offset.value = ms
            _hub.value?.let { offsets[it.id] = ms }
        } else {
            offsets[id] = ms
            _members.value = _members.value.map { if (it.id == id) it.copy(offset = ms) else it }
            service.send(ConnectMessage.Offset(ms), to = id)
        }
        saveOffsets()
    }

    fun setMemberVolume(id: String, volume: Double) {
        val v = volume.coerceIn(0.0, 1.0)
        _members.value = _members.value.map { if (it.id == id) it.copy(volume = v) else it }
        service.send(ConnectMessage.Cmd(ConnectCommand.Vol(v)), to = id)
    }

    fun remoteToggle() {
        _remote.value = _remote.value?.let { it.copy(pos = expectedPos(it), at = nowMs().toDouble(), playing = !it.playing) }
        sendCmd(ConnectCommand.Toggle)
    }

    fun remoteNext() = sendCmd(ConnectCommand.Next)
    fun remotePrev() = sendCmd(ConnectCommand.Prev)

    fun remoteSeek(posSec: Double) {
        _remote.value = _remote.value?.let { it.copy(pos = posSec, at = nowMs().toDouble()) }
        sendCmd(ConnectCommand.Seek(posSec))
    }

    fun remoteVolume(v: Double) {
        val c = v.coerceIn(0.0, 1.0)
        _remote.value = _remote.value?.copy(vol = c)
        sendCmd(ConnectCommand.Vol(c))
    }

    /* ── Sesión ── */

    private suspend fun ensure(id: String): Boolean {
        if (service.state.value.peers.any { it.id == id }) return true
        _busy.value = true
        try {
            val r = service.open(id)
            r.onFailure { _notice.value = it.message ?: "No se pudo conectar" }
            return r.isSuccess
        } finally {
            _busy.value = false
        }
    }

    private fun ctl() = if (_controlAll.value) ControlScope.all else ControlScope.one

    private fun nameOf(id: String): String {
        val s = service.state.value
        return s.peers.firstOrNull { it.id == id }?.name ?: s.devices.firstOrNull { it.id == id }?.name ?: "otro dispositivo"
    }

    private fun kindOf(id: String): DeviceKind {
        val s = service.state.value
        return s.peers.firstOrNull { it.id == id }?.kind ?: s.devices.firstOrNull { it.id == id }?.kind ?: DeviceKind.pc
    }

    private fun hubOf(id: String) = Hub(id, nameOf(id), kindOf(id))
    private fun memberOf(id: String, host: Boolean) = Member(id, nameOf(id), kindOf(id), host, offset = offsets[id] ?: 0.0)

    private fun addMember(m: Member) {
        _members.value = _members.value.filter { it.id != m.id } + m
    }

    private fun removeMember(id: String) {
        _members.value = _members.value.filter { it.id != id }
        if (_members.value.isEmpty() && (_role.value == Role.HOST || _role.value == Role.LEADER)) clearRole()
    }

    private fun clearRole() {
        joinWait?.cancel()
        _role.value = Role.NONE
        _hub.value = null
        _members.value = emptyList()
        _remote.value = null
        _drift.value = null
        owner = null
        pending = null
    }

    /** Antes de empezar una sesión nueva repartiendo: si había otra, se cierra (menos con quien seguimos). */
    private fun prepareToShare() {
        if (_role.value == Role.REMOTE || _role.value == Role.FOLLOWER) {
            _hub.value?.let { service.send(ConnectMessage.Stop, to = it.id) }
            clearRole()
        }
    }

    private fun clearSession(stopOthers: Boolean) {
        if (_role.value == Role.NONE) return
        if (stopOthers) {
            val ids = _members.value.map { it.id } + listOfNotNull(_hub.value?.id)
            for (id in ids) service.send(ConnectMessage.Stop, to = id)
        }
        clearRole()
    }

    private fun waitForJoin(id: String) {
        joinWait?.cancel()
        joinWait = scope.launch {
            delay(JOIN_WAIT_MS)
            val got = when (_role.value) {
                Role.REMOTE -> _remote.value != null
                Role.FOLLOWER -> pending != null || _drift.value != null || player.state.value.queue.current != null && prevPlaying
                else -> true
            }
            if (!got && _hub.value?.id == id) {
                _notice.value = "No hay nada sonando en ${nameOf(id)}"
                clearRole()
            }
        }
    }

    private fun onPeerLost(id: String) {
        when {
            _hub.value?.id == id -> {
                val role = _role.value
                _notice.value = "Se perdió la conexión con ${_hub.value?.name ?: nameOf(id)}"
                _toasts.tryEmit("Se perdió la conexión con ${_hub.value?.name ?: nameOf(id)}")
                clearRole()
                if (role == Role.REMOTE) player.pause()
            }
            _members.value.any { it.id == id } -> removeMember(id)
        }
        if (expectingHandoff == id) expectingHandoff = null
    }

    /* ── Mensajes ── */

    private fun onEvent(ev: ConnectEvent) {
        when (ev) {
            is ConnectEvent.StateChanged -> {
                val now = ev.state.peers.mapTo(HashSet()) { it.id }
                for (id in knownPeers - now) onPeerLost(id)
                knownPeers = now
            }
            is ConnectEvent.Message -> onMessage(ev.from, ev.name, ev.message)
        }
    }

    private fun onMessage(from: String, name: String, m: ConnectMessage) {
        when (m) {
            is ConnectMessage.Take -> onTake(from, m)
            is ConnectMessage.State -> onState(from, m)
            is ConnectMessage.Cmd -> onCmd(from, name, m.cmd)
            is ConnectMessage.Join -> onJoin(from, m.mode)
            is ConnectMessage.Handoff -> {
                if (expectingHandoff != from || !sane(m.pb)) return
                expectingHandoff = null
                clearSession(stopOthers = true)
                playFrom(m.pb, 0.0)
            }
            ConnectMessage.Stop -> onStop(from)
            is ConnectMessage.Drift -> _members.value = _members.value.map {
                if (it.id == from) it.copy(drift = m.ms, volume = m.vol) else it
            }
            is ConnectMessage.Offset -> {
                if (_hub.value?.id != from) return
                _offset.value = m.ms
                offsets[from] = m.ms
                saveOffsets()
            }
            is ConnectMessage.Ctrl -> _toasts.tryEmit("${m.name} ${opText(m.op)}")
        }
    }

    private fun onTake(from: String, m: ConnectMessage.Take) {
        if (!sane(m.pb)) return
        // Una sesión nueva sustituye a la vieja; a quien venga de otro sitio se le avisa.
        if (_role.value != Role.NONE) {
            val ids = (_members.value.map { it.id } + listOfNotNull(_hub.value?.id)).filter { it != from }
            for (id in ids) service.send(ConnectMessage.Stop, to = id)
            clearRole()
        }
        m.ctl?.let { _controlAll.value = it == ControlScope.all }
        when (m.mode) {
            ConnectMode.there -> {
                _role.value = Role.HOST
                owner = from
                addMember(memberOf(from, host = false))
            }
            ConnectMode.both -> {
                _role.value = Role.FOLLOWER
                _hub.value = hubOf(from)
                m.offset?.let {
                    _offset.value = it
                    offsets[from] = it
                    saveOffsets()
                }
            }
        }
        playFrom(m.pb, if (m.mode == ConnectMode.both) _offset.value else 0.0)
        if (m.mode == ConnectMode.there) {
            scope.launch {
                delay(HOLD_MS)
                broadcastState()
            }
        }
    }

    private fun onState(from: String, m: ConnectMessage.State) {
        if (!sane(m.pb)) return
        m.ctl?.let { _controlAll.value = it == ControlScope.all }
        when (_role.value) {
            Role.REMOTE -> if (_hub.value?.id == from) _remote.value = m.pb
            Role.LEADER -> if (_members.value.any { it.id == from }) _remote.value = m.pb
            Role.FOLLOWER -> if (_hub.value?.id == from) follow(m.pb)
            else -> Unit
        }
    }

    private fun onCmd(from: String, name: String, cmd: ConnectCommand) {
        if (_role.value != Role.HOST) return
        if (_members.value.none { it.id == from }) return
        if (!_controlAll.value && from != owner) return
        obey(cmd)
        val op = cmd.wireOp()
        if (op != "sync") {
            service.send(ConnectMessage.Ctrl(from, name, op), except = from)
            _toasts.tryEmit("$name ${opText(op)}")
        }
        // Se confirma enseguida y otra vez cuando el reproductor ya se ha asentado.
        broadcastState()
        scope.launch {
            delay(300)
            broadcastState()
        }
    }

    private fun onJoin(from: String, mode: ConnectJoinMode) {
        val role = _role.value
        if (role == Role.REMOTE || role == Role.FOLLOWER || role == Role.LEADER) return
        val pb = localPb() ?: return
        when (mode) {
            ConnectJoinMode.remote -> {
                _role.value = Role.HOST
                addMember(memberOf(from, host = false))
                service.send(ConnectMessage.State(pb, ctl()), to = from)
            }
            ConnectJoinMode.follow -> {
                _role.value = Role.HOST
                addMember(memberOf(from, host = true))
                service.send(ConnectMessage.Take(ConnectMode.both, pb, ctl(), offsets[from]), to = from)
            }
            ConnectJoinMode.pull -> {
                service.send(ConnectMessage.Handoff(pb), to = from)
                service.send(ConnectMessage.Stop, except = from)
                applyingUntil = nowMs() + APPLY_MS
                player.pause()
                clearRole()
            }
        }
    }

    private fun onStop(from: String) {
        when {
            _hub.value?.id == from -> {
                val role = _role.value
                clearRole()
                if (role == Role.FOLLOWER) {
                    applyingUntil = nowMs() + APPLY_MS
                    player.pause()
                }
            }
            _members.value.any { it.id == from } -> removeMember(from)
        }
    }

    private fun opText(op: String) = when (op) {
        "next", "prev", "playAt", "playTrack", "load", "radio" -> "cambió de canción"
        "toggle" -> "pausó o reanudó la música"
        "play" -> "reanudó la música"
        "pause" -> "pausó la música"
        "seek" -> "movió la canción"
        "vol" -> "cambió el volumen"
        else -> "tocó la música"
    }

    /* ── Órdenes ── */

    private fun sendCmd(cmd: ConnectCommand) {
        val msg = ConnectMessage.Cmd(cmd)
        when (_role.value) {
            Role.HOST -> {
                obey(cmd)
                broadcastState()
            }
            Role.REMOTE, Role.FOLLOWER -> _hub.value?.let { service.send(msg, to = it.id) }
            Role.LEADER -> for (m in _members.value) service.send(msg, to = m.id)
            Role.NONE -> Unit
        }
    }

    /** Hace aquí lo que pide otro dispositivo. */
    private fun obey(cmd: ConnectCommand) {
        applyingUntil = nowMs() + APPLY_MS
        when (cmd) {
            ConnectCommand.Toggle -> player.togglePlay()
            ConnectCommand.Next -> player.next()
            ConnectCommand.Prev -> player.previous()
            ConnectCommand.Play -> player.play()
            ConnectCommand.Pause -> player.pause()
            is ConnectCommand.Seek -> player.seekTo(max(0.0, cmd.pos * 1000).toLong())
            is ConnectCommand.Vol -> player.setVolume(cmd.v.coerceIn(0.0, 1.0).toFloat())
            is ConnectCommand.PlayAt -> {
                val i = sentFrom + cmd.index
                if (i in player.state.value.queue.items.indices) player.playAt(i)
            }
            is ConnectCommand.PlayTrack -> {
                val i = player.state.value.queue.items.indexOfFirst { it.track.videoId == cmd.id }
                if (i >= 0 && i != player.state.value.queue.index) player.playAt(i)
            }
            ConnectCommand.Sync -> Unit
            is ConnectCommand.Load -> loadQueue(cmd.queue, cmd.index)?.let { (tracks, idx) ->
                player.playTracks(tracks, idx, SOURCE)
            }
            is ConnectCommand.Radio -> {
                val t = fromWire(cmd.track)
                if (t.videoId != null) player.playRadio(t, SOURCE)
            }
            // Cambiar la cola entera a distancia no está soportado todavía.
            is ConnectCommand.Queue -> Unit
        }
    }

    /* ── Reproducción local ── */

    private fun toWire(t: Track): ConnectTrack = ConnectTrack(
        id = t.videoId?.takeIf { VIDEO_ID.matches(it) } ?: "",
        title = t.title.take(300),
        artists = t.artists.take(300),
        album = t.album?.take(300),
        thumb = t.thumbnail?.takeIf { it.startsWith("https://") },
        dur = t.durationSeconds?.toDouble(),
    )

    private fun fromWire(t: ConnectTrack) = Track(
        videoId = t.id.takeIf { VIDEO_ID.matches(it) },
        title = t.title,
        artists = t.artists,
        album = t.album,
        thumbnail = t.thumb?.takeIf { it.startsWith("https://") },
        durationSeconds = t.dur?.toLong(),
    )

    /** Lo que suena aquí, en el formato del cable (una ventana de la cola alrededor de la canción actual). */
    private fun localPb(): ConnectPlayback? {
        val s = player.state.value
        val q = s.queue
        if (q.items.isEmpty() || q.index !in q.items.indices) return null
        val from = max(0, q.index - QUEUE_BEFORE)
        val to = minOf(q.items.size, q.index + QUEUE_AFTER)
        sentFrom = from
        return ConnectPlayback(
            queue = (from until to).map { toWire(q.items[it].track) },
            index = q.index - from,
            pos = s.positionMs / 1000.0,
            playing = s.isPlaying,
            at = nowMs().toDouble(),
            vol = s.volume.toDouble(),
        )
    }

    private fun sane(pb: ConnectPlayback): Boolean =
        pb.queue.size in 1..MAX_QUEUE && pb.index in pb.queue.indices && VIDEO_ID.matches(pb.queue[pb.index].id)

    private fun loadQueue(queue: List<ConnectTrack>, index: Int): Pair<List<Track>, Int>? {
        if (queue.isEmpty() || queue.size > MAX_QUEUE || index !in queue.indices) return null
        val tracks = ArrayList<Track>()
        var idx = -1
        queue.forEachIndexed { i, t ->
            if (VIDEO_ID.matches(t.id)) {
                if (i == index) idx = tracks.size
                tracks += fromWire(t)
            }
        }
        return if (idx < 0) null else tracks to idx
    }

    private fun expectedPos(pb: ConnectPlayback): Double =
        max(0.0, if (pb.playing) pb.pos + (nowMs() - pb.at) / 1000.0 else pb.pos)

    /** Posición a la que debería ir este dispositivo (ms), con el ajuste manual. */
    private fun expectedMs(pb: ConnectPlayback, offsetMs: Double): Long =
        max(0L, (expectedPos(pb) * 1000 + offsetMs).toLong())

    private fun playFrom(pb: ConnectPlayback, offsetMs: Double) {
        val (tracks, idx) = loadQueue(pb.queue, pb.index) ?: return
        applyingUntil = nowMs() + APPLY_MS
        player.playTracks(tracks, idx, SOURCE)
        pending = Pending(pb.queue[pb.index].id, pb, nowMs() + PENDING_MS, offsetMs)
    }

    /** Seguidor: se alinea con el estado que manda el centro. */
    private fun follow(pb: ConnectPlayback) {
        val s = player.state.value
        val wanted = pb.queue[pb.index].id
        val curId = s.queue.current?.track?.videoId
        val off = _offset.value
        if (curId != wanted) {
            val i = s.queue.items.indexOfFirst { it.track.videoId == wanted }
            if (i >= 0) {
                applyingUntil = nowMs() + APPLY_MS
                player.playAt(i)
                pending = Pending(wanted, pb, nowMs() + PENDING_MS, off)
            } else {
                playFrom(pb, off)
            }
            return
        }
        if (pending != null) return
        applyingUntil = nowMs() + APPLY_MS
        if (pb.playing != s.isPlaying) if (pb.playing) player.play() else player.pause()
        val target = expectedMs(pb, off)
        val drift = (s.positionMs - target).toDouble()
        _drift.value = drift
        if (pb.playing && abs(drift) > DRIFT_FIX_MS) player.seekTo(target + 40)
        else if (!pb.playing && abs(drift) > 500) player.seekTo(target)
        service.send(ConnectMessage.Drift(drift, s.volume.toDouble()), to = _hub.value?.id)
    }

    private fun broadcastState() {
        if (_role.value != Role.HOST) return
        val pb = localPb() ?: return
        service.send(ConnectMessage.State(pb, ctl()))
    }

    private fun onLocal(s: PlaybackState) {
        val now = nowMs()
        val curId = s.queue.current?.track?.videoId

        pending?.let { p ->
            if (now > p.deadline) {
                pending = null
            } else if (curId == p.id && s.durationMs > 0) {
                pending = null
                applyingUntil = now + APPLY_MS
                player.seekTo(expectedMs(p.pb, p.offsetMs))
                if (p.pb.playing) player.play() else player.pause()
                remember(s, now)
                return
            }
        }

        val trackChanged = curId != prevId
        val playingChanged = s.isPlaying != prevPlaying
        val expectedPrev = prevPos + if (prevPlaying) now - prevAt else 0L
        val jumped = !trackChanged && abs(s.positionMs - expectedPrev) > JUMP_MS
        val nearEnd = prevDur > 0 && prevDur - expectedPrev < NEAR_END_MS

        if (prevInit && now >= applyingUntil && (trackChanged || playingChanged || jumped)) {
            userAction(s, curId, trackChanged, playingChanged, jumped, nearEnd)
        }
        remember(s, now)
        publishNow(s)
    }

    private fun remember(s: PlaybackState, now: Long) {
        prevInit = true
        prevPlaying = s.isPlaying
        prevPos = s.positionMs
        prevDur = s.durationMs
        prevAt = now
        prevId = s.queue.current?.track?.videoId
    }

    /** El usuario tocó el reproductor de este dispositivo: según el papel, se reparte o se manda al centro. */
    private fun userAction(
        s: PlaybackState,
        curId: String?,
        trackChanged: Boolean,
        playingChanged: Boolean,
        jumped: Boolean,
        nearEnd: Boolean,
    ) {
        when (_role.value) {
            Role.HOST -> broadcastState()
            Role.FOLLOWER -> {
                val hub = _hub.value ?: return
                if (!_controlAll.value) return
                if (trackChanged && !nearEnd && curId != null) {
                    service.send(ConnectMessage.Cmd(ConnectCommand.PlayTrack(curId)), to = hub.id)
                } else if (playingChanged) {
                    service.send(ConnectMessage.Cmd(if (s.isPlaying) ConnectCommand.Play else ConnectCommand.Pause), to = hub.id)
                } else if (jumped) {
                    service.send(ConnectMessage.Cmd(ConnectCommand.Seek(s.positionMs / 1000.0)), to = hub.id)
                }
            }
            Role.REMOTE, Role.LEADER -> {
                // Si aquí empieza a sonar algo, se manda al otro dispositivo y aquí se calla.
                if (!s.isPlaying) return
                if (trackChanged && curId != null) {
                    val pb = localPb() ?: return
                    sendCmd(ConnectCommand.Load(pb.queue, pb.index))
                } else if (playingChanged) {
                    sendCmd(ConnectCommand.Play)
                }
                applyingUntil = nowMs() + APPLY_MS
                player.pause()
            }
            Role.NONE -> Unit
        }
    }

    /** Publica qué suena aquí para que otros Dezzy puedan entrar. */
    private fun publishNow(s: PlaybackState) {
        val t = s.queue.current?.track
        val key = if (t == null) "" else "${t.videoId}|${s.isPlaying}"
        if (key == publishedKey) return
        publishedKey = key
        service.setNow(t?.let { ConnectNow(it.videoId.orEmpty(), it.title, it.artists, s.isPlaying) })
    }

    private fun saveOffsets() {
        val text = ConnectJson.json.encodeToString(MapSerializer(String.serializer(), Double.serializer()), offsets.toMap())
        scope.launch { prefs.set(OffsetsKey, text) }
    }
}
