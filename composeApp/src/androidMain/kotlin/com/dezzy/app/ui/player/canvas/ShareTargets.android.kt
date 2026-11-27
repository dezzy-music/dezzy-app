/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.canvas

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.dezzy.app.core.net.HttpClientFactory
import com.dezzy.app.feature.downloads.DownloadManager
import com.dezzy.app.feature.player.StreamDataSource
import com.dezzy.app.feature.video.MusicVideoCache
import com.dezzy.app.source.stream.AudioQuality
import com.dezzy.app.source.stream.StreamUrlResolver
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koin.mp.KoinPlatform
import java.io.File
import kotlin.coroutines.resume

private const val INSTAGRAM = "com.instagram.android"
private const val WHATSAPP = "com.whatsapp"
private const val STORY_ACTION = "com.instagram.share.ADD_TO_STORY"

private fun appContext(): Context = KoinPlatform.getKoin().get<Context>()

actual fun sharePngTo(png: ByteArray, fileName: String, text: String?, target: ShareTarget) {
    val context = appContext()
    val uri = insertPng(context, png, fileName) ?: return
    shareMedia(context, uri, "image/png", text, target)
}

actual fun shareVideoTo(uri: String, text: String?, target: ShareTarget) {
    shareMedia(appContext(), Uri.parse(uri), "video/mp4", text, target)
}

/** Historias de Instagram o WhatsApp si están instalados; si no, el menú de compartir del sistema. */
private fun shareMedia(context: Context, uri: Uri, mime: String, text: String?, target: ShareTarget) {
    fun chooser() {
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (!text.isNullOrBlank()) send.putExtra(Intent.EXTRA_TEXT, text)
        runCatching {
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    val direct = when (target) {
        ShareTarget.Chooser -> null
        ShareTarget.Stories -> Intent(STORY_ACTION)
            .setDataAndType(uri, mime)
            .setPackage(INSTAGRAM)
            .putExtra("source_application", context.packageName)
        ShareTarget.WhatsApp -> Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .apply { if (!text.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, text) }
            .setPackage(WHATSAPP)
    }
    if (direct == null) return chooser()
    direct.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    if (target == ShareTarget.Stories) context.grantUriPermission(INSTAGRAM, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(direct)
    } catch (e: ActivityNotFoundException) {
        chooser()
    } catch (e: SecurityException) {
        chooser()
    }
}

// ── Vídeo del Canvas ────────────────────────────────────────────────────────────────────────

actual val canvasExportSupported: Boolean = true

private const val OUT_W = 720
private const val OUT_H = 1280

/** Escala y desplazamiento (coordenadas de -1 a 1, y hacia arriba) del vídeo dentro de la tarjeta. */
private const val VIDEO_SCALE = 0.72f
private const val VIDEO_SHIFT_Y = 0.12f

@OptIn(UnstableApi::class)
actual suspend fun exportCanvasVideo(request: CanvasExportRequest): String? {
    val context = appContext()
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val out = File(dir, "canvas.mp4")
    out.delete()
    val frame = if (request.branded) withContext(Dispatchers.Default) { frameBitmap(request) } else null
    // Primero con el audio de la canción; si falla (sin audio o fuente rara), sólo el vídeo en bucle.
    var ok = request.videoId != null && runTransform(context, request, frame, out, withAudio = true)
    if (!ok) {
        out.delete()
        ok = runTransform(context, request, frame, out, withAudio = false)
    }
    frame?.recycle()
    if (!ok || !out.exists() || out.length() == 0L) return null
    return withContext(Dispatchers.IO) { insertVideo(context, out, request.fileName)?.toString() }
}

@OptIn(UnstableApi::class)
private suspend fun runTransform(
    context: Context,
    request: CanvasExportRequest,
    frame: Bitmap?,
    out: File,
    withAudio: Boolean,
): Boolean = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { cont ->
        val transformer = try {
            Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setAssetLoaderFactory(
                    @Suppress("DEPRECATION")
                    DefaultAssetLoaderFactory(
                        context,
                        DefaultDecoderFactory(context),
                        Clock.DEFAULT,
                        DefaultMediaSourceFactory(routingFactory(context)),
                        DataSourceBitmapLoader(context),
                    ),
                )
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        if (cont.isActive) cont.resume(false)
                    }
                })
                .build()
        } catch (e: Exception) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }
        try {
            transformer.start(composition(request, frame, withAudio), out.absolutePath)
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(false)
            return@suspendCancellableCoroutine
        }
        cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { runCatching { transformer.cancel() } } }
    }
}

@OptIn(UnstableApi::class)
private fun composition(request: CanvasExportRequest, frame: Bitmap?, withAudio: Boolean): Composition {
    val clip = MediaItem.ClippingConfiguration.Builder()
        .setStartPositionMs(request.loopStartMs)
        .apply { if (request.loopLengthMs > 0) setEndPositionMs(request.loopStartMs + request.loopLengthMs) }
        .build()
    val videoItem = MediaItem.Builder().setUri(request.videoUrl).setClippingConfiguration(clip).build()
    val effects = if (frame != null) {
        listOf(
            // Cuadrado del centro → ancho completo de la Historia → encogido y subido dentro de la tarjeta.
            Presentation.createForAspectRatio(1f, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP),
            Presentation.createForWidthAndHeight(OUT_W, OUT_H, Presentation.LAYOUT_SCALE_TO_FIT),
            MatrixTransformation { _ ->
                Matrix().apply {
                    setScale(VIDEO_SCALE, VIDEO_SCALE)
                    postTranslate(0f, VIDEO_SHIFT_Y)
                }
            },
            OverlayEffect(ImmutableList.of<TextureOverlay>(BitmapOverlay.createStaticBitmapOverlay(frame))),
        )
    } else {
        listOf(Presentation.createForWidthAndHeight(OUT_W, OUT_H, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
    }
    val video = EditedMediaItem.Builder(videoItem)
        .setRemoveAudio(true)
        .setEffects(Effects(emptyList(), effects))
        .build()
    if (!withAudio || request.videoId == null) {
        // Sin audio: el bucle se repite a mano hasta cubrir la duración pedida.
        val loopMs = request.loopLengthMs.takeIf { it > 0 } ?: 8_000L
        val times = ((request.durationMs + loopMs - 1) / loopMs).toInt().coerceIn(1, 60)
        return Composition.Builder(EditedMediaItemSequence(List(times) { video })).build()
    }
    val audioItem = MediaItem.Builder()
        .setUri(StreamDataSource.uriFor(request.videoId))
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(request.audioStartMs)
                .setEndPositionMs(request.audioStartMs + request.durationMs)
                .build(),
        )
        .build()
    val audio = EditedMediaItem.Builder(audioItem).setRemoveVideo(true).build()
    // El audio (sin bucle) marca la duración; el vídeo se repite hasta cubrirla.
    return Composition.Builder(
        EditedMediaItemSequence(listOf(video), true),
        EditedMediaItemSequence(listOf(audio), false),
    ).build()
}

/** Fuente de datos: «dezzy://stream/…» va por el resolvedor de audio; lo demás, por la del vídeo. */
@OptIn(UnstableApi::class)
private fun routingFactory(context: Context): DataSource.Factory {
    val koin = KoinPlatform.getKoin()
    val resolver = koin.get<StreamUrlResolver>()
    val http = OkHttpDataSource.Factory(OkHttpClient()).setUserAgent(HttpClientFactory.DEFAULT_USER_AGENT)
    val audio = StreamDataSource.Factory(
        resolver = resolver,
        upstream = http,
        quality = { AudioQuality.HIGH },
        localFile = { id -> koin.getOrNull<DownloadManager>()?.localFile(id) },
    )
    val video = MusicVideoCache.dataSourceFactory(context)
    return DataSource.Factory { RoutingDataSource(audio.createDataSource(), video.createDataSource()) }
}

@OptIn(UnstableApi::class)
private class RoutingDataSource(private val audio: DataSource, private val video: DataSource) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        audio.addTransferListener(transferListener)
        video.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (dataSpec.uri.scheme == StreamDataSource.SCHEME) audio else video
        active = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        active?.read(buffer, offset, length) ?: error("no abierto")

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        val current = active
        active = null
        current?.close()
    }
}

/** Marco de la tarjeta (720×1280) con un hueco transparente donde se ve el vídeo. */
private fun frameBitmap(request: CanvasExportRequest): Bitmap {
    val bitmap = Bitmap.createBitmap(OUT_W, OUT_H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val color = request.frameColor or 0xFF000000.toInt()
    if (request.gradient) {
        paint.shader = LinearGradient(0f, 0f, 0f, OUT_H.toFloat(), color, 0xFF000000.toInt(), Shader.TileMode.CLAMP)
    } else {
        paint.color = color
    }
    canvas.drawRect(0f, 0f, OUT_W.toFloat(), OUT_H.toFloat(), paint)
    paint.shader = null

    // Hueco: el mismo rectángulo en el que acaba el vídeo tras las transformaciones.
    val halfW = VIDEO_SCALE
    val halfH = VIDEO_SCALE * OUT_W / OUT_H
    val hole = RectF(
        (1f - halfW) / 2f * OUT_W,
        (1f - (halfH + VIDEO_SHIFT_Y)) / 2f * OUT_H,
        (1f + halfW) / 2f * OUT_W,
        (1f + halfH - VIDEO_SHIFT_Y) / 2f * OUT_H,
    )
    val pad = 40f
    val card = RectF(hole.left - pad, hole.top - pad, hole.right + pad, hole.bottom + 210f)
    paint.color = 0xFF0C0C0C.toInt()
    canvas.drawRoundRect(card, 28f, 28f, paint)

    val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    canvas.drawRoundRect(hole, 16f, 16f, clear)

    val maxText = card.width() - 2 * pad
    val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt()
        textSize = 40f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFA9A9A9.toInt()
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val cx = card.centerX()
    canvas.drawText(TextUtils.ellipsize(request.title, title, maxText, TextUtils.TruncateAt.END).toString(), cx, hole.bottom + 70f, title)
    canvas.drawText(TextUtils.ellipsize(request.artist, sub, maxText, TextUtils.TruncateAt.END).toString(), cx, hole.bottom + 112f, sub)
    title.textSize = 30f
    canvas.drawText("★ Dezzy", cx, hole.bottom + 175f, title)
    return bitmap
}

private fun insertVideo(context: Context, file: File, fileName: String): Uri? {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Dezzy")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
    }
    val uri = runCatching {
        resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
    }.getOrNull() ?: return null
    return try {
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("sin salida")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        }
        uri
    } catch (e: Exception) {
        runCatching { resolver.delete(uri, null, null) }
        null
    } finally {
        file.delete()
    }
}
