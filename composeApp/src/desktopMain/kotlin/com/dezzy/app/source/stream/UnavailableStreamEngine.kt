/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.stream

/**
 * El destino JVM de escritorio sólo sirve para las pruebas: el Dezzy de PC es
 * Dezzy Desktop (Electron), que tiene su propio motor.
 */
class UnavailableStreamEngine : StreamEngine {
    private fun unavailable(): Nothing = throw StreamEngineException("El motor de streaming sólo existe en Android")
    override suspend fun createSession(): String = unavailable()
    override suspend fun mintPoToken(binding: String): String = unavailable()
    override suspend fun audioFormats(videoId: String, client: String, poToken: String): List<EngineAudioFormat> = unavailable()
    override fun reset(hard: Boolean) = Unit
}
