/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.downloads

import com.dezzy.app.core.database.db.DezzyDatabase
import com.dezzy.app.core.model.Track
import com.dezzy.app.core.prefs.DezzyPreferences
import com.dezzy.app.feature.lossless.LosslessWifiOnlyKey
import com.dezzy.app.source.stream.StreamPriority
import com.dezzy.app.source.stream.StreamUrlResolver
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.Koin
import kotlin.time.Clock

private const val KV_KEY = "downloads_v1"
private const val CHUNK_BYTES = 8L * 1024 * 1024
private const val FLUSH_BYTES = 256 * 1024
private const val GB = 1024L * 1024 * 1024
private const val MAX_ATTEMPTS = 3
private val RETRY_DELAYS_MS = longArrayOf(15_000L, 60_000L)
private const val EMIT_EVERY_MS = 200L
private const val MAX_PER_ENQUEUE = 500
private const val LIMIT_MESSAGE = "Se alcanzó el límite de espacio"
private const val NO_RESOLVER_MESSAGE = "Configura tu resolver en Ajustes › Descargas lossless"

@Serializable
private class StoredDownloads(
    val paused: Boolean = false,
    val items: List<DownloadItem> = emptyList(),
)

private class Resolved(
    val url: String,
    val ext: String,
    val format: String,
    val total: Long,
    val refresh: suspend () -> String,
)

private class Chunk(val status: Int, val total: Long?, val written: Long)

/**
 * Cola de descargas: concurrencia 1–4, pausa persistente, límite de espacio, trozos con Range de 8 MB
 * a `<id>.part` (reanudables) y reintentos automáticos (15 s, 60 s). La cola se guarda en la tabla
 * `kv` (clave `downloads_v1`).
 */
class DownloadManager(
    private val http: HttpClient,
    private val resolver: StreamUrlResolver,
    private val db: DezzyDatabase,
    private val prefs: DezzyPreferences,
    private val koin: Koin,
    private val storage: DownloadStorage,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(DownloadsState())
    val state: StateFlow<DownloadsState> = _state.asStateFlow()

    private val jobs = MutableStateFlow<Map<String, Job>>(emptyMap())
    private val retryAt = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val persistLock = Mutex()

    private val loop = lazy {
        scope.launch {
            launch {
                _state.map { s -> !s.paused && s.items.any { it.status != DownloadStatus.DONE && it.status != DownloadStatus.ERROR } }
                    .distinctUntilChanged()
                    .collect { onDownloadsActive(it) }
            }
            while (isActive) {
                runCatching { schedule() }
                withTimeoutOrNull(5_000) { wake.receive() }
            }
        }
    }

    init {
        load()
    }

    /** Idempotente: arranca el planificador (la cola ya se cargó al crear el gestor). */
    fun start() {
        loop.value
        wake.trySend(Unit)
    }

    fun enqueue(tracks: List<Track>) {
        val now = nowMs()
        val present = _state.value.items.map { it.id }.toHashSet()
        val fresh = LinkedHashMap<String, Track>()
        for (t in tracks) {
            val id = t.videoId?.takeIf { it.isNotBlank() && !it.startsWith("dzr:") } ?: continue
            if (id in present || id in fresh) continue
            fresh[id] = t
            if (fresh.size >= MAX_PER_ENQUEUE) break
        }
        if (fresh.isEmpty()) return
        val items = fresh.map { (id, t) -> DownloadItem(id = id, track = t, status = DownloadStatus.QUEUED, addedAt = now) }
        mutate { it + items }
        persist()
        wake.trySend(Unit)
    }

    fun enqueueLossless(track: Track, ref: LosslessRef) {
        val id = track.videoId?.takeIf { it.isNotBlank() } ?: "dzr:${ref.trackId}"
        if (_state.value.items.any { it.id == id }) return
        val item = DownloadItem(
            id = id,
            track = track.copy(videoId = id),
            status = DownloadStatus.QUEUED,
            addedAt = nowMs(),
            lossless = ref,
        )
        mutate { it + item }
        persist()
        wake.trySend(Unit)
    }

    fun retry(id: String) {
        val item = _state.value.items.firstOrNull { it.id == id } ?: return
        when (item.status) {
            DownloadStatus.ERROR -> {
                storage.delete(partPath(id))
                retryAt.update { it - id }
                update(id) {
                    it.copy(status = DownloadStatus.QUEUED, attempts = 0, error = null, bytes = 0, progress = 0f, totalBytes = 0)
                }
                persist()
            }
            DownloadStatus.QUEUED -> retryAt.update { it - id }
            else -> return
        }
        wake.trySend(Unit)
    }

    fun retryFailed() {
        _state.value.items.filter { it.status == DownloadStatus.ERROR }.forEach { retry(it.id) }
    }

    /** Quita de la cola. En curso (RESOLVING/SAVING): cancela el trabajo y borra el .part. DONE: borra el archivo. */
    fun remove(id: String) {
        val item = _state.value.items.firstOrNull { it.id == id } ?: return
        jobs.value[id]?.cancel()
        jobs.update { it - id }
        retryAt.update { it - id }
        item.filePath?.let { storage.delete(it) }
        storage.delete(partPath(id))
        mutate { list -> list.filter { it.id != id } }
        persist()
        wake.trySend(Unit)
    }

    fun setPaused(paused: Boolean) {
        _state.update { it.copy(paused = paused) }
        if (paused) {
            jobs.value.values.forEach { it.cancel() }
            mutate { list ->
                list.map {
                    if (it.status == DownloadStatus.RESOLVING || it.status == DownloadStatus.SAVING) {
                        it.copy(status = DownloadStatus.QUEUED)
                    } else it
                }
            }
        }
        persist()
        wake.trySend(Unit)
    }

    /** Ruta del archivo si la canción está DONE y el archivo existe. Síncrono (lo usa el reproductor). */
    fun localFile(id: String): String? =
        _state.value.items.firstOrNull { it.id == id && it.status == DownloadStatus.DONE }
            ?.filePath
            ?.takeIf { storage.exists(it) }

    fun status(id: String): Flow<DownloadStatus?> =
        _state.map { s -> s.items.firstOrNull { it.id == id }?.status }.distinctUntilChanged()

    // ---- Estado y persistencia ----

    private fun load() {
        val raw = runCatching { db.dezzyQueries.getKv(KV_KEY).executeAsOneOrNull() }.getOrNull()
        val stored = raw?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<StoredDownloads>(it) }.getOrNull() }
            ?: StoredDownloads()
        val items = stored.items.map {
            if (it.status == DownloadStatus.RESOLVING || it.status == DownloadStatus.SAVING) {
                it.copy(status = DownloadStatus.QUEUED)
            } else it
        }
        _state.value = DownloadsState(items, stored.paused, used(items), runCatching { storage.freeBytes() }.getOrNull())
    }

    private fun used(items: List<DownloadItem>): Long = items.sumOf { it.bytes }

    private fun mutate(transform: (List<DownloadItem>) -> List<DownloadItem>) {
        _state.update { s ->
            val items = transform(s.items)
            s.copy(items = items, usedBytes = used(items))
        }
    }

    private fun update(id: String, change: (DownloadItem) -> DownloadItem) {
        mutate { list -> list.map { if (it.id == id) change(it) else it } }
    }

    private fun itemOf(id: String): DownloadItem? = _state.value.items.firstOrNull { it.id == id }

    private fun persist() {
        scope.launch {
            persistLock.withLock {
                val s = _state.value
                runCatching { db.dezzyQueries.putKv(KV_KEY, json.encodeToString(StoredDownloads(s.paused, s.items))) }
            }
        }
    }

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    private fun partPath(id: String): String = storage.root() + "/" + safeId(id) + ".part"

    // ---- Planificador ----

    private suspend fun schedule() {
        val free = runCatching { storage.freeBytes() }.getOrNull()
        if (free != _state.value.freeBytes) _state.update { it.copy(freeBytes = free) }
        val s = _state.value
        if (s.paused) return
        val max = prefs.value(DownloadConcurrencyKey, DOWNLOAD_CONCURRENCY_DEFAULT).first().coerceIn(1, 4)
        val wifiOnly = prefs.value(DownloadWifiOnlyKey, DOWNLOAD_WIFI_ONLY_DEFAULT).first()
        val losslessWifiOnly = prefs.value(LosslessWifiOnlyKey, true).first()
        val metered = isMeteredNetwork()
        val now = nowMs()
        var running = s.items.count { it.status == DownloadStatus.RESOLVING || it.status == DownloadStatus.SAVING }
        for (item in s.items) {
            if (running >= max) break
            if (item.status != DownloadStatus.QUEUED || jobs.value.containsKey(item.id)) continue
            if ((retryAt.value[item.id] ?: 0L) > now) continue
            if (metered && (if (item.lossless != null) losslessWifiOnly else wifiOnly)) continue
            launchJob(item.id)
            running++
        }
    }

    private fun launchJob(id: String) {
        update(id) { it.copy(status = DownloadStatus.RESOLVING, error = null) }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                run(id)
            } finally {
                jobs.update { it - id }
                wake.trySend(Unit)
            }
        }
        jobs.update { it + (id to job) }
        job.start()
    }

    // ---- Una descarga ----

    private suspend fun run(id: String) {
        val item = itemOf(id) ?: return
        try {
            val source = resolveSource(item)
            val limitBytes = prefs.value(DownloadLimitGbKey, DOWNLOAD_LIMIT_GB_DEFAULT).first().toLong() * GB
            if (limitBytes > 0 && source.total > 0 && usedByOthers(id) + source.total > limitBytes) {
                throw DownloadFailure(LIMIT_MESSAGE, retry = false)
            }
            update(id) {
                it.copy(status = DownloadStatus.SAVING, format = source.format, totalBytes = maxOf(source.total, 0L))
            }
            persist()
            val part = partPath(id)
            download(id, source, part, limitBytes)

            val finalPath = uniquePath(item, source.ext)
            if (!storage.rename(part, finalPath)) throw DownloadFailure("No se pudo guardar el archivo")
            val size = storage.size(finalPath)
            update(id) {
                it.copy(
                    status = DownloadStatus.DONE,
                    progress = 1f,
                    bytes = size,
                    totalBytes = size,
                    filePath = finalPath,
                    error = null,
                    finishedAt = nowMs(),
                )
            }
            persist()
            retryAt.update { it - id }

            val done = itemOf(id) ?: return
            val post = koin.getOrNull<DownloadPostProcessor>()
            if (post != null) {
                try {
                    post.process(done, finalPath)
                    val newSize = storage.size(finalPath)
                    if (newSize > 0 && newSize != size) {
                        update(id) { it.copy(bytes = newSize, totalBytes = newSize) }
                        persist()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // Etiquetas/copia pública: si fallan, la descarga sigue valiendo.
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
            fail(id, e)
        }
    }

    private fun fail(id: String, e: Throwable) {
        val item = itemOf(id) ?: return
        val message = e.message?.takeIf { it.isNotBlank() } ?: "Error de descarga"
        val fatal = (e is DownloadFailure && !e.retry) || message.contains("Configura tu resolver")
        val attempts = item.attempts + 1
        if (!fatal && attempts < MAX_ATTEMPTS) {
            retryAt.update { it + (id to nowMs() + RETRY_DELAYS_MS[attempts - 1]) }
            update(id) { it.copy(status = DownloadStatus.QUEUED, attempts = attempts, error = message) }
        } else {
            storage.delete(partPath(id))
            update(id) {
                it.copy(
                    status = DownloadStatus.ERROR,
                    attempts = attempts,
                    error = message,
                    bytes = 0,
                    progress = 0f,
                )
            }
        }
        persist()
    }

    private suspend fun resolveSource(item: DownloadItem): Resolved {
        val ref = item.lossless
        if (ref != null) {
            val provider = koin.getOrNull<LosslessUrlProvider>()
                ?: throw DownloadFailure(NO_RESOLVER_MESSAGE, retry = false)
            val first = provider.resolve(ref, item.track)
            return Resolved(
                url = first.url,
                ext = first.ext,
                format = first.format,
                total = first.sizeBytes ?: 0L,
                refresh = { provider.resolve(ref, item.track).url },
            )
        }
        val quality = audioQualityOf(prefs.value(DownloadQualityKey, DOWNLOAD_QUALITY_DEFAULT).first())
        suspend fun fetch() = resolver.resolveStream(item.id, StreamPriority.DOWNLOAD, quality, force = true)
            .getOrElse { throw DownloadFailure(it.message ?: "No se pudo resolver la canción") }
        val audio = fetch()
        val webm = audio.mimeType.contains("webm", ignoreCase = true)
        return Resolved(
            url = audio.url,
            ext = if (webm) "webm" else "m4a",
            format = if (webm) "Opus" else "AAC",
            total = audio.contentLength,
            refresh = { fetch().url },
        )
    }

    private fun usedByOthers(id: String): Long =
        _state.value.items.filter { it.id != id && it.status == DownloadStatus.DONE }.sumOf { it.bytes }

    private suspend fun download(id: String, source: Resolved, part: String, limitBytes: Long) {
        var url = source.url
        var total = source.total
        var refreshes = 0
        var lastEmit = 0L
        while (true) {
            val start = storage.size(part)
            if (total > 0 && start >= total) break
            val end = if (total > 0) minOf(start + CHUNK_BYTES, total) - 1 else start + CHUNK_BYTES - 1
            val requested = end - start + 1
            val res = fetchChunk(url, start, end, part) { size ->
                if (limitBytes > 0 && usedByOthers(id) + size > limitBytes) {
                    throw DownloadFailure(LIMIT_MESSAGE, retry = false)
                }
                val now = nowMs()
                if (now - lastEmit >= EMIT_EVERY_MS) {
                    lastEmit = now
                    val t = total
                    update(id) {
                        it.copy(
                            bytes = size,
                            totalBytes = if (t > 0) t else it.totalBytes,
                            progress = if (t > 0) (size.toFloat() / t).coerceIn(0f, 1f) else 0f,
                        )
                    }
                }
            }
            if (res.status == 403 || res.status == 410) {
                if (++refreshes > 3) throw DownloadFailure("La URL caducó y no se pudo renovar")
                url = source.refresh()
                continue
            }
            if (res.status == 416) {
                if (start > 0) break
                throw DownloadFailure("El servidor rechazó el rango pedido")
            }
            if (res.total != null && res.total > 0) total = res.total
            if (res.status == 200) {
                total = storage.size(part)
                break
            }
            if (res.written <= 0) throw DownloadFailure("Respuesta vacía del servidor")
            if (total <= 0 && res.written < requested) break
        }
        val size = storage.size(part)
        if (total > 0 && size != total) {
            storage.delete(part)
            throw DownloadFailure("Descarga incompleta")
        }
        update(id) { it.copy(bytes = size, totalBytes = size, progress = 1f) }
    }

    private suspend fun fetchChunk(
        url: String,
        start: Long,
        end: Long,
        part: String,
        onSize: (Long) -> Unit,
    ): Chunk = http.prepareGet(url) {
        header(HttpHeaders.Range, "bytes=$start-$end")
        timeout {
            requestTimeoutMillis = 10 * 60_000L
            connectTimeoutMillis = 20_000L
            socketTimeoutMillis = 45_000L
        }
    }.execute { resp ->
        val code = resp.status.value
        if (code == 403 || code == 410 || code == 416) return@execute Chunk(code, null, 0)
        if (code !in 200..299) {
            throw DownloadFailure(
                "El servidor respondió $code",
                retry = code >= 500 || code == 429 || code == 408,
            )
        }
        var size = start
        if (code == 200 && start > 0) {
            // El servidor ignoró el Range: se empieza de cero.
            storage.delete(part)
            size = 0
        }
        val total = if (code == 200) {
            resp.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        } else {
            resp.headers[HttpHeaders.ContentRange]?.substringAfterLast('/')?.toLongOrNull()
        }
        val channel = resp.bodyAsChannel()
        val buf = ByteArray(FLUSH_BYTES)
        var fill = 0
        var written = 0L
        while (true) {
            val n = channel.readAvailable(buf, fill, buf.size - fill)
            if (n < 0) break
            fill += n
            if (fill == buf.size) {
                storage.append(part, buf, fill)
                size += fill
                written += fill
                fill = 0
                onSize(size)
            }
        }
        if (fill > 0) {
            storage.append(part, buf, fill)
            size += fill
            written += fill
            onSize(size)
        }
        Chunk(code, total, written)
    }

    private fun uniquePath(item: DownloadItem, ext: String): String {
        val base = safeFileName(item.track.artists, item.track.title)
        val root = storage.root()
        val plain = "$root/$base.$ext"
        val taken = storage.exists(plain) ||
            _state.value.items.any { it.id != item.id && it.filePath == plain }
        return if (taken) "$root/$base (${safeId(item.id)}).$ext" else plain
    }
}

private val ILLEGAL_NAME_CHARS = "<>:\"/\\|?*"
private val WHITESPACE = Regex("\\s+")

private fun safeId(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "")

/** «artistas» - «título» sin `<>:"/\|?*`, máx. 120 caracteres. */
internal fun safeFileName(artists: String, title: String): String {
    val raw = when {
        artists.isBlank() -> title
        title.isBlank() -> artists
        else -> "$artists - $title"
    }
    val cleaned = raw
        .filter { it !in ILLEGAL_NAME_CHARS && it.code >= 32 }
        .replace(WHITESPACE, " ")
        .trim()
    val cut = cleaned.take(120).trimEnd('.', ' ')
    return cut.ifBlank { "Sin título" }
}
