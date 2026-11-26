/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.video

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Caché exclusiva del modo vídeo (filesDir/video_cache, 256 MB), aparte de las de audio para
 * que los videoclips nunca desplacen ni estropeen canciones guardadas.
 */
@OptIn(UnstableApi::class)
object MusicVideoCache {
    private const val DIRECTORY = "video_cache"
    private const val MAX_BYTES = 256L * 1024 * 1024

    @Volatile private var cache: SimpleCache? = null

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun cache(context: Context): SimpleCache =
        cache ?: synchronized(this) {
            cache ?: SimpleCache(
                context.applicationContext.filesDir.resolve(DIRECTORY),
                LeastRecentlyUsedCacheEvictor(MAX_BYTES),
                StandaloneDatabaseProvider(context.applicationContext),
            ).also { cache = it }
        }

    /**
     * Las URLs de googlevideo cambian de firma en cada resolución; la clave estable es
     * id+itag+tamaño, así un videoclip ya visto se reutiliza aunque su URL sea nueva.
     */
    private val cacheKeyFactory = CacheKeyFactory { dataSpec ->
        val uri: Uri = dataSpec.uri
        val host = uri.host.orEmpty()
        val id = runCatching { uri.getQueryParameter("id") }.getOrNull()
        val itag = runCatching { uri.getQueryParameter("itag") }.getOrNull()
        if (host.endsWith("googlevideo.com") && !id.isNullOrBlank() && !itag.isNullOrBlank()) {
            val clen = runCatching { uri.getQueryParameter("clen") }.getOrNull().orEmpty()
            val sq = runCatching { uri.getQueryParameter("sq") }.getOrNull().orEmpty()
            "gv:$id:$itag:$clen:$sq"
        } else {
            dataSpec.key ?: uri.toString()
        }
    }

    /** Fuente de datos del reproductor de vídeo: red (cliente propio) + caché de vídeo. */
    fun dataSourceFactory(context: Context): DataSource.Factory {
        val upstream = DefaultDataSource.Factory(
            context.applicationContext,
            OkHttpDataSource.Factory(httpClient).setUserAgent(MusicVideoResolver.USER_AGENT),
        )
        return CacheDataSource.Factory()
            .setCache(cache(context))
            .setCacheKeyFactory(cacheKeyFactory)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
