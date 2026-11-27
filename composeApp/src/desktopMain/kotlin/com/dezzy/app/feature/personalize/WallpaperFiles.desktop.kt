/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.personalize

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.imageio.ImageIO

private class DesktopWallpaperFiles :
    JvmWallpaperFiles(File(System.getProperty("user.home"), ".dezzy/wallpapers")) {

    /** En escritorio no hay selector `content://`: se admite una ruta de archivo local y se copia. */
    override suspend fun importPicked(uri: String, ext: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val src = fileOf(uri) ?: File(uri)
            val dest = newFile(ext)
            src.copyTo(dest, overwrite = true)
            uriOf(dest)
        }.getOrNull()
    }

    /** Imágenes con ImageIO; el vídeo no se lee en escritorio (se da por vertical). */
    override fun isLandscape(uri: String, isVideo: Boolean): Boolean {
        if (isVideo) return false
        val file = fileOf(uri) ?: return false
        return runCatching {
            val img = ImageIO.read(file) ?: return false
            img.width > img.height
        }.getOrDefault(false)
    }

    override fun isOnline(): Boolean = true
}

private val instance: WallpaperFiles by lazy { DesktopWallpaperFiles() }

actual fun wallpaperFiles(): WallpaperFiles = instance
