/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.video

import com.dezzy.app.core.database.db.DezzyDatabase
import com.dezzy.app.core.model.Track
import com.dezzy.app.source.youtube.YouTubeMusicClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.time.Clock

/** Streams resueltos para un video concreto. */
data class ResolvedVideo(
    val videoId: String,
    /** Manifiesto HLS maestro (video+audio en el mismo stream, bitrate adaptativo). */
    val hlsUrl: String?,
    /** Video progresivo sin audio (se combina con [audioUrl]). */
    val videoUrl: String?,
    /** Audio progresivo para combinar con [videoUrl]. */
    val audioUrl: String?,
    /** Progresivo con video+audio juntos (itag 18 y similares). */
    val muxedUrl: String?,
    val height: Int?,
    val title: String?,
    val author: String?,
    val lengthSeconds: Int?,
    val expiresAtMs: Long,
    val source: String,
) {
    /** Pista «Art Track» de YouTube Music: solo la portada fija, no es un videoclip. */
    val isArtTrack: Boolean get() = author?.endsWith(" - Topic") == true

    /** Stream único que un receptor Cast puede reproducir, con su tipo MIME. */
    fun castStream(): Pair<String, String>? = when {
        muxedUrl != null -> muxedUrl to "video/mp4"
        hlsUrl != null -> hlsUrl to "application/x-mpegURL"
        else -> null
    }

    /** URL de video para usos sin audio (fondo animado del reproductor). */
    fun silentVideoUrl(): String? = videoUrl ?: muxedUrl ?: hlsUrl
}

/** Errores tipados con el mensaje que se enseña al usuario. */
sealed class VideoError(val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause) {
    class NotFound : VideoError("No encontramos un videoclip para esta canción.")
    class RegionBlocked : VideoError("Este videoclip no está disponible en tu región.")
    class AgeRestricted : VideoError("Este videoclip tiene restricción de edad y no se puede ver aquí.")
    class RateLimited : VideoError("YouTube está limitando las peticiones. Inténtalo de nuevo en unos minutos.")
    class Network(cause: Throwable?) : VideoError("Sin conexión. Revisa tu red e inténtalo de nuevo.", cause)
    class Unplayable(reason: String?) : VideoError(
        reason?.takeIf { it.isNotBlank() }?.let { "No se puede reproducir este videoclip: $it" }
            ?: "No se puede reproducir este videoclip.",
    )
}

private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.long(key: String, default: Long): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: default
private fun JsonObject.int(key: String, default: Int = 0): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: default
private fun JsonObject.objOrNull(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.arrOrNull(key: String): JsonArray? = this[key] as? JsonArray
private fun JsonElement?.at(index: Int): JsonElement? = (this as? JsonArray)?.getOrNull(index)

/**
 * Resuelve el videoclip de una canción para el «modo video».
 *
 * Diseño pensado para producción a gran escala:
 * - La resolución ocurre EN CADA DISPOSITIVO. Las URLs de googlevideo van atadas a la IP que
 *   las pidió (`/ip/…`), así que un servidor central no puede resolver por millones de usuarios;
 *   repartir el trabajo en los clientes es lo que escala.
 * - Cliente VISIONOS de InnerTube: con un visitorData fresco devuelve streams directos (sin
 *   poToken ni descifrado JS) y un manifiesto HLS con variantes H.264+AAC de 144p a 1080p.
 * - Cada dispositivo hace el mínimo de peticiones: caché de resultados hasta su caducidad,
 *   deduplicación de peticiones simultáneas, como mucho 3 resoluciones en paralelo, backoff
 *   cuando YouTube limita, y el mapeo canción→videoclip se guarda 30 días.
 */
class MusicVideoResolver(
    private val http: HttpClient,
    private val client: YouTubeMusicClient,
    private val db: DezzyDatabase,
) {
    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"
        private const val CLIENT_NAME = "VISIONOS"
        private const val CLIENT_VERSION = "1.02"
        private const val CLIENT_ID = "101"
        private const val DEVICE_MAKE = "Apple"
        private const val DEVICE_MODEL = "RealityDevice17,1"
        private const val OS_NAME = "visionOS"
        private const val OS_VERSION = "26.5.23O471"
        private const val PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
        private const val VISITOR_URL = "https://www.youtube.com/sw.js_data"

        private const val VISITOR_TTL_MS = 6L * 60 * 60 * 1000
        private const val DEFAULT_EXPIRES_SEC = 21_540L
        private const val EXPIRY_SAFETY_MS = 5L * 60 * 1000
        private const val MAX_RESULT_ENTRIES = 64
        private const val MAX_PARALLEL_RESOLVES = 3
        private const val BACKOFF_MS = 10L * 60 * 1000
        private const val MAPPING_TTL_MS = 30L * 24 * 60 * 60 * 1000
        private const val NEGATIVE_TTL_MS = 24L * 60 * 60 * 1000
        private const val MAPPING_KEY = "video.mappings"
        private const val MAPPING_MAX_ENTRIES = 2_000
        private const val MIN_MATCH_SCORE = 5.0
        private const val MAPPING_NONE = "\u0000none"

        private const val HL = "es"
        private const val GL = "US"

        private val NOISE = Regex(
            """[(\[][^)\]]*(official|oficial|video|vídeo|audio|lyric|letra|visuali[sz]er|hd|4k|remaster)[^)\]]*[)\]]""",
            RegexOption.IGNORE_CASE,
        )
        private val ARTIST_SPLIT = Regex("""\s*(,|&|\bfeat\.?|\bft\.?|\bx\b|\by\b|\bwith\b)\s*""", RegexOption.IGNORE_CASE)
        private val PENALIZED = listOf(
            "live", "en vivo", "en directo", "cover", "karaoke", "reaction", "reaccion", "8d", "slowed",
            "sped up", "nightcore", "instrumental", "tutorial", "acustico", "acoustic", "concierto",
            "concert", "remix", "teaser", "trailer", "shorts", "behind the scenes",
        )
        private val OFFICIAL = listOf("official video", "official music video", "video oficial", "videoclip", "video musical")
        private val LYRIC = listOf("lyric", "lyrics", "letra", "audio", "visualizer")
        private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")

        private val ACCENTS = mapOf(
            'á' to 'a', 'à' to 'a', 'â' to 'a', 'ä' to 'a', 'ã' to 'a', 'å' to 'a',
            'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
            'í' to 'i', 'ì' to 'i', 'î' to 'i', 'ï' to 'i',
            'ó' to 'o', 'ò' to 'o', 'ô' to 'o', 'ö' to 'o', 'õ' to 'o',
            'ú' to 'u', 'ù' to 'u', 'û' to 'u', 'ü' to 'u',
            'ñ' to 'n', 'ç' to 'c', 'ý' to 'y', 'ÿ' to 'y',
        )

        /** Mensaje para el usuario a partir de cualquier error del resolvedor. */
        fun userMessageFor(error: Throwable?): String =
            (error as? VideoError)?.userMessage ?: "No hay video musical disponible para esta canción."
    }

    private class MappingEntry(val target: String, val savedAt: Long)

    // ---- Estado en memoria ----

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Caché LRU: el último usado va al final; al pasar de [MAX_RESULT_ENTRIES] se borra el primero. */
    private val resultCache = LinkedHashMap<String, ResolvedVideo>()
    private val cacheMutex = Mutex()
    private val metadataMatches = HashMap<String, String>()
    private val keyLocks = Array(32) { Mutex() }
    private val resolveGate = Semaphore(MAX_PARALLEL_RESOLVES)
    private val visitorMutex = Mutex()

    private var visitorCache: Pair<String, Long>? = null
    private var backoffUntilMs = 0L

    private val mappingMutex = Mutex()
    private var mappings: MutableMap<String, MappingEntry>? = null

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    // ---- API pública ----

    /**
     * Resuelve el videoclip de una canción. Si [mediaId] es una pista «Art Track» (solo portada),
     * busca el videoclip oficial y recuerda el emparejamiento para no repetir la búsqueda.
     */
    suspend fun resolveForSong(
        mediaId: String,
        title: String?,
        artist: String?,
        durationSec: Int?,
        maxHeight: Int,
    ): Result<ResolvedVideo> = withContext(Dispatchers.Default) {
        runCatching {
            val mapped = readMapping(mediaId)
            if (mapped == MAPPING_NONE) throw VideoError.NotFound()
            if (mapped != null) {
                try {
                    return@runCatching resolveStreams(mapped, maxHeight)
                } catch (e: VideoError) {
                    if (e is VideoError.Network || e is VideoError.RateLimited) throw e
                    // El videoclip emparejado dejó de estar disponible: se vuelve a buscar.
                    removeMapping(mediaId)
                }
            }

            var directVideo: ResolvedVideo? = null
            var directError: VideoError? = null
            try {
                directVideo = resolveStreams(mediaId, maxHeight)
            } catch (e: VideoError) {
                if (e is VideoError.Network || e is VideoError.RateLimited) throw e
                directError = e
            }

            if (directVideo != null && !directVideo.isArtTrack) {
                writeMapping(mediaId, mediaId)
                return@runCatching directVideo
            }

            val searchTitle = title?.takeIf { it.isNotBlank() } ?: directVideo?.title
            val searchArtist = artist?.takeIf { it.isNotBlank() }
                ?: directVideo?.author?.removeSuffix(" - Topic")
            val searchDuration = durationSec?.takeIf { it > 0 } ?: directVideo?.lengthSeconds
            val candidate = findMusicVideo(searchTitle, searchArtist, searchDuration, exclude = mediaId)
            if (candidate == null) {
                writeMapping(mediaId, MAPPING_NONE)
                throw directError ?: VideoError.NotFound()
            }
            val video = resolveStreams(candidate, maxHeight)
            writeMapping(mediaId, candidate)
            video
        }
    }

    /**
     * Busca el videoclip por título y artista (sin mediaId) y lo resuelve. Lo usa el fondo
     * animado del reproductor cuando no hay canvas de Spotify/Apple.
     */
    suspend fun resolveByMetadata(
        title: String,
        artist: String?,
        durationSec: Int?,
        maxHeight: Int,
    ): Result<ResolvedVideo> = withContext(Dispatchers.Default) {
        runCatching {
            val key = normalize(title) + "|" + normalize(artist.orEmpty())
            val known = cacheMutex.withLock { metadataMatches[key] }
            if (known == MAPPING_NONE) throw VideoError.NotFound()
            val videoId = known ?: findMusicVideo(title, artist, durationSec, exclude = "")
                .also { found -> cacheMutex.withLock { metadataMatches[key] = found ?: MAPPING_NONE } }
                ?: throw VideoError.NotFound()
            resolveStreams(videoId, maxHeight)
        }
    }

    /** Resuelve directamente un videoId (sin buscar videoclip alternativo). */
    suspend fun resolve(videoId: String, maxHeight: Int): Result<ResolvedVideo> =
        withContext(Dispatchers.Default) { runCatching { resolveStreams(videoId, maxHeight) } }

    /** Olvida un resultado (por ejemplo, cuando el reproductor recibe un 403). */
    fun invalidate(videoId: String) {
        scope.launch {
            cacheMutex.withLock {
                resultCache.keys.filter { it.startsWith("$videoId:") }.forEach { resultCache.remove(it) }
            }
        }
    }

    // ---- Resolución de streams ----

    private fun lockFor(key: String): Mutex = keyLocks[(key.hashCode() and Int.MAX_VALUE) % keyLocks.size]

    private suspend fun cachedResult(key: String): ResolvedVideo? = cacheMutex.withLock {
        val hit = resultCache.remove(key) ?: return@withLock null
        if (hit.expiresAtMs > nowMs()) {
            resultCache[key] = hit // orden de uso
            hit
        } else {
            null
        }
    }

    private suspend fun storeResult(key: String, value: ResolvedVideo) = cacheMutex.withLock {
        resultCache.remove(key)
        resultCache[key] = value
        while (resultCache.size > MAX_RESULT_ENTRIES) {
            resultCache.remove(resultCache.keys.first())
        }
    }

    private suspend fun resolveStreams(videoId: String, maxHeight: Int): ResolvedVideo {
        val key = "$videoId:$maxHeight"
        cachedResult(key)?.let { return it }
        return lockFor(key).withLock {
            // Otra corrutina pudo resolverlo mientras esperábamos el candado.
            cachedResult(key)?.let { return@withLock it }
            val resolved = if (nowMs() < backoffUntilMs) {
                throw VideoError.RateLimited()
            } else {
                resolveGate.withPermit { resolveWithVisionOs(videoId, maxHeight) }
            }
            storeResult(key, resolved)
            resolved
        }
    }

    private suspend fun resolveWithVisionOs(videoId: String, maxHeight: Int): ResolvedVideo {
        var refreshedVisitor = false
        for (attempt in 0..1) {
            val visitor = visitorData(forceRefresh = refreshedVisitor)
            val json = try {
                callPlayer(videoId, visitor)
            } catch (e: VideoError.RateLimited) {
                backoffUntilMs = nowMs() + BACKOFF_MS
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: VideoError) {
                throw e
            } catch (e: Exception) {
                throw VideoError.Network(e)
            }

            val status = json.objOrNull("playabilityStatus")
            val code = status?.str("status").orEmpty()
            val reason = status?.str("reason")?.takeIf { it.isNotBlank() }
            val reasonLower = reason.orEmpty().lowercase()

            if (code == "OK") {
                parseStreams(videoId, json, maxHeight)?.let { return it }
                throw VideoError.Unplayable(reason)
            }

            when {
                reasonLower.isAgeRelated() || code.startsWith("AGE_") || code == "CONTENT_CHECK_REQUIRED" ->
                    throw VideoError.AgeRestricted()
                code == "LOGIN_REQUIRED" && !refreshedVisitor -> {
                    // «Confirma que no eres un bot»: casi siempre es un visitorData caducado.
                    refreshedVisitor = true
                    continue
                }
                code == "LOGIN_REQUIRED" -> {
                    backoffUntilMs = nowMs() + BACKOFF_MS
                    throw VideoError.RateLimited()
                }
                reasonLower.isRegionRelated() -> throw VideoError.RegionBlocked()
                code == "ERROR" -> throw VideoError.NotFound()
                else -> throw VideoError.Unplayable(reason)
            }
        }
        throw VideoError.RateLimited()
    }

    private fun String.isAgeRelated(): Boolean =
        contains("age") && (contains("confirm") || contains("restrict")) ||
            contains("edad") || contains("inappropriate") || contains("inapropiado")

    private fun String.isRegionRelated(): Boolean =
        contains("country") || contains("país") || contains("pais") ||
            contains("region") || contains("región")

    private suspend fun callPlayer(videoId: String, visitor: String?): JsonObject {
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", CLIENT_NAME)
                    put("clientVersion", CLIENT_VERSION)
                    put("deviceMake", DEVICE_MAKE)
                    put("deviceModel", DEVICE_MODEL)
                    put("osName", OS_NAME)
                    put("osVersion", OS_VERSION)
                    put("hl", HL)
                    put("gl", GL)
                    if (!visitor.isNullOrBlank()) put("visitorData", visitor)
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }

        val response = http.post(PLAYER_URL) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header("X-YouTube-Client-Name", CLIENT_ID)
            header("X-YouTube-Client-Version", CLIENT_VERSION)
            header(HttpHeaders.Origin, "https://www.youtube.com")
            if (!visitor.isNullOrBlank()) header("X-Goog-Visitor-Id", visitor)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val code = response.status.value
        if (code == 429) throw VideoError.RateLimited()
        if (code !in 200..299) throw IllegalStateException("player HTTP $code")
        return Json.parseToJsonElement(response.bodyAsText()) as JsonObject
    }

    /** Elige los mejores streams que respetan la calidad máxima elegida por el usuario. */
    private fun parseStreams(videoId: String, json: JsonObject, maxHeight: Int): ResolvedVideo? {
        val streaming = json.objOrNull("streamingData") ?: return null
        val details = json.objOrNull("videoDetails")
        val expiresSec = streaming.long("expiresInSeconds", DEFAULT_EXPIRES_SEC).takeIf { it > 0 } ?: DEFAULT_EXPIRES_SEC
        val hls = streaming.str("hlsManifestUrl").takeIf { it.startsWith("http") }

        var bestVideo: JsonObject? = null
        var bestVideoRank = Long.MIN_VALUE
        var bestAudio: JsonObject? = null
        var bestAudioRank = Long.MIN_VALUE
        val adaptive = streaming.arrOrNull("adaptiveFormats") ?: JsonArray(emptyList())
        for (element in adaptive) {
            val format = element as? JsonObject ?: continue
            if (!format.str("url").startsWith("http")) continue
            val mime = format.str("mimeType")
            val bitrate = format.long("bitrate", 0L)
            when {
                mime.startsWith("video/") -> {
                    val height = format.int("height", 0)
                    if (height <= 0 || height > maxHeight) continue
                    // H.264 se decodifica por hardware en todos los dispositivos; VP9 de respaldo.
                    val codecRank = when {
                        mime.contains("avc1") -> 2L
                        mime.contains("vp9") || mime.contains("vp09") -> 1L
                        else -> continue
                    }
                    val rank = height * 10_000_000L + codecRank * 1_000_000L + bitrate.coerceAtMost(999_999L)
                    if (rank > bestVideoRank) {
                        bestVideoRank = rank
                        bestVideo = format
                    }
                }
                mime.startsWith("audio/mp4") -> {
                    val rank = (if (format.int("itag") == 140) 1_000_000_000L else 0L) + bitrate
                    if (rank > bestAudioRank) {
                        bestAudioRank = rank
                        bestAudio = format
                    }
                }
            }
        }

        var muxed: JsonObject? = null
        val formats = streaming.arrOrNull("formats") ?: JsonArray(emptyList())
        for (element in formats) {
            val format = element as? JsonObject ?: continue
            if (!format.str("url").startsWith("http")) continue
            val height = format.int("height", 0)
            if (height > maxHeight) continue
            if (muxed == null || height > muxed.int("height", 0)) muxed = format
        }

        val videoUrl = bestVideo?.str("url")?.takeIf { bestAudio != null }
        val audioUrl = bestAudio?.str("url")?.takeIf { videoUrl != null }
        val muxedUrl = muxed?.str("url")
        if (hls == null && videoUrl == null && muxedUrl == null) return null

        return ResolvedVideo(
            videoId = videoId,
            hlsUrl = hls,
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            muxedUrl = muxedUrl,
            height = (bestVideo?.int("height") ?: muxed?.int("height"))?.takeIf { it > 0 },
            title = details?.str("title")?.takeIf { it.isNotBlank() },
            author = details?.str("author")?.takeIf { it.isNotBlank() },
            lengthSeconds = details?.str("lengthSeconds")?.toIntOrNull(),
            expiresAtMs = nowMs() + expiresSec * 1000L - EXPIRY_SAFETY_MS,
            source = CLIENT_NAME,
        )
    }

    // ---- visitorData ----

    private suspend fun visitorData(forceRefresh: Boolean): String? = visitorMutex.withLock {
        val now = nowMs()
        val cached = visitorCache
        if (!forceRefresh && cached != null && cached.second > now) return@withLock cached.first
        val fresh = try {
            fetchVisitorData()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (!fresh.isNullOrBlank()) {
            visitorCache = fresh to now + VISITOR_TTL_MS
            fresh
        } else {
            cached?.first
        }
    }

    private suspend fun fetchVisitorData(): String? {
        val response = http.get(VISITOR_URL) { header(HttpHeaders.UserAgent, USER_AGENT) }
        val code = response.status.value
        if (code !in 200..299) throw IllegalStateException("sw.js_data HTTP $code")
        val raw = response.bodyAsText()
        val start = raw.indexOf('[')
        if (start < 0) return null
        return Json.parseToJsonElement(raw.substring(start))
            .at(0).at(2).at(0).at(0).at(13)
            .let { it as? JsonPrimitive }
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
    }

    // ---- Emparejamiento canción → videoclip ----

    /** Carga (una vez) los emparejamientos guardados. Llamar con [mappingMutex] tomado. */
    private fun loadMappings(): MutableMap<String, MappingEntry> {
        mappings?.let { return it }
        val map = LinkedHashMap<String, MappingEntry>()
        runCatching {
            val raw = db.dezzyQueries.getKv(MAPPING_KEY).executeAsOneOrNull()
            if (!raw.isNullOrBlank()) {
                val root = Json.parseToJsonElement(raw) as JsonObject
                for ((id, value) in root) {
                    val entry = value as? JsonObject ?: continue
                    val at = (entry["at"] as? JsonPrimitive)?.longOrNull ?: continue
                    map[id] = MappingEntry(entry.str("t"), at)
                }
            }
        }
        mappings = map
        return map
    }

    private fun saveMappings(map: Map<String, MappingEntry>) {
        runCatching {
            val json = buildJsonObject {
                for ((id, entry) in map) {
                    putJsonObject(id) {
                        put("t", entry.target)
                        put("at", entry.savedAt)
                    }
                }
            }.toString()
            db.dezzyQueries.putKv(MAPPING_KEY, json)
        }
    }

    /** Devuelve el videoId emparejado, [MAPPING_NONE] si se sabe que no hay, o null si no consta. */
    private suspend fun readMapping(mediaId: String): String? = mappingMutex.withLock {
        val entry = loadMappings()[mediaId] ?: return@withLock null
        val target = entry.target
        val ttl = if (target.isEmpty()) NEGATIVE_TTL_MS else MAPPING_TTL_MS
        if (nowMs() - entry.savedAt > ttl) return@withLock null
        target.ifEmpty { MAPPING_NONE }
    }

    private suspend fun removeMapping(mediaId: String) = mappingMutex.withLock {
        val map = loadMappings()
        if (map.remove(mediaId) != null) saveMappings(map)
    }

    private suspend fun writeMapping(mediaId: String, target: String) = mappingMutex.withLock {
        val map = loadMappings()
        map.remove(mediaId)
        map[mediaId] = MappingEntry(if (target == MAPPING_NONE) "" else target, nowMs())
        if (map.size > MAPPING_MAX_ENTRIES) {
            // Poda las entradas más antiguas para que el almacén no crezca sin límite.
            map.entries
                .sortedBy { it.value.savedAt }
                .take(map.size - MAPPING_MAX_ENTRIES + MAPPING_MAX_ENTRIES / 10)
                .map { it.key }
                .forEach { map.remove(it) }
        }
        saveMappings(map)
    }

    // ---- Búsqueda y puntuación ----

    private fun normalize(s: String): String =
        s.lowercase()
            .map { c -> ACCENTS[c] ?: c }
            .filter { c -> c !in '̀'..'ͯ' }
            .joinToString("")
            .replace(NON_ALNUM, " ")
            .trim()

    private fun tokens(s: String): Set<String> =
        normalize(s).split(' ').filter { it.length > 1 || it.firstOrNull()?.isDigit() == true }.toSet()

    private fun containsPhrase(haystack: String, phrase: String): Boolean = " $haystack ".contains(" $phrase ")

    private suspend fun findMusicVideo(title: String?, artist: String?, durationSec: Int?, exclude: String): String? {
        if (title.isNullOrBlank()) return null
        val cleanTitle = NOISE.replace(title, " ").trim().ifBlank { title }
        val primaryArtist = artist?.split(ARTIST_SPLIT)?.firstOrNull { it.isNotBlank() }?.trim()
        val query = listOfNotNull(cleanTitle, primaryArtist).joinToString(" ")

        val result = client.searchVideos(query)
        result.exceptionOrNull()?.let { throw VideoError.Network(it) }
        val candidates = result.getOrNull()?.items.orEmpty().take(10)

        val wantTitle = tokens(cleanTitle)
        if (wantTitle.isEmpty()) return null
        val wantArtist = primaryArtist?.let { tokens(it) }.orEmpty()
        val songTitleNorm = normalize(title)

        return candidates
            .asSequence()
            .filter { it.videoId != null && it.videoId != exclude }
            .map { item -> item to score(item, wantTitle, wantArtist, songTitleNorm, durationSec) }
            .maxByOrNull { it.second }
            ?.takeIf { it.second >= MIN_MATCH_SCORE }
            ?.first
            ?.videoId
    }

    private fun score(
        item: Track,
        wantTitle: Set<String>,
        wantArtist: Set<String>,
        songTitleNorm: String,
        durationSec: Int?,
    ): Double {
        val candTitleNorm = normalize(item.title)
        val candTitle = tokens(item.title)
        val candArtist = tokens(item.artists)

        val titleOverlap = wantTitle.count { it in candTitle }.toDouble() / wantTitle.size
        if (titleOverlap < 0.6) return Double.NEGATIVE_INFINITY
        var score = titleOverlap * 4.0

        if (wantArtist.isNotEmpty()) {
            val needed = ceil(wantArtist.size * 0.6).toInt().coerceAtLeast(1)
            val hits = wantArtist.count { it in candArtist || it in candTitle }
            score += if (hits >= needed) 3.0 else -2.0
        }

        val candDuration = item.durationSeconds?.toInt()
        if (durationSec != null && durationSec > 0 && candDuration != null && candDuration > 0) {
            val diff = abs(candDuration - durationSec)
            score += when {
                diff <= 10 -> 2.0
                diff <= 40 -> 1.0
                diff > 150 -> -3.0
                else -> 0.0
            }
        }

        PENALIZED.forEach { word ->
            if (containsPhrase(candTitleNorm, word) && !containsPhrase(songTitleNorm, word)) score -= 3.0
        }
        if (OFFICIAL.any { containsPhrase(candTitleNorm, it) }) {
            score += 1.5
        } else if (LYRIC.any { containsPhrase(candTitleNorm, it) && !containsPhrase(songTitleNorm, it) }) {
            score -= 1.0
        }
        return score
    }
}
