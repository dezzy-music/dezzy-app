/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.spotify

/** Consigue un token de acceso de la biblioteca a partir de la cookie `sp_dc`. */
interface SpotifyTokenSource {
    /** Devuelve el token; lanza una excepción si no lo consigue. */
    suspend fun capture(spDc: String): String
}

/** Cabecera de navegador que se usa tanto al capturar el token como en las consultas. */
internal const val SPOTIFY_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
