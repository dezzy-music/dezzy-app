/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.lossless

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

actual class PublicMusicCopier {
    actual suspend fun copy(path: String, displayName: String, mime: String): Boolean = false
}

actual fun createPublicMusicCopier(scope: org.koin.core.scope.Scope): PublicMusicCopier = PublicMusicCopier()
