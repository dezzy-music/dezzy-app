/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.personalize

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.takeFrom
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException

/** Resultado de bajar un pin: el archivo ya está en la carpeta privada de la app. */
sealed interface PinterestResult {
    /** [isLandscape]: más ancho que alto (ya contando la rotación del vídeo o el EXIF de la foto). */
    data class Ok(val uri: String, val isVideo: Boolean, val title: String, val isLandscape: Boolean) : PinterestResult
    data class Error(val message: String) : PinterestResult
}

/**
 * Fondos desde Pinterest (mismo método que Dezzy Desktop).
 *
 * Acepta pin.it/…, pinterest.<país>/pin/… y enlaces directos a i.pinimg.com / v1.pinimg.com.
 * Cada salto (redirecciones incluidas) se comprueba contra la lista de hosts de Pinterest, y cada
 * descarga tiene tiempo límite, tipo comprobado (cabecera + firma de los primeros bytes) y tope de tamaño.
 * El archivo se guarda en la carpeta privada de la app, así que no hace falta ningún permiso.
 */
class PinterestWallpaper(
    client: HttpClient,
    private val files: WallpaperFiles = wallpaperFiles(),
) {
    private companion object {
        const val PAGE_TIMEOUT_MS = 15_000L
        const val MEDIA_TIMEOUT_MS = 90_000L
        const val PAGE_MAX = 8L * 1024 * 1024
        const val IMAGE_MAX = 40L * 1024 * 1024
        const val VIDEO_MAX = 300L * 1024 * 1024
        const val MAX_HOPS = 5
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        val IMAGE_TYPES = mapOf(
            "image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp", "image/gif" to "gif", "image/avif" to "avif",
        )
        val VIDEO_TYPES = mapOf("video/mp4" to "mp4", "video/webm" to "webm")

        val PINTEREST_HOST = Regex("""^(?:[a-z0-9-]+\.)?pinterest\.(?:com|[a-z]{2}|co\.[a-z]{2}|com\.[a-z]{2})$""")
        val PINIMG_HOST = Regex("""^(?:i|v|v1|v2)\.pinimg\.com$""")
    }

    private object Err {
        const val INVALID = "Ese enlace no es de un pin de Pinterest. Copia el enlace del pin (pin.it/… o pinterest.com/pin/…)."
        const val GONE = "No se encuentra ese pin: puede ser privado o haberse borrado."
        const val OFFLINE = "Sin conexión. Prueba otra vez cuando vuelva internet."
        const val TIMEOUT = "Pinterest tarda demasiado en responder. Prueba otra vez."
        const val BIG = "La imagen o el vídeo del pin pesa demasiado."
        const val TYPE = "Ese pin no tiene una imagen ni un vídeo que Dezzy pueda usar."
        const val FAIL = "No se pudo bajar el pin. Prueba otra vez en un rato."
    }

    private class PinError(message: String) : Exception(message)

    /** Sin redirecciones automáticas: cada salto se valida a mano. */
    private val http: HttpClient = client.config { followRedirects = false }

    private fun isPinterestHost(h: String) = h == "pin.it" || PINTEREST_HOST.matches(h)
    private fun isPinimgHost(h: String) = PINIMG_HOST.matches(h)

    /** ¿Lo pegado parece de Pinterest? Sirve para mostrar el logo junto al campo. */
    fun isPinterestUrl(raw: String): Boolean {
        val u = parse(raw) ?: return false
        val host = u.host.lowercase()
        return isPinterestHost(host) || isPinimgHost(host)
    }

    private fun parse(raw: String): Url? {
        var s = raw.trim()
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        if (!Regex("^[a-zA-Z]+://").containsMatchIn(s)) s = "https://$s" // «pin.it/abc» sin https:// también vale
        val u = runCatching { Url(s) }.getOrNull() ?: return null
        if (!u.user.isNullOrEmpty() || !u.password.isNullOrEmpty()) return null
        if (u.port != 80 && u.port != 443) return null
        return URLBuilder(u).apply {
            protocol = URLProtocol.HTTPS
            port = 443
        }.build()
    }

    private fun pinId(u: Url): String? =
        Regex("""/pin/(?:[^/]*--)?(\d{5,22})(?:/|$)""").find(u.encodedPath)?.groupValues?.get(1)

    private sealed interface Hop<out T> {
        data class Next(val url: Url) : Hop<Nothing>
        data class Done<T>(val value: T) : Hop<T>
    }

    private fun isTimeout(e: Throwable) = e::class.simpleName?.contains("Timeout", ignoreCase = true) == true

    /** Petición con redirecciones a mano: cada salto tiene que seguir en hosts permitidos. */
    private suspend fun <T> request(
        start: Url,
        allow: (String) -> Boolean,
        timeoutMs: Long,
        accept: String,
        block: suspend (HttpResponse, Url) -> T,
    ): T {
        var url = start
        repeat(MAX_HOPS + 1) {
            if (url.protocol != URLProtocol.HTTPS || !allow(url.host.lowercase())) throw PinError(Err.INVALID)
            val current = url
            val hop: Hop<T> = try {
                http.prepareGet(current) {
                    header(HttpHeaders.UserAgent, UA)
                    header(HttpHeaders.Accept, accept)
                    header(HttpHeaders.AcceptLanguage, "es-ES,es;q=0.9,en;q=0.8")
                    timeout {
                        requestTimeoutMillis = timeoutMs
                        connectTimeoutMillis = PAGE_TIMEOUT_MS
                        socketTimeoutMillis = 30_000L
                    }
                }.execute { res ->
                    if (res.status.value in 300..399) {
                        val loc = res.headers[HttpHeaders.Location] ?: throw PinError(Err.FAIL)
                        Hop.Next(URLBuilder(current).takeFrom(loc).build())
                    } else {
                        Hop.Done(block(res, current))
                    }
                }
            } catch (e: PinError) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw PinError(
                    when {
                        isTimeout(e) -> if (files.isOnline()) Err.TIMEOUT else Err.OFFLINE
                        else -> if (files.isOnline()) Err.FAIL else Err.OFFLINE
                    },
                )
            }
            when (hop) {
                is Hop.Next -> url = hop.url
                is Hop.Done -> return hop.value
            }
        }
        throw PinError(Err.FAIL)
    }

    /** Lee el cuerpo como texto sin pasar de `max` bytes. */
    private suspend fun readText(res: HttpResponse, max: Long): String {
        val declared = res.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declared != null && declared > max) throw PinError(Err.FAIL)
        val ch = res.bodyAsChannel()
        var data = ByteArray(64 * 1024)
        var size = 0
        while (true) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            val n = ch.readAvailable(data, size, data.size - size)
            if (n < 0) break
            size += n
            if (size > max) throw PinError(Err.FAIL)
        }
        return data.decodeToString(0, size)
    }

    /** Firma de los primeros bytes: que el archivo sea de verdad lo que dice la cabecera. */
    private fun sniff(b: ByteArray): String? {
        if (b.size < 12) return null
        fun ascii(from: Int, to: Int) = buildString { for (i in from until to) append((b[i].toInt() and 0xff).toChar()) }
        val u = { i: Int -> b[i].toInt() and 0xff }
        return when {
            u(0) == 0xff && u(1) == 0xd8 && u(2) == 0xff -> "jpg"
            u(0) == 0x89 && ascii(1, 4) == "PNG" -> "png"
            ascii(0, 4) == "GIF8" -> "gif"
            ascii(0, 4) == "RIFF" && ascii(8, 12) == "WEBP" -> "webp"
            ascii(4, 8) == "ftyp" -> if (Regex("^avi[fs]").containsMatchIn(ascii(8, 12))) "avif" else "mp4"
            u(0) == 0x1a && u(1) == 0x45 && u(2) == 0xdf && u(3) == 0xa3 -> "webm"
            else -> null
        }
    }

    private fun unescapeJson(s: String) = s.replace(Regex("""\\u002F""", RegexOption.IGNORE_CASE), "/").replace("\\/", "/")

    private data class PinMedia(val video: String?, val image: String?, val title: String)

    /** Saca del HTML del pin el mejor medio del propio pin (no de los relacionados que vienen debajo). */
    private fun pickFromPage(html: String, id: String): PinMedia? {
        // Bloque de datos del pin pedido: Pinterest lo manda como respuesta Relay de «v3GetPinQueryv2».
        val at = html.indexOf("\"v3GetPinQueryv2\"")
        val end = if (at < 0) -1 else html.indexOf("</script>", at)
        val raw = if (at < 0) "" else unescapeJson(html.substring(at, if (end < 0) minOf(html.length, at + 200_000) else end))
        // Sólo vale si es el pin pedido; si no, se tira de og:image, que siempre es el del pin.
        val block = if (raw.contains("\"entityId\":\"$id\"")) raw else ""
        val title = (
            Regex("\"seoTitle\":\"([^\"]{1,200})\"").find(block)?.groupValues?.get(1)
                ?: Regex("<meta content=\"([^\"]{1,200})\"[^>]*og:title\"").find(html)?.groupValues?.get(1)
                ?: ""
            )
            .replace(Regex("""\\u([0-9a-fA-F]{4})""")) { it.groupValues[1].toInt(16).toChar().toString() }
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")

        var video: String? = null
        if (block.isNotEmpty()) {
            // Pin de vídeo normal: el MP4 de 720p es H.264 (los «hevcMp4» no se ven en todos los móviles).
            video = Regex(""""v720P":\{[^}]*?"url":"(https://v1\.pinimg\.com/[^"]+\.mp4)"""").find(block)?.groupValues?.get(1)
                ?: Regex(""""(https://v1\.pinimg\.com/videos/mc/[^"]+\.mp4)"""").find(block)?.groupValues?.get(1)
                // Pin de idea (storyPinData): sólo trae HLS; el MP4 se saca luego de su lista maestra.
                ?: Regex(""""(https://v1\.pinimg\.com/videos/iht/hls/[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{32}\.m3u8)"""")
                    .find(block)?.groupValues?.get(1)
        }
        val og = ogImage(html)
        val sig = Regex("\"imageSignature\":\"([0-9a-f]{32})\"").find(block)?.groupValues?.get(1)
            ?: Regex("""i\.pinimg\.com/\w+/[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{2}/([0-9a-f]{32})""").find(og ?: "")?.groupValues?.get(1)
        var image = sig?.let {
            Regex("""https://i\.pinimg\.com/originals/[0-9a-f/]{9}$it\.(jpg|jpeg|png|webp|gif)""").find(unescapeJson(html))?.value
        }
        if (image == null) image = og
        return if (video != null || image != null) PinMedia(video, image, title) else null
    }

    private fun ogImage(html: String): String? =
        (
            Regex("""<meta[^>]+content="(https://i\.pinimg\.com/[^"]+)"[^>]*property="og:image"""").find(html)
                ?: Regex("""<meta[^>]+property="og:image"[^>]*content="(https://i\.pinimg\.com/[^"]+)"""").find(html)
            )?.groupValues?.get(1)

    /** Para un enlace directo a i.pinimg.com: prueba antes el original a toda resolución. */
    private fun originalsFor(u: Url): List<Url> {
        val m = Regex("""^/[^/]+/([0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{32})\.(\w+)$""").find(u.encodedPath)
        if (m == null || u.encodedPath.startsWith("/originals/")) return listOf(u)
        val exts = listOf(m.groupValues[2], "jpg", "png", "webp", "gif").distinct()
        return exts.mapNotNull { runCatching { Url("https://i.pinimg.com/originals/${m.groupValues[1]}.$it") }.getOrNull() } + u
    }

    /** Busca el medio del pin. No descarga nada todavía. */
    private suspend fun resolvePin(raw: String): PinMedia {
        val start = parse(raw)
        val startHost = start?.host?.lowercase()
        if (start == null || startHost == null || !(isPinterestHost(startHost) || isPinimgHost(startHost))) throw PinError(Err.INVALID)
        if (isPinimgHost(startHost)) {
            return if (startHost == "i.pinimg.com") PinMedia(null, start.toString(), "") else PinMedia(start.toString(), null, "")
        }
        var url = start
        if (startHost == "pin.it") {
            // El acortador lleva a api.pinterest.com y de ahí al pin. Se sigue con la misma lista de hosts.
            val (code, landed) = request(start, ::isPinterestHost, PAGE_TIMEOUT_MS, "text/html") { res, at ->
                res.status.value to at
            }
            if (code == 404) throw PinError(Err.GONE)
            url = landed
        }
        val id = pinId(url) ?: throw PinError(if (startHost == "pin.it") Err.GONE else Err.INVALID)
        val page = Url("https://www.pinterest.com/pin/$id/")
        val html = request(page, ::isPinterestHost, PAGE_TIMEOUT_MS, "text/html") { res, _ ->
            val code = res.status.value
            if (code == 404 || code == 410) throw PinError(Err.GONE)
            if (code !in 200..299) throw PinError(Err.FAIL)
            readText(res, PAGE_MAX)
        }
        return pickFromPage(html, id) ?: throw PinError(Err.GONE)
    }

    /**
     * HLS de un pin de idea → MP4 H.264 equivalentes, del más ancho al más estrecho (hasta 1920 px).
     * Pinterest publica cada ancho de la lista maestra también como `iht/expMp4/<hash>_<ancho>w.mp4`.
     */
    private suspend fun mp4sFromHls(m3u8: Url): List<Url> {
        val m = Regex("""^/videos/iht/hls/([0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{32})\.m3u8$""").find(m3u8.encodedPath)
            ?: return emptyList()
        var widths = emptyList<Int>()
        try {
            widths = request(m3u8, ::isPinimgHost, PAGE_TIMEOUT_MS, "application/vnd.apple.mpegurl,*/*") { res, _ ->
                if (res.status.value in 200..299) {
                    Regex("""_(\d{3,4})w\.m3u8""").findAll(readText(res, 256 * 1024)).map { it.groupValues[1].toInt() }.toList()
                } else {
                    emptyList()
                }
            }
        } catch (e: PinError) {
            if (e.message == Err.OFFLINE) throw e
        }
        return (widths.filter { it <= 1920 } + 720).distinct().sortedDescending()
            .mapNotNull { runCatching { Url("https://v1.pinimg.com/videos/iht/expMp4/${m.groupValues[1]}_${it}w.mp4") }.getOrNull() }
    }

    /** Baja directo a un archivo (los vídeos pueden pesar mucho), con tope y firma comprobada. */
    private suspend fun download(u: Url, wantVideo: Boolean): Pair<String, Boolean> =
        request(u, ::isPinimgHost, MEDIA_TIMEOUT_MS, if (wantVideo) "video/*" else "image/avif,image/webp,image/*") { r, _ ->
            val code = r.status.value
            if (code == 403 || code == 404) throw PinError(Err.GONE)
            if (code !in 200..299) throw PinError(Err.FAIL)
            val type = (r.headers[HttpHeaders.ContentType] ?: "").substringBefore(';').trim().lowercase()
            val isVideo = type in VIDEO_TYPES
            if (type !in IMAGE_TYPES && !isVideo) throw PinError(Err.TYPE)
            val max = if (isVideo) VIDEO_MAX else IMAGE_MAX
            val declared = r.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (declared != null && declared > max) throw PinError(Err.BIG)

            val sink = files.openSink()
            try {
                var total = 0L
                val head = ByteArray(16)
                var headLen = 0
                val ch = r.bodyAsChannel()
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = ch.readAvailable(buf, 0, buf.size)
                    if (n < 0) break
                    if (n == 0) continue
                    if (headLen < head.size) {
                        val c = minOf(n, head.size - headLen)
                        buf.copyInto(head, headLen, 0, c)
                        headLen += c
                    }
                    total += n
                    if (total > max) throw PinError(Err.BIG)
                    sink.write(buf, n)
                }
                // La firma manda: un .jpg que en realidad es WebP se guarda como .webp; algo que no es imagen/vídeo, fuera.
                val real = sniff(head.copyOf(headLen))
                val allowed = if (isVideo) VIDEO_TYPES.values else IMAGE_TYPES.values
                if (real == null || real !in allowed) throw PinError(Err.TYPE)
                sink.finish(real) to isVideo
            } catch (e: Throwable) {
                sink.abort()
                throw e
            }
        }

    /** Enlace de Pinterest → archivo listo para usar como fondo. Si el vídeo no se puede bajar, queda la imagen. */
    suspend fun fetch(raw: String): PinterestResult = try {
        val media = resolvePin(raw)
        val groups = buildList<Pair<Boolean, suspend () -> List<Url>>> {
            media.video?.let { runCatching { Url(it) }.getOrNull() }?.let { v ->
                val urls: suspend () -> List<Url> = { if (v.encodedPath.endsWith(".m3u8")) mp4sFromHls(v) else listOf(v) }
                add(true to urls)
            }
            media.image?.let { runCatching { Url(it) }.getOrNull() }?.let { i ->
                val urls: suspend () -> List<Url> = { originalsFor(i) }
                add(false to urls)
            }
        }
        var last: Exception? = null
        var result: PinterestResult? = null
        search@ for ((wantVideo, urls) in groups) {
            for (u in urls()) {
                try {
                    val (uri, isVideo) = download(u, wantVideo)
                    result = PinterestResult.Ok(uri, isVideo, media.title.trim(), files.isLandscape(uri, isVideo))
                    break@search
                } catch (e: PinError) {
                    last = e
                    if (e.message == Err.OFFLINE) throw e
                    // «No existe esa resolución» → se prueba la siguiente; otro fallo → se pasa al siguiente grupo.
                    if (e.message != Err.GONE) break
                }
            }
        }
        result ?: throw (last ?: PinError(Err.FAIL))
    } catch (e: PinError) {
        PinterestResult.Error(e.message ?: Err.FAIL)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PinterestResult.Error(Err.FAIL)
    }
}
