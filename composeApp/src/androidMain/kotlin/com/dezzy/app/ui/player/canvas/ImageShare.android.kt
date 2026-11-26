/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.canvas

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import org.koin.mp.KoinPlatform
import java.io.ByteArrayOutputStream

actual fun encodePng(image: ImageBitmap): ByteArray = runCatching {
    ByteArrayOutputStream().use { out ->
        image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }
}.getOrDefault(ByteArray(0))

/**
 * La app no declara un FileProvider, así que la imagen se guarda en Imágenes/Dezzy (MediaStore) y se
 * comparte ese content://. Por eso compartir también deja una copia en la galería.
 */
actual fun shareImage(png: ByteArray, fileName: String, text: String?) {
    val context = KoinPlatform.getKoin().get<Context>()
    val uri = insertPng(context, png, fileName) ?: return
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if (!text.isNullOrBlank()) send.putExtra(Intent.EXTRA_TEXT, text)
    runCatching {
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

actual fun saveImage(png: ByteArray, fileName: String): Boolean {
    val context = KoinPlatform.getKoin().get<Context>()
    return insertPng(context, png, fileName) != null
}

private fun insertPng(context: Context, png: ByteArray, fileName: String): Uri? {
    if (png.isEmpty()) return null
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Dezzy")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = runCatching {
        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }.getOrNull() ?: return null
    return try {
        resolver.openOutputStream(uri)?.use { it.write(png) } ?: error("sin salida")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
        }
        uri
    } catch (e: Exception) {
        runCatching { resolver.delete(uri, null, null) }
        null
    }
}
