/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.dj

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.koin.core.scope.Scope

actual fun createDjAudioPlayer(scope: Scope): DjAudioPlayer = DesktopDjAudioPlayer

/** En escritorio el DJ aún no habla: el guion sale por texto. */
private object DesktopDjAudioPlayer : DjAudioPlayer {
    override suspend fun play(audio: ByteArray, onSession: (Int) -> Unit): Boolean = false
    override suspend fun speak(text: String, languageTag: String): Boolean = false
    override fun stop() = Unit
}

@Composable
actual fun rememberDjDictation(djName: String, onResult: (String) -> Unit): () -> Unit = remember { {} }
