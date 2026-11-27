/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.together

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal actual fun gzipCompress(data: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    GZIPOutputStream(out).use { it.write(data) }
    return out.toByteArray()
}

/** Descomprime con tope de 8 MiB para no dejar que un mensaje malicioso llene la memoria. */
internal actual fun gzipDecompress(data: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    GZIPInputStream(ByteArrayInputStream(data)).use { input ->
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > 8 * 1024 * 1024) throw java.io.IOException("Mensaje demasiado grande")
            out.write(buffer, 0, n)
        }
    }
    return out.toByteArray()
}
