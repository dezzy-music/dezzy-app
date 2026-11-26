/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.connect

import androidx.datastore.preferences.core.stringPreferencesKey
import com.dezzy.app.core.prefs.DezzyPreferences
import com.dezzy.app.core.prefs.SecureStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.koin.core.scope.Scope
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

actual fun createConnectService(scope: Scope): ConnectService =
    JvmConnectService(scope.get<SecureStorage>(), scope.get<DezzyPreferences>(), createConnectPlatform(scope))

private val SelfIdKey = stringPreferencesKey("connect_self_id")
private val DevicesKey = stringPreferencesKey("connect_devices")
private const val GROUP_KEY_NAME = "account_connect_key"

/**
 * Dezzy Connect en la JVM (Android y escritorio). Compatible byte a byte con `connect.ts` de
 * Dezzy Desktop:
 *
 * - Avisos UDP 47830 (difusión): "DZC1" + iv(12) + AES-256-GCM(disc, JSON) + etiqueta(16), con JSON
 *   {v:1, t:"here"|"look", id, name, kind, port[, now]}.
 * - Conexión TCP al `port` del aviso. Tramas: longitud (4 bytes, big endian) + cuerpo.
 *   1. cliente -> {v:1, t:"hi", id, name, kind, n}   (n = 16 bytes hex)
 *   2. servidor -> {v:1, t:"hi", id, name, kind, n, mac: HMAC(auth, "srv|nC|nS")}
 *   3. cliente -> {t:"auth", mac: HMAC(auth, "cli|nC|nS")}
 *   Clave de sesión = HMAC(auth, "sess|nC|nS"). Desde ahí cada cuerpo es AES-256-GCM (texto + etiqueta)
 *   con iv = [dirección (0 cliente, 1 servidor), 0, 0, 0, contador de 8 bytes big endian].
 * - disc = HMAC(clave, "dezzy-connect-v1|disc"), auth = HMAC(clave, "dezzy-connect-v1|auth").
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class JvmConnectService(
    private val storage: SecureStorage,
    private val prefs: DezzyPreferences,
    private val platform: ConnectPlatform,
) : ConnectService {
    private companion object {
        const val PORT = 47830
        val MAGIC = "DZC1".toByteArray()
        const val ANNOUNCE_MS = 15_000L
        const val SEARCH_MS = 12_000L
        const val SEARCH_EVERY_MS = 3_000L
        const val AVAILABLE_MS = 40_000L
        const val HANDSHAKE_MS = 6_000
        const val OPEN_TRIES = 3
        const val REFRESH_WAIT_MS = 2_000L
        const val PING_MS = 4_000L
        /** Sin recibir nada en este tiempo se da por perdida. */
        const val IDLE_MS = 45_000
        /** Una conexión que habló hace menos de esto se considera viva al resolver un choque. */
        const val FRESH_MS = 8_000L
        const val HEAL_MS = 1_000L
        const val NET_CHECK_MS = 5_000L
        /** Tras un corte, el otro sigue como «conectado» este tiempo mientras se reconecta. */
        const val GRACE_MS = 10_000L
        const val RECONNECT_FOR_MS = 10 * 60_000L
        const val MAX_FRAME = 512 * 1024
        const val MAX_DEVICES = 20
        const val MAX_LINKS = 3
        val ID_RE = Regex("^[0-9a-f]{16}$")
        val HEX64 = Regex("^[0-9a-f]{64}$")
        val NONCE_RE = Regex("^[0-9a-f]{32}$")
        val FORWARDED = setOf("take", "state", "cmd", "join", "handoff", "stop", "drift", "offset", "ctrl")
    }

    private data class Known(val id: String, val name: String, val kind: DeviceKind, val ip: String? = null)
    private data class Seen(val ip: InetAddress, val port: Int, val lastSeen: Long, val now: ConnectNow? = null)

    /** Dispositivo con el que se cortó la conexión sin despedirse: se intenta recuperar. */
    private class Wanted(val name: String, val kind: DeviceKind, val since: Long, var next: Long) {
        var tries = 0
        var busy = false
        var graceOver = false
    }

    private class Link(val socket: Socket, val incoming: Boolean, var id: String) {
        val dir = if (incoming) 1 else 0
        var name = ""
        var kind = DeviceKind.pc
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
        var theirNonce: ByteArray? = null
        var key: ByteArray? = null
        var sendCtr = 0L
        var recvCtr = 0L
        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

        @Volatile var closed = false
        var lastRx = System.currentTimeMillis()
        /** Su reloj menos el nuestro, en ms. */
        var offset = 0.0
        var rtt: Int? = null
        val samples = ArrayDeque<Pair<Int, Double>>()
        var pingJob: Job? = null
        var ready: CompletableDeferred<String?>? = null
    }

    private val random = SecureRandom()

    /** Un solo hilo para el estado; las lecturas de red bloquean en Dispatchers.IO. */
    private val logic = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + logic)

    private val _state = MutableStateFlow(ConnectState())
    override val state: StateFlow<ConnectState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<ConnectEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<ConnectEvent> = _events.asSharedFlow()

    private val keyMutex = Mutex()
    private var started = false
    private var selfId = ""
    private var disc: ByteArray? = null
    private var auth: ByteArray? = null
    private var known = listOf<Known>()
    private val seen = HashMap<String, Seen>()
    private var udp: DatagramSocket? = null
    private var server: ServerSocket? = null
    private var tcpPort = 0
    private val links = LinkedHashMap<String, Link>()
    private var pending = 0
    private var searchJob: Job? = null
    private var searchUntil = 0L
    private val wanted = LinkedHashMap<String, Wanted>()
    private var netSig = ""
    private var lastNetCheck = 0L
    private var localNow: ConnectNow? = null
    private var lastNowAnnounce = 0L
    private var nowJob: Job? = null
    private var lastError: String? = null

    private fun now() = System.currentTimeMillis()

    /* ── Clave de grupo ── */

    override suspend fun groupKeyHex(): String = keyMutex.withLock {
        val stored = storage.get(GROUP_KEY_NAME)
        if (stored != null && HEX64.matches(stored)) return@withLock stored
        val fresh = ByteArray(32).also(random::nextBytes).toHex()
        storage.set(GROUP_KEY_NAME, fresh)
        fresh
    }

    /* ── Arranque ── */

    override fun start() {
        synchronized(this) {
            if (started) return
            started = true
        }
        scope.launch {
            selfId = prefs.value(SelfIdKey, "").first().takeIf { ID_RE.matches(it) }
                ?: ByteArray(8).also(random::nextBytes).toHex().also { prefs.set(SelfIdKey, it) }
            known = decodeKnown(prefs.value(DevicesKey, "[]").first())
            val key = groupKeyHex().hexToBytes()
            disc = hmac(key, "dezzy-connect-v1|disc")
            auth = hmac(key, "dezzy-connect-v1|auth")
            key.fill(0)
            platform.setMulticast(true)
            netSig = netSignature()
            startNetwork()
            lookKnown()
            emitState()
            // Repaso: sockets caídos, cambios de red y reconexiones pendientes.
            scope.launch {
                while (isActive) {
                    delay(HEAL_MS)
                    heal()
                }
            }
            while (isActive) {
                delay(ANNOUNCE_MS)
                announce("here", null)
                lookKnown()
                // Los que dejan de anunciarse pasan a «No disponible ahora».
                emitState()
            }
        }
    }

    /* ── Estado ── */

    private fun isAvailable(id: String): Boolean = seen[id]?.let { now() - it.lastSeen < AVAILABLE_MS } == true

    private fun live(): List<Link> = links.values.filter { it.key != null && !it.closed }

    private fun emitState() {
        val peers = live().map { ConnectPeer(it.id, it.name, it.kind, it.incoming, it.rtt) }.toMutableList()
        // Recién cortados: siguen contando unos segundos mientras se reconecta solo.
        for ((id, w) in wanted) {
            if (!w.graceOver && peers.none { it.id == id }) peers += ConnectPeer(id, w.name, w.kind, false, null)
        }
        val ids = peers.mapTo(HashSet()) { it.id }
        val next = ConnectState(
            ready = disc != null,
            searching = now() < searchUntil,
            self = ConnectSelf(selfId, platform.deviceName()),
            devices = known.map {
                val avail = isAvailable(it.id)
                ConnectDevice(it.id, it.name, it.kind, avail, it.id in ids, if (avail) seen[it.id]?.now else null)
            },
            peers = peers,
            peer = peers.firstOrNull(),
            rtt = peers.firstOrNull()?.rtt,
            error = lastError,
        )
        _state.value = next
        _events.tryEmit(ConnectEvent.StateChanged(next))
    }

    private fun remember(device: Known) {
        val old = known.firstOrNull { it.id == device.id }
        val merged = if (device.ip == null) device.copy(ip = old?.ip) else device
        if (old == merged) return
        known = (listOf(merged) + known.filter { it.id != device.id }).take(MAX_DEVICES)
        saveKnown()
    }

    private fun saveKnown() {
        val text = JsonArray(
            known.map { k ->
                buildJsonObject {
                    put("id", k.id)
                    put("name", k.name)
                    put("kind", k.kind.name)
                    k.ip?.let { put("ip", it) }
                }
            },
        ).toString()
        scope.launch { prefs.set(DevicesKey, text) }
    }

    private fun decodeKnown(text: String): List<Known> = runCatching {
        (ConnectJson.json.parseToJsonElement(text) as? JsonArray).orEmpty().mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val id = o.str("id")?.takeIf { ID_RE.matches(it) } ?: return@mapNotNull null
            Known(id, o.str("name") ?: "Ordenador", if (o.str("kind") == "phone") DeviceKind.phone else DeviceKind.pc, o.str("ip"))
        }
    }.getOrDefault(emptyList())

    /* ── Blindaje ── */

    /** Huella de las IP de este equipo: si cambia, la red cambió. */
    private fun netSignature(): String = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nif ->
                nif.interfaceAddresses.filter { it.address is Inet4Address }.map { "${it.address.hostAddress}/${it.networkPrefixLength}" }
            }
            .sorted()
            .joinToString(",")
    }.getOrDefault("")

    /** La red cambió: avisos nuevos, búsqueda y reconexión inmediata. */
    private fun networkChanged() {
        udp?.let { old ->
            udp = null
            runCatching { old.close() }
        }
        startNetwork()
        announce("look", null)
        lookKnown()
        val soon = now() + 500 + random.nextInt(1_000)
        for (w in wanted.values) w.next = minOf(w.next, soon)
    }

    /** Pregunta directamente a la última IP de cada dispositivo conocido que no esté conectado. */
    private fun lookKnown() {
        for (k in known) {
            val ip = k.ip ?: continue
            if (links[k.id]?.key != null) continue
            val addr = runCatching { InetAddress.getByName(ip) }.getOrNull() ?: continue
            announce("look", addr to PORT)
        }
    }

    private fun heal() {
        if (disc == null) return
        val t = now()
        if (t - lastNetCheck >= NET_CHECK_MS) {
            lastNetCheck = t
            val sig = netSignature()
            if (sig != netSig) {
                val changed = netSig.isNotEmpty()
                netSig = sig
                if (changed) {
                    networkChanged()
                    return
                }
            }
            if (udp == null || server == null) startNetwork()
        }
        val iter = wanted.entries.iterator()
        while (iter.hasNext()) {
            val (id, w) = iter.next()
            if (links[id]?.key != null) {
                iter.remove()
                continue
            }
            if (t - w.since > RECONNECT_FOR_MS) {
                iter.remove()
                emitState()
                continue
            }
            if (!w.graceOver && t - w.since >= GRACE_MS) {
                w.graceOver = true
                emitState()
            }
            if (w.busy || t < w.next) continue
            w.busy = true
            w.tries++
            scope.launch {
                try {
                    if (!isAvailable(id)) refresh(id)
                    if (wanted[id] === w) openOnce(id)
                } finally {
                    w.busy = false
                    w.next = now() + minOf(30_000L, 1_000L shl minOf(w.tries, 5)) + random.nextInt(1_000)
                }
            }
        }
    }

    /** Apunta un corte inesperado para reconectar solo. */
    private fun want(l: Link) {
        if (l.id.isEmpty() || disc == null) return
        val t = now()
        wanted[l.id] = Wanted(l.name, l.kind, t, t + 300 + random.nextInt(700))
    }

    /* ── Avisos en la red ── */

    private fun startNetwork() {
        if (disc == null) return
        try {
            if (server == null) {
                // Si el puerto está ocupado (otra copia de Dezzy) vale cualquiera: va en el aviso.
                val srv = runCatching { ServerSocket(PORT) }.getOrElse { ServerSocket(0) }
                server = srv
                tcpPort = srv.localPort
                scope.launch(Dispatchers.IO) { acceptLoop(srv) }
            }
            if (udp == null) {
                val sock = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(PORT))
                }
                udp = sock
                scope.launch(Dispatchers.IO) { receiveLoop(sock) }
            }
            if (lastError != null) {
                lastError = null
                emitState()
            }
        } catch (e: Exception) {
            if (lastError == null) {
                lastError = "No se pudo usar la red local"
                emitState()
            }
        }
        announce("here", null)
    }

    private fun broadcastTargets(): List<InetAddress> {
        val out = linkedSetOf<InetAddress>(InetAddress.getByName("255.255.255.255"))
        runCatching {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (a in nif.interfaceAddresses) a.broadcast?.let(out::add)
            }
        }
        return out.toList()
    }

    override fun setNow(now: ConnectNow?) {
        val clean = now?.takeIf { it.title.isNotBlank() }?.let {
            ConnectNow(
                id = it.id.trim().take(11),
                title = it.title.trim().take(80),
                artists = it.artists.trim().take(80),
                playing = it.playing,
            )
        }
        scope.launch {
            if (localNow == clean) return@launch
            localNow = clean
            if (udp == null || disc == null || tcpPort == 0) return@launch
            val wait = 1_200L - (now() - lastNowAnnounce)
            if (wait <= 0L) {
                nowJob?.cancel()
                nowJob = null
                lastNowAnnounce = now()
                announce("here", null)
            } else if (nowJob?.isActive != true) {
                nowJob = scope.launch {
                    delay(wait)
                    lastNowAnnounce = now()
                    announce("here", null)
                }
            }
        }
    }

    private fun parseNow(raw: Any?): ConnectNow? {
        val o = raw as? JsonObject ?: return null
        val title = o.str("title")?.trim()?.take(80)?.takeIf { it.isNotEmpty() } ?: return null
        val artists = o.str("artists")?.trim()?.take(80).orEmpty()
        val id = o.str("id")?.trim()?.take(11).orEmpty()
        val playing = (o["playing"] as? JsonPrimitive)?.booleanOrNull == true
        return ConnectNow(id = id, title = title, artists = artists, playing = playing)
    }

    private fun announce(t: String, to: Pair<InetAddress, Int>?) {
        val sock = udp ?: return
        val key = disc ?: return
        if (tcpPort == 0) return
        val body = buildJsonObject {
            put("v", 1)
            put("t", t)
            put("id", selfId)
            put("name", platform.deviceName())
            put("kind", platform.kind.name)
            put("port", tcpPort)
            if (t == "here") {
                localNow?.let { n ->
                    put(
                        "now",
                        buildJsonObject {
                            put("id", n.id)
                            put("title", n.title)
                            put("artists", n.artists)
                            put("playing", n.playing)
                        },
                    )
                }
            }
        }.toString().toByteArray()
        val iv = ByteArray(12).also(random::nextBytes)
        val packet = MAGIC + iv + gcmEncrypt(key, iv, body)
        val targets = to?.let { listOf(it) } ?: broadcastTargets().map { it to PORT }
        scope.launch(Dispatchers.IO) {
            for ((ip, port) in targets) runCatching { sock.send(DatagramPacket(packet, packet.size, ip, port)) }
        }
    }

    private suspend fun receiveLoop(sock: DatagramSocket) {
        val buf = ByteArray(2048)
        while (!sock.isClosed) {
            val packet = DatagramPacket(buf, buf.size)
            if (runCatching { sock.receive(packet) }.isFailure) break
            val data = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
            val from = packet.address
            val fromPort = packet.port
            withContext(logic) { onDatagram(data, from, fromPort) }
        }
        withContext(logic) { if (udp === sock) udp = null }
    }

    private fun onDatagram(msg: ByteArray, from: InetAddress, fromPort: Int) {
        val key = disc ?: return
        if (msg.size < 34 || msg.size > 2048 || !msg.copyOfRange(0, 4).contentEquals(MAGIC)) return
        val plain = gcmDecrypt(key, msg.copyOfRange(4, 16), msg.copyOfRange(16, msg.size)) ?: return
        val d = runCatching { ConnectJson.json.parseToJsonElement(plain.decodeToString()).jsonObject }.getOrNull() ?: return
        val id = d.str("id") ?: return
        if (d.int("v") != 1 || !ID_RE.matches(id) || id == selfId) return
        val port = d.int("port") ?: return
        if (port !in 1..65535) return
        val kind = if (d.str("kind") == "phone") DeviceKind.phone else DeviceKind.pc
        val name = d.str("name")?.trim()?.take(60)?.ifBlank { null } ?: if (kind == DeviceKind.phone) "Móvil" else "Ordenador"
        val t = d.str("t")
        val nowInfo = if (t == "here") parseNow(d["now"]) else seen[id]?.now
        seen[id] = Seen(from, port, now(), nowInfo)
        remember(Known(id, name, kind, from.hostAddress))
        if (t == "look") announce("here", from to fromPort)
        emitState()
    }

    override fun search() {
        if (!started) return
        scope.launch {
            startNetwork()
            searchJob?.cancel()
            searchJob = scope.launch {
                searchUntil = now() + SEARCH_MS
                while (now() < searchUntil) {
                    emitState()
                    announce("look", null)
                    delay(SEARCH_EVERY_MS)
                }
                searchUntil = 0L
                emitState()
            }
        }
    }

    /* ── Conexiones ── */

    private suspend fun acceptLoop(srv: ServerSocket) {
        while (!srv.isClosed) {
            val sock = runCatching { srv.accept() }.getOrNull() ?: break
            withContext(logic) {
                if (disc == null || pending >= 4) {
                    runCatching { sock.close() }
                } else {
                    pending++
                    attach(sock, incoming = true, id = "")
                }
            }
        }
        // El servidor se rompió: se suelta y el repaso periódico abre otro.
        withContext(logic) { if (server === srv) server = null }
    }

    private fun attach(sock: Socket, incoming: Boolean, id: String): Link {
        sock.tcpNoDelay = true
        sock.soTimeout = HANDSHAKE_MS
        val l = Link(sock, incoming, id)
        scope.launch(Dispatchers.IO) {
            try {
                while (!l.closed) {
                    val len = l.input.readInt()
                    if (len < 0 || len > MAX_FRAME || (l.key == null && len > 4096)) break
                    val body = ByteArray(len)
                    l.input.readFully(body)
                    val ok = withContext(logic) { onFrame(l, body) }
                    if (!ok) break
                }
            } catch (_: Exception) {
                // Cortada, sin respuesta a tiempo o basura en el canal.
            }
            withContext(logic) {
                if (incoming) pending--
                drop(l, if (l.key == null) "No responde" else "Se cortó la conexión")
            }
        }
        return l
    }

    private fun writeFrame(l: Link, body: ByteArray) {
        if (l.closed) return
        runCatching {
            l.output.writeInt(body.size)
            l.output.write(body)
            l.output.flush()
        }.onFailure { drop(l, "Se cortó la conexión") }
    }

    private fun sendPlain(l: Link, value: JsonObject) = writeFrame(l, value.toString().toByteArray())

    private fun sendSecure(l: Link, text: String) {
        val key = l.key ?: return
        writeFrame(l, gcmEncrypt(key, ivFor(l.dir, l.sendCtr++), text.toByteArray()))
    }

    private fun sendSecure(l: Link, value: JsonObject) = sendSecure(l, value.toString())

    private fun hello(l: Link, mac: String? = null) = buildJsonObject {
        put("v", 1)
        put("t", "hi")
        put("id", selfId)
        put("name", platform.deviceName())
        put("kind", platform.kind.name)
        put("n", l.nonce.toHex())
        if (mac != null) put("mac", mac)
    }

    private fun validHello(d: JsonObject): Boolean {
        val id = d.str("id") ?: return false
        val n = d.str("n") ?: return false
        return d.int("v") == 1 && d.str("t") == "hi" && ID_RE.matches(id) && id != selfId && NONCE_RE.matches(n)
    }

    private fun setIdentity(l: Link, d: JsonObject) {
        l.id = d.str("id").orEmpty()
        l.kind = if (d.str("kind") == "phone") DeviceKind.phone else DeviceKind.pc
        l.name = d.str("name")?.trim()?.take(60)?.ifBlank { null } ?: if (l.kind == DeviceKind.phone) "Móvil" else "Ordenador"
    }

    private fun handMac(label: String, client: ByteArray, server: ByteArray): String =
        hmac(auth!!, "$label|${client.toHex()}|${server.toHex()}").toHex()

    private fun closeLink(l: Link, bye: Boolean) {
        if (l.closed) return
        if (bye && l.key != null) sendSecure(l, buildJsonObject { put("t", "bye") })
        l.closed = true
        l.pingJob?.cancel()
        runCatching { l.socket.close() }
        l.ready?.complete("Se cortó la conexión")
        l.ready = null
        if (links[l.id] === l) {
            links.remove(l.id)
            emitState()
        }
    }

    private fun drop(l: Link, message: String?): Boolean {
        // Estaba conectado y se cortó sin despedirse: se intentará recuperar.
        if (l.key != null && !l.closed && links[l.id] === l) want(l)
        val report = l.ready
        l.ready = null
        closeLink(l, bye = false)
        report?.complete(message ?: "No se pudo conectar")
        return false
    }

    private fun ready(l: Link): Boolean {
        val old = links[l.id]?.takeIf { it !== l }
        if (old != null) {
            // Choque: los dos abrieron a la vez. Gana la que abrió el de id menor, igual en los dos
            // lados, siempre que la vieja siga viva; si no, la nueva sustituye a la vieja.
            val oldOpener = if (old.incoming) old.id else selfId
            val newOpener = if (l.incoming) l.id else selfId
            val oldAlive = old.key != null && !old.closed && now() - old.lastRx < FRESH_MS
            if (oldAlive && oldOpener != newOpener && oldOpener < newOpener) {
                val report = l.ready
                l.ready = null
                closeLink(l, bye = false)
                report?.complete(null)
                return false
            }
            // Primero la nueva, para que la sesión no vea un «se perdió» entre medias.
            links[l.id] = l
            closeLink(old, bye = false)
        } else if (links.size >= MAX_LINKS) {
            return drop(l, "Ya hay ${MAX_LINKS + 1} dispositivos conectados")
        }
        links[l.id] = l
        wanted.remove(l.id)
        runCatching { l.socket.soTimeout = IDLE_MS }
        remember(Known(l.id, l.name, l.kind))
        l.pingJob = scope.launch {
            while (!l.closed) {
                if (now() - l.lastRx > IDLE_MS) {
                    drop(l, null)
                    break
                }
                sendSecure(l, buildJsonObject { put("t", "ping"); put("a", now()) })
                delay(PING_MS)
            }
        }
        l.ready?.complete(null)
        l.ready = null
        emitState()
        return true
    }

    private fun handshake(l: Link, body: ByteArray): Boolean {
        val authKey = auth ?: return drop(l, null)
        val d = runCatching { ConnectJson.json.parseToJsonElement(body.decodeToString()).jsonObject }.getOrNull()
            ?: return drop(l, null)
        if (l.incoming) {
            val theirs = l.theirNonce
            if (theirs == null) {
                if (!validHello(d)) return drop(l, null)
                val n = d.str("n")!!.hexToBytes()
                l.theirNonce = n
                setIdentity(l, d)
                sendPlain(l, hello(l, handMac("srv", n, l.nonce)))
                return true
            }
            if (d.str("t") != "auth" || !sameHex(d.str("mac"), handMac("cli", theirs, l.nonce))) return drop(l, null)
            l.key = hmac(authKey, "sess|${theirs.toHex()}|${l.nonce.toHex()}")
            return ready(l)
        }
        if (!validHello(d) || d.str("id") != l.id) return drop(l, "Ese dispositivo ya no responde igual; busca otra vez")
        val n = d.str("n")!!.hexToBytes()
        l.theirNonce = n
        if (!sameHex(d.str("mac"), handMac("srv", l.nonce, n))) return drop(l, "Ese dispositivo no es de tu grupo")
        sendPlain(l, buildJsonObject { put("t", "auth"); put("mac", handMac("cli", l.nonce, n)) })
        l.key = hmac(authKey, "sess|${l.nonce.toHex()}|${n.toHex()}")
        setIdentity(l, d)
        return ready(l)
    }

    private fun onSecure(l: Link, msg: JsonObject) {
        when (val t = msg.str("t")) {
            "ping" -> {
                // Se devuelve tal cual llegó (número en el reloj del otro).
                val a = msg["a"]?.takeIf { msg.num("a") != null } ?: return
                sendSecure(l, buildJsonObject { put("t", "pong"); put("a", a); put("b", now()) })
            }
            "pong" -> {
                val a = msg.num("a") ?: return
                val b = msg.num("b") ?: return
                val rtt = (now() - a).toInt()
                if (rtt < 0 || rtt > 10_000) return
                l.samples.addLast(rtt to (b - (a + rtt / 2.0)))
                while (l.samples.size > 8) l.samples.removeFirst()
                // La medida con menos ida y vuelta es la más fiable.
                l.offset = l.samples.minBy { it.first }.second
                val changed = l.rtt == null || kotlin.math.abs(l.rtt!! - rtt) > 5
                l.rtt = rtt
                if (changed) emitState()
            }
            "bye" -> {
                // Se despidió a propósito: no hay que reconectar.
                if (links[l.id] === l) wanted.remove(l.id)
                closeLink(l, bye = false)
            }
            else -> {
                if (links[l.id] !== l || t == null || t !in FORWARDED) return
                // `pb.at` viaja en el reloj del otro: se pasa al nuestro.
                val pb = msg["pb"] as? JsonObject
                val at = pb?.num("at")
                val out = if (pb != null && at != null) {
                    JsonObject(msg + ("pb" to JsonObject(pb + ("at" to JsonPrimitive(at - l.offset)))))
                } else msg
                val message = ConnectJson.decode(out) ?: return
                _events.tryEmit(ConnectEvent.Message(message, l.id, l.name))
            }
        }
    }

    private fun onFrame(l: Link, body: ByteArray): Boolean {
        l.lastRx = now()
        val key = l.key ?: return handshake(l, body)
        val plain = gcmDecrypt(key, ivFor(1 - l.dir, l.recvCtr), body) ?: return drop(l, null)
        l.recvCtr++
        val msg = runCatching { ConnectJson.json.parseToJsonElement(plain.decodeToString()).jsonObject }.getOrNull()
            ?: return drop(l, null)
        onSecure(l, msg)
        return !l.closed
    }

    /**
     * Abre conexión con un dispositivo del grupo. Reintenta sola hasta [OPEN_TRIES] veces: lo normal
     * es que el primer intento falle porque la dirección que teníamos del otro ya no vale. Antes de
     * cada reintento se le pregunta «¿dónde estás?» y se espera un momento a su respuesta.
     */
    override suspend fun open(id: String): Result<Unit> {
        var last: String? = null
        for (attempt in 0 until OPEN_TRIES) {
            if (attempt > 0) refresh(id)
            val (problem, retry) = openOnce(id)
            if (problem == null) return Result.success(Unit)
            last = problem
            if (!retry) break
        }
        return Result.failure(IllegalStateException(last ?: "No se pudo conectar"))
    }

    /** Pide al dispositivo que se anuncie y espera (poco) a que conteste con su dirección. */
    private suspend fun refresh(id: String) {
        val before = withContext(logic) {
            announce("look", null)
            // También directo a la última dirección conocida, por si la difusión se pierde.
            seen[id]?.let { announce("look", it.ip to PORT) }
            seen[id]?.lastSeen ?: 0L
        }
        val until = now() + REFRESH_WAIT_MS
        while (now() < until) {
            delay(150)
            if ((withContext(logic) { seen[id]?.lastSeen } ?: 0L) > before) return
        }
    }

    /** Un intento. Devuelve (motivo del fallo o null, si merece la pena reintentar). */
    private suspend fun openOnce(id: String): Pair<String?, Boolean> {
        var problem: String? = null
        var retry = true
        var already = false
        val where: Seen? = withContext(logic) {
            when {
                disc == null -> null.also { problem = "Vincula el móvil para usar Dispositivos"; retry = false }
                links[id]?.let { it.key != null && !it.closed } == true -> null.also { already = true }
                links.size >= MAX_LINKS -> null.also { problem = "Ya suena en ${MAX_LINKS + 1} dispositivos, el máximo"; retry = false }
                else -> seen[id]?.takeIf { isAvailable(id) } ?: null.also { problem = "No disponible ahora" }
            }
        }
        if (already) return null to false
        if (where == null) return problem to retry
        val ready = CompletableDeferred<String?>()
        val sock = withContext(Dispatchers.IO) {
            runCatching { Socket().apply { connect(InetSocketAddress(where.ip, where.port), HANDSHAKE_MS) } }.getOrNull()
        } ?: return "No se pudo conectar" to true
        val link = withContext(logic) {
            attach(sock, incoming = false, id = id).also {
                it.ready = ready
                sendPlain(it, hello(it))
            }
        }
        val result = withTimeoutOrNull(HANDSHAKE_MS + 2_000L) { ready.await() }
        if (result == null && !ready.isCompleted) {
            // Sin respuesta: se cierra este intento para que no quede a medias.
            withContext(logic) { drop(link, "No responde") }
            return "No responde" to true
        }
        val message = result ?: return null to false
        val fatal = message.startsWith("Ya hay") || message.contains("no es de tu grupo")
        return message to !fatal
    }

    override fun send(message: ConnectMessage, to: String?, except: String?) {
        val text = ConnectJson.encode(message)
        scope.launch {
            for (l in live()) {
                if (to != null && l.id != to) continue
                if (except != null && l.id == except) continue
                sendSecure(l, text)
            }
        }
    }

    override fun close(id: String?) {
        scope.launch {
            if (id == null) wanted.clear() else wanted.remove(id)
            for (l in links.values.toList()) if (id == null || l.id == id) closeLink(l, bye = true)
            emitState()
        }
    }

    override fun forget(id: String) {
        scope.launch {
            wanted.remove(id)
            links[id]?.let { closeLink(it, bye = true) }
            seen.remove(id)
            known = known.filter { it.id != id }
            saveKnown()
            emitState()
        }
    }

    /* ── Cifrado y utilidades ── */

    private fun hmac(key: ByteArray, text: String): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(text.toByteArray(Charsets.UTF_8))
        }

    /** Devuelve texto cifrado + etiqueta (16), igual que en el ordenador. */
    private fun gcmEncrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            doFinal(data)
        }

    private fun gcmDecrypt(key: ByteArray, iv: ByteArray, body: ByteArray): ByteArray? {
        if (body.size < 16) return null
        return runCatching {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
                doFinal(body)
            }
        }.getOrNull()
    }

    /** [dirección, 0, 0, 0, contador de 8 bytes big endian]. */
    private fun ivFor(dir: Int, counter: Long): ByteArray =
        ByteBuffer.allocate(12).put(dir.toByte()).put(0).put(0).put(0).putLong(counter).array()

    private fun sameHex(a: String?, b: String): Boolean {
        if (a == null || a.length != b.length || !a.all { it in '0'..'9' || it in 'a'..'f' }) return false
        return MessageDigest.isEqual(a.hexToBytes(), b.hexToBytes())
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
}
