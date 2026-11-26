/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.lyrics

import com.dezzy.app.core.model.Track
import com.dezzy.app.core.prefs.DezzyPreferences
import com.dezzy.app.feature.player.PlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Sigue a la canción que suena, busca su letra y (si hay idioma elegido) la traduce. */
class LyricsManager(
    private val player: PlayerController,
    private val service: LyricsService,
    private val translator: LyricsTranslator,
    private val prefs: DezzyPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    private val _state = MutableStateFlow<LyricsState>(LyricsState.Idle)
    val state: StateFlow<LyricsState> = _state.asStateFlow()

    private val _translation = MutableStateFlow<TranslationState>(TranslationState.Off)
    val translation: StateFlow<TranslationState> = _translation.asStateFlow()

    private val retryTick = MutableStateFlow(0)
    private var skipCacheOnce = false

    private fun keyOf(track: Track): String = track.videoId ?: "${track.title}|${track.artists}"

    /** Idempotente: empieza a seguir la canción que suena. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(
                player.state.map { it.queue.current?.track }.distinctUntilChanged { a, b ->
                    (a?.let { keyOf(it) }) == (b?.let { keyOf(it) })
                },
                retryTick,
            ) { track, tick -> track to tick }.collectLatest { (track, _) ->
                if (track == null) {
                    _state.value = LyricsState.Idle
                    return@collectLatest
                }
                val id = keyOf(track)
                _state.value = LyricsState.Loading(id)
                val skip = skipCacheOnce
                skipCacheOnce = false
                // Sin duración propia no se usa la del reproductor: en el cambio de pista aún puede ser la de la anterior.
                val durationSec = track.durationSeconds?.takeIf { it > 0 }?.toInt()
                val query = LyricsQuery(
                    videoId = track.videoId,
                    title = track.title,
                    artists = track.artists.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    album = track.album,
                    durationSec = durationSec,
                )
                val result = try {
                    service.fetch(query, skip)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                _state.value = if (result != null) LyricsState.Found(id, result) else LyricsState.NotFound(id)
            }
        }
        scope.launch {
            combine(_state, prefs.value(LyricsTranslateLangKey, "")) { st, lang -> st to lang }
                .collectLatest { (st, lang) -> translate(st, lang) }
        }
    }

    private suspend fun translate(st: LyricsState, lang: String) {
        if (lang.isBlank()) {
            _translation.value = TranslationState.Off
            return
        }
        when (st) {
            is LyricsState.Found -> {
                _translation.value = TranslationState.Loading(lang)
                val originals = st.result.synced?.map { it.text } ?: st.result.plain.orEmpty().lines()
                try {
                    val translated = translator.translate(originals, lang)
                    val lines = originals.mapIndexed { i, original ->
                        val t = translated.getOrNull(i).orEmpty()
                        if (t.isBlank() || squash(t) == squash(original)) null else t
                    }
                    _translation.value = TranslationState.Ready(lang, lines)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _translation.value = TranslationState.Failed(lang)
                }
            }
            is LyricsState.Loading -> _translation.value = TranslationState.Loading(lang)
            else -> _translation.value = TranslationState.Off
        }
    }

    private fun squash(s: String): String = s.filterNot { it.isWhitespace() }.lowercase()

    /** Vuelve a pedir la letra de la canción actual, saltándose la caché. */
    fun retry() {
        skipCacheOnce = true
        retryTick.value = retryTick.value + 1
    }

    /** null o "" = sin traducir. Guarda la preferencia. */
    fun setTranslateLanguage(lang: String?) {
        scope.launch { prefs.set(LyricsTranslateLangKey, lang.orEmpty()) }
    }
}
