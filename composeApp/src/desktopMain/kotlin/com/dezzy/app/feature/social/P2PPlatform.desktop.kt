/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.koin.core.scope.Scope
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.util.Base64
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

actual fun createP2PImageTools(scope: Scope): P2PImageTools = DesktopP2PImageTools

actual fun createP2PNotifier(scope: Scope): P2PNotifier = DesktopP2PNotifier

actual fun createP2PBle(scope: Scope): P2PBle = DesktopP2PBle

actual fun p2pDeviceName(): String =
    runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() }?.take(60) ?: "Escritorio"

private object DesktopP2PImageTools : P2PImageTools {

    private fun read(source: String): BufferedImage? = runCatching {
        val file = if (source.startsWith("file:")) File(URI(source)) else File(source)
        ImageIO.read(file)
    }.getOrNull()

    private fun read(bytes: ByteArray): BufferedImage? = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()

    private fun resize(src: BufferedImage, w: Int, h: Int): BufferedImage {
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(src, 0, 0, w, h, java.awt.Color.WHITE, null)
        g.dispose()
        return out
    }

    private fun jpeg(image: BufferedImage, quality: Float): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val params = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = quality
        }
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { ios ->
            writer.output = ios
            writer.write(null, IIOImage(image, null, null), params)
        }
        writer.dispose()
        return out.toByteArray()
    }

    private fun avatar(src: BufferedImage, size: Int): String {
        val side = minOf(src.width, src.height)
        val square = src.getSubimage((src.width - side) / 2, (src.height - side) / 2, side, side)
        val bytes = jpeg(resize(square, size, size), 0.8f)
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes)
    }

    private fun photo(src: BufferedImage, maxSide: Int): ByteArray {
        val scale = minOf(1.0, maxSide.toDouble() / maxOf(src.width, src.height))
        val scaled = if (scale < 1.0) resize(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1)) else resize(src, src.width, src.height)
        return jpeg(scaled, 0.82f)
    }

    override suspend fun avatarDataUri(source: String, size: Int): String? = withContext(Dispatchers.IO) {
        runCatching { read(source)?.let { avatar(it, size) } }.getOrNull()
    }

    override suspend fun avatarDataUri(bytes: ByteArray, size: Int): String? = withContext(Dispatchers.IO) {
        runCatching { read(bytes)?.let { avatar(it, size) } }.getOrNull()
    }

    override suspend fun compressPhoto(source: String, maxSide: Int): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { read(source)?.let { photo(it, maxSide) } }.getOrNull()
    }

    override suspend fun compressPhoto(bytes: ByteArray, maxSide: Int): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { read(bytes)?.let { photo(it, maxSide) } }.getOrNull()
    }

    override suspend fun prepareBannerVideo(source: String): Result<P2PBannerVideo> =
        Result.failure(UnsupportedOperationException("Las portadas de video se ponen desde el móvil."))
}

private object DesktopP2PNotifier : P2PNotifier {
    override fun notifyMessage(peer: String, name: String, text: String, songTitle: String?, createdAtSec: Long) = Unit

    override fun notifyFollowRequest(peer: String, name: String) = Unit

    override fun clearChat(peer: String) = Unit

    override fun clearAll() = Unit
}

private object DesktopP2PBle : P2PBle {
    private val _status = MutableStateFlow(NearbyStatus.UNSUPPORTED)
    override val status: StateFlow<NearbyStatus> = _status.asStateFlow()

    override fun requiredPermissions(): List<String> = emptyList()

    override fun hasPermissions(): Boolean = true

    override fun start(myPubkey: String, advertise: Boolean, onFound: (pubkey: String, rssi: Int) -> Unit) = Unit

    override fun stop() = Unit
}
