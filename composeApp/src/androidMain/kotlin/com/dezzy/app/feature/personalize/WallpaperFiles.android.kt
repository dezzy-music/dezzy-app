/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.personalize

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform
import java.io.File

private class AndroidWallpaperFiles(private val context: Context) :
    JvmWallpaperFiles(File(context.filesDir, "wallpapers")) {

    override suspend fun importPicked(uri: String, ext: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dest = newFile(ext)
            val input = context.contentResolver.openInputStream(Uri.parse(uri)) ?: return@runCatching null
            input.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            uriOf(dest)
        }.getOrNull()
    }

    /** Cuenta la rotación del vídeo y la orientación EXIF de la foto. Cuadrado = vertical. */
    override fun isLandscape(uri: String, isVideo: Boolean): Boolean {
        val file = fileOf(uri) ?: return false
        return try {
            if (isVideo) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(file.absolutePath)
                    val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    if (rot % 180 != 0) h > w else w > h
                } finally {
                    r.release()
                }
            } else {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, o)
                val turned = try {
                    when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
                        ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE -> true
                        else -> false
                    }
                } catch (e: Exception) {
                    false
                }
                if (turned) o.outHeight > o.outWidth else o.outWidth > o.outHeight
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

private val instance: WallpaperFiles by lazy { AndroidWallpaperFiles(KoinPlatform.getKoin().get<Context>()) }

actual fun wallpaperFiles(): WallpaperFiles = instance
