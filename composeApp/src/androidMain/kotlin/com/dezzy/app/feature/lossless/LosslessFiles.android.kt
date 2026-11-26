/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.lossless

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

actual fun readFileHead(path: String, max: Int): ByteArray? = try {
    val file = File(path)
    if (!file.isFile) null
    else RandomAccessFile(file, "r").use { raf ->
        val buf = ByteArray(minOf(max.toLong(), raf.length()).toInt())
        raf.readFully(buf)
        buf
    }
} catch (e: Exception) {
    null
}

actual fun replaceFileHead(path: String, oldHeadLength: Int, newHead: ByteArray): Boolean {
    val file = File(path)
    val tmp = File(file.parentFile, file.name + ".tag")
    return try {
        FileInputStream(file).use { input ->
            input.channel.position(oldHeadLength.toLong())
            tmp.outputStream().buffered().use { out ->
                out.write(newHead)
                input.copyTo(out)
            }
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) { tmp.delete(); return false }
        }
        true
    } catch (e: Exception) {
        tmp.delete()
        false
    }
}

/** Copia a `Música/Dezzy` con MediaStore (Android 10+; antes haría falta permiso de almacenamiento y no se hace). */
actual class PublicMusicCopier(private val context: Context) {
    actual suspend fun copy(path: String, displayName: String, mime: String): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
        val source = File(path)
        if (!source.isFile) return@withContext false
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        try {
            val exists = resolver.query(
                collection,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Audio.Media.DISPLAY_NAME} = ?",
                arrayOf("$RELATIVE_PATH%", displayName),
                null,
            )?.use { it.moveToFirst() } == true
            if (exists) return@withContext true

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: return@withContext false
            try {
                resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                    ?: run { resolver.delete(uri, null, null); return@withContext false }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                true
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        const val RELATIVE_PATH = "Music/Dezzy"
    }
}

actual fun createPublicMusicCopier(scope: org.koin.core.scope.Scope): PublicMusicCopier =
    PublicMusicCopier(scope.androidContext())
