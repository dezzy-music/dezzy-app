/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.storage

import android.content.Context
import com.dezzy.app.feature.video.MusicVideoCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidCacheCleaner(private val context: Context) : CacheCleaner {
    private val imageDir: File get() = context.cacheDir.resolve("image_cache")

    override suspend fun entries(): List<CacheEntry> = withContext(Dispatchers.IO) {
        listOf(
            CacheEntry("images", "Caché de imágenes", imageDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }),
            CacheEntry("video", "Caché de vídeo", MusicVideoCache.sizeBytes(context)),
        )
    }

    override suspend fun clear(id: String) {
        withContext(Dispatchers.IO) {
            when (id) {
                "images" -> imageDir.listFiles()?.forEach { it.deleteRecursively() }
                "video" -> MusicVideoCache.clear(context)
            }
        }
    }
}
