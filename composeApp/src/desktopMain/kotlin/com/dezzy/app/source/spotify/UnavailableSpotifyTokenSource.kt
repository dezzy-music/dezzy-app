/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.spotify

class UnavailableSpotifyTokenSource : SpotifyTokenSource {
    override suspend fun capture(spDc: String): String =
        throw IllegalStateException("En el escritorio, Spotify llega vinculado desde el móvil")
}
