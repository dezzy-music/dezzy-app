/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.lossless

import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.downloads.LosslessDownload
import com.dezzy.app.feature.downloads.LosslessRef
import com.dezzy.app.feature.downloads.LosslessUrlProvider

/**
 * Pide al resolver del usuario (`POST <url>/dl/dzr`) la URL descifrada de una pista de Deezer.
 * Sin URL configurada lanza [LOSSLESS_NO_RESOLVER_MESSAGE] (el gestor de descargas no lo reintenta).
 */
class LosslessResolver(private val client: LosslessClient) : LosslessUrlProvider {
    override suspend fun resolve(ref: LosslessRef, track: Track): LosslessDownload =
        client.requestDownload(
            id = ref.trackId,
            isrc = ref.isrc,
            quality = ref.quality,
            title = track.title,
            artist = track.artists,
        )
}
