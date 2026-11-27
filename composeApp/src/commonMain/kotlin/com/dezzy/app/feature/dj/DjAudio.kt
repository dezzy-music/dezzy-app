/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.dj

import androidx.compose.runtime.Composable
import org.koin.core.scope.Scope

/** Reproduce la voz del DJ. En Android usa MediaPlayer y el sintetizador del sistema; en escritorio no hace nada. */
interface DjAudioPlayer {
    /**
     * Reproduce un audio mp3 y espera a que termine. [onSession] recibe el id de sesión de audio
     * (para el visualizador) en cuanto existe. Devuelve false si no se pudo reproducir.
     */
    suspend fun play(audio: ByteArray, onSession: (Int) -> Unit): Boolean

    /** Voz gratuita: lee [text] con el sintetizador del sistema. [languageTag]: «es», «en»… Devuelve false si no pudo. */
    suspend fun speak(text: String, languageTag: String): Boolean

    /** Corta lo que esté sonando. */
    fun stop()
}

expect fun createDjAudioPlayer(scope: Scope): DjAudioPlayer

/**
 * Dictado por voz para pedirle música al DJ. Devuelve la función que lo lanza; [onResult] recibe
 * el texto dictado. En plataformas sin dictado no hace nada.
 */
@Composable
expect fun rememberDjDictation(djName: String, onResult: (String) -> Unit): () -> Unit
