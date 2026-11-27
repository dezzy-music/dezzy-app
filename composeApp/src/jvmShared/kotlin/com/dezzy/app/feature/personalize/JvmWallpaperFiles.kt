/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.personalize

import java.io.File
import java.io.FileOutputStream

/** Parte común (java.io) de [WallpaperFiles]; cada plataforma aporta la carpeta y lo que depende del sistema. */
internal abstract class JvmWallpaperFiles(private val dir: File) : WallpaperFiles {
    private val prefix = "pin-"

    /** `file:///ruta` (y `file:///C:/ruta` en Windows). Sin codificar: los nombres los ponemos nosotros. */
    protected fun uriOf(file: File): String {
        val p = file.absolutePath.replace('\\', '/')
        return "file://" + if (p.startsWith("/")) p else "/$p"
    }

    protected fun fileOf(uri: String): File? {
        if (!uri.startsWith("file://")) return null
        var p = uri.removePrefix("file://")
        if (Regex("^/[A-Za-z]:/").containsMatchIn(p)) p = p.substring(1)
        return File(p)
    }

    protected fun newFile(ext: String): File {
        dir.mkdirs()
        return File(dir, "$prefix${System.currentTimeMillis()}.$ext")
    }

    override fun openSink(): WallpaperSink {
        dir.mkdirs()
        val tmp = File(dir, "tmp-${System.nanoTime()}")
        val out = FileOutputStream(tmp)
        return object : WallpaperSink {
            private var closed = false
            override fun write(buf: ByteArray, len: Int) = out.write(buf, 0, len)

            override fun finish(ext: String): String {
                out.close()
                closed = true
                val dest = newFile(ext)
                if (!tmp.renameTo(dest)) {
                    tmp.delete()
                    throw java.io.IOException("No se pudo guardar el archivo")
                }
                return uriOf(dest)
            }

            override fun abort() {
                if (!closed) runCatching { out.close() }
                closed = true
                tmp.delete()
            }
        }
    }

    override fun isOwnFile(uri: String): Boolean {
        val f = fileOf(uri) ?: return false
        return f.name.startsWith(prefix) && f.parentFile?.canonicalPath == dir.canonicalPath
    }

    override fun deleteOwnFile(uri: String) {
        if (isOwnFile(uri)) fileOf(uri)?.delete()
    }
}
