/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.dj

import com.dezzy.app.feature.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** En qué está el DJ. */
enum class DjUiState { IDLE, GENERATING, PLAYING, SPEAKING }

/**
 * Cerebro del DJ (singleton de Koin; sustituye al ViewModel). Arma la sesión con [DjSetBuilder],
 * la pone en la cola del reproductor, habla entre canciones con [GeminiDjClient] y la voz elegida,
 * y se adapta en vivo: si saltas dos canciones de un bloque, lo quita; si te gusta una, trae más.
 * Todo se llama desde el hilo principal.
 */
class DjController(
    private val player: PlayerController,
    private val store: DjStore,
    private val settings: DjSettings,
    private val gemini: GeminiDjClient,
    private val voiceApi: DjVoiceApi,
    private val audio: DjAudioPlayer,
    private val builder: DjSetBuilder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(DjUiState.IDLE)
    val uiState: StateFlow<DjUiState> = _uiState.asStateFlow()

    private val _statusText = MutableStateFlow(STATUS_IDLE)
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _djScript = MutableStateFlow("")
    val djScript: StateFlow<String> = _djScript.asStateFlow()

    private val _vibeCards = MutableStateFlow(GeminiDjClient.DEFAULT_VIBES)
    val dynamicVibeCards: StateFlow<List<DjVibeCard>> = _vibeCards.asStateFlow()

    /** null mientras no se ha comprobado; true si hay clave de Gemini. */
    private val _hasBrain = MutableStateFlow<Boolean?>(null)
    val hasBrain: StateFlow<Boolean?> = _hasBrain.asStateFlow()

    /** Pregunta de la primera vez (null = no se está preguntando). */
    private val _askingTaste = MutableStateFlow<String?>(null)
    val askingTaste: StateFlow<String?> = _askingTaste.asStateFlow()

    /** Sesión de audio de la voz del DJ (para el visualizador); 0 si no suena. */
    private val _audioSessionId = MutableStateFlow(0)
    val djAudioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    private var profile: TasteProfile? = null
    private var currentSet: DjSet = DjSet.EMPTY
    private val extraPicks = HashMap<String, DjPick>()
    private val blockSkips = HashMap<Int, Int>()
    private var expansions = 0
    private var songsSinceTalk = 0
    private var spokeOnce = false
    private val recentLines = ArrayList<String>()

    private var sessionJob: Job? = null
    private var monitorJob: Job? = null
    private var speakJob: Job? = null

    suspend fun refreshKeys() {
        _hasBrain.value = settings.geminiKey().isNotBlank()
    }

    // ── Sesión ──────────────────────────────────────────────────────────────────────────────────

    fun startDjSession(userRequest: String? = null) {
        sessionJob?.cancel()
        sessionJob = scope.launch {
            _uiState.value = DjUiState.GENERATING
            _statusText.value = "Preparando tu sesión..."
            refreshKeys()
            val key = settings.geminiKey()
            val djName = settings.djName()
            val language = settings.language.first()
            val built = TasteProfile.build(store)
            profile = built

            // Primera vez sin historial: pregunta qué le gusta (sólo con Gemini).
            if (userRequest == null && built.isEmpty && key.isNotBlank() && settings.taste().isBlank()) {
                val q = gemini.generateTasteQuestion(key, djName, language) ?: "¿Qué música te apetece escuchar hoy?"
                _askingTaste.value = q
                _uiState.value = DjUiState.IDLE
                _statusText.value = STATUS_IDLE
                return@launch
            }

            val set = if (userRequest.isNullOrBlank()) {
                val personal = builder.buildSession(built)
                if (personal.picks.isNotEmpty()) personal else {
                    val taste = settings.taste()
                    val seedText = taste.ifBlank { "éxitos del momento" }
                    builder.buildForRequest(seedText, seedText, built)
                }
            } else {
                val query = if (key.isNotBlank()) gemini.generateSearchQuery(key, userRequest) ?: userRequest else userRequest
                builder.buildForRequest(userRequest, query, built)
            }
            if (set.picks.isEmpty()) {
                _uiState.value = DjUiState.IDLE
                _statusText.value = "No encontré canciones. Prueba con otra petición."
                return@launch
            }

            stopVoice()
            currentSet = set
            extraPicks.clear()
            blockSkips.clear()
            expansions = 0
            songsSinceTalk = 0
            spokeOnce = false
            recentLines.clear()
            startMonitor()
            player.playTracks(set.tracks, 0, "DJ")
            _uiState.value = DjUiState.PLAYING
        }
    }

    /** Petición libre (texto o dictado): Gemini decide qué hacer; sin clave se busca tal cual. */
    fun handleUserRequest(text: String) {
        val request = text.trim()
        if (request.isBlank()) return
        scope.launch {
            refreshKeys()
            val key = settings.geminiKey()
            if (key.isBlank()) {
                startDjSession(request)
                return@launch
            }
            _uiState.value = DjUiState.GENERATING
            _statusText.value = "Pensando..."
            val nowPlaying = player.state.value.queue.current?.track?.let { "${it.title} - ${it.artists}" }
            val agent = gemini.executeAgentTask(key, settings.djName(), request, nowPlaying, settings.language.first())
            if (agent == null) {
                startDjSession(request)
                return@launch
            }
            when (agent.action) {
                DjAction.PLAY_MUSIC -> {
                    if (agent.message.isNotBlank()) _djScript.value = agent.message
                    startDjSession(agent.query.ifBlank { request })
                }
                // Guardar en listas no está conectado todavía: el DJ sólo responde.
                DjAction.ADD_TO_PLAYLIST, DjAction.RESPOND_ONLY -> {
                    _uiState.value = if (player.state.value.isPlaying) DjUiState.PLAYING else DjUiState.IDLE
                    _statusText.value = STATUS_IDLE
                    if (agent.message.isNotBlank()) {
                        _djScript.value = agent.message
                        speakJob?.cancel()
                        speakJob = launch { say(agent.message) }
                    }
                }
            }
        }
    }

    fun answerTaste(text: String) {
        val answer = text.trim()
        _askingTaste.value = null
        scope.launch {
            if (answer.isNotBlank()) settings.setTaste(answer)
            startDjSession(answer.ifBlank { null })
        }
    }

    fun cancelTaste() {
        _askingTaste.value = null
        scope.launch {
            // Que no vuelva a preguntar: se marca como contestado.
            if (settings.taste().isBlank()) settings.setTaste("-")
            startDjSession(null)
        }
    }

    fun loadVibeSuggestions() {
        scope.launch {
            refreshKeys()
            val key = settings.geminiKey()
            if (key.isBlank()) {
                _vibeCards.value = GeminiDjClient.DEFAULT_VIBES
                return@launch
            }
            val cards = gemini.generateVibeCards(key, DjClock.hourOf(DjClock.nowMs()), settings.taste().takeIf { it != "-" }.orEmpty(), settings.language.first())
            _vibeCards.value = cards ?: GeminiDjClient.DEFAULT_VIBES
        }
    }

    /** Termina la sesión del DJ (la música sigue como esté en el reproductor). */
    fun stop() {
        sessionJob?.cancel()
        monitorJob?.cancel()
        stopVoice()
        currentSet = DjSet.EMPTY
        _uiState.value = DjUiState.IDLE
        _statusText.value = STATUS_IDLE
    }

    // ── Monitor de la cola ──────────────────────────────────────────────────────────────────────

    private fun pickOf(videoId: String): DjPick? =
        currentSet.picks.firstOrNull { it.id == videoId } ?: extraPicks[videoId]

    private fun startMonitor() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            var entryId: String? = null
            var prevPick: DjPick? = null
            var pos = 0L
            var dur = 0L
            player.state.collect { st ->
                val item = st.queue.current
                if (item?.queueEntryId != entryId) {
                    val prev = prevPick
                    val listened = pos
                    val prevDur = dur
                    entryId = item?.queueEntryId
                    val pick = item?.track?.videoId?.let { pickOf(it) }
                    pos = 0L
                    dur = 0L
                    prevPick = pick
                    if (prev != null) onLeft(prev, listened, prevDur)
                    if (pick != null) {
                        onEntered(pick)
                    } else if (item != null && prev != null) {
                        // Puso otra música por su cuenta: la sesión del DJ termina.
                        stop()
                    }
                }
                if (item != null) {
                    pos = st.positionMs
                    dur = if (st.durationMs > 0) st.durationMs else (item.track.durationSeconds ?: 0L) * 1000L
                }
            }
        }
    }

    private fun onEntered(pick: DjPick) {
        val index = currentSet.picks.indexOfFirst { it.id == pick.id }
        val block = if (index >= 0) currentSet.blockStartingAt(index) else null
        val first = !spokeOnce
        scope.launch {
            val talk = when (settings.talk.first()) {
                "start" -> first
                "often" -> first || block != null || songsSinceTalk >= 2
                else -> first || block != null
            }
            speakJob?.cancel()
            stopVoice()
            if (talk) {
                spokeOnce = true
                songsSinceTalk = 0
                speakJob = launch { generateAndPlayDjTrack(pick, block ?: currentSet.blockAt(index), first) }
            } else {
                songsSinceTalk++
                _uiState.value = DjUiState.PLAYING
                _statusText.value = "Sonando: ${pick.track.title}"
            }
        }
    }

    private fun onLeft(prev: DjPick, listenedMs: Long, durationMs: Long) {
        val skipped = listenedMs in 1_000L..30_000L && durationMs > 60_000L
        val completed = durationMs > 0 && listenedMs > durationMs * 0.7
        scope.launch {
            if (skipped) {
                val index = currentSet.picks.indexOfFirst { it.id == prev.id }
                val block = if (index >= 0) currentSet.blockAt(index) else null
                if (block != null) {
                    val n = (blockSkips[block.start] ?: 0) + 1
                    blockSkips[block.start] = n
                    if (n >= 2) dropRestOf(block)
                }
            } else if (completed && expansions < MAX_EXPANSIONS) {
                val liked = djCatching { store.isLiked(prev.id) }.getOrDefault(false)
                if (liked || prev.kind == DjKind.DISCOVERY || prev.kind == DjKind.REQUEST) expandAfter(prev)
            }
        }
    }

    /** Dos saltos seguidos en un bloque: se quita lo que queda de ese bloque en la cola. */
    private fun dropRestOf(block: DjBlock) {
        val ids = currentSet.picks.drop(block.start).take(block.size).mapTo(HashSet()) { it.id }
        val q = player.state.value.queue
        val remove = q.items.withIndex().filter { (i, item) -> i > q.index && item.track.videoId in ids }.map { it.index }
        if (remove.size >= q.items.size - q.index - 1) return // no vaciar la cola
        remove.sortedDescending().forEach { player.removeAt(it) }
        if (remove.isNotEmpty()) _statusText.value = "Quito lo que no te convence."
    }

    /** Te gustó o la escuchaste entera: se mete más de lo mismo justo después. */
    private suspend fun expandAfter(prev: DjPick) {
        expansions++
        val exclude = HashSet<String>().apply {
            currentSet.picks.forEach { add(it.id) }
            addAll(extraPicks.keys)
            player.state.value.queue.items.forEach { it.track.videoId?.let(::add) }
        }
        val more = builder.moreLike(prev.track, profile, exclude)
        if (more.isEmpty()) return
        more.forEach { extraPicks[it.id] = it }
        more.reversed().forEach { player.playNext(it.track) }
    }

    // ── Voz ─────────────────────────────────────────────────────────────────────────────────────

    private suspend fun generateAndPlayDjTrack(pick: DjPick, block: DjBlock?, first: Boolean) {
        _uiState.value = DjUiState.SPEAKING
        val djName = settings.djName()
        _statusText.value = "$djName está hablando..."
        val title = pick.track.title
        val artist = pick.track.artists.substringBefore(',').trim().ifBlank { pick.track.artists }
        val username = settings.username()
        val key = settings.geminiKey()
        val script = (if (key.isNotBlank()) {
            val context = if (block != null) DjSetBuilder.describe(block.copy(reason = pick.reason ?: block.reason), title, artist)
            else "Cuenta en una frase por qué viene «$title» de $artist."
            gemini.generateScript(
                key, djName, username, title, artist, context, first,
                settings.language.first(), settings.style.first(), settings.taste().takeIf { it != "-" }.orEmpty(), recentLines.toList(),
            )
        } else null) ?: DjSetBuilder.fallbackLine(block, title, artist, username, djName, first)

        _djScript.value = script
        recentLines += script
        while (recentLines.size > MAX_RECENT_LINES) recentLines.removeAt(0)
        say(script)
        _uiState.value = DjUiState.PLAYING
        _statusText.value = "Sonando: $title"
    }

    /** Dice [text] con la voz elegida, bajando antes el volumen de la música. */
    private suspend fun say(text: String) {
        val language = settings.language.first().let { if (it == "auto") "es" else it }
        player.setVolume(DUCK_VOLUME)
        try {
            val provider = settings.currentProvider()
            val ttsKey = settings.ttsKey()
            var spoken = false
            if (provider != DjVoice.FREE && ttsKey.isNotBlank()) {
                val bytes = voiceApi.synthesize(provider, ttsKey, settings.voice.first(), text, settings.style.first()).getOrNull()
                if (bytes != null && bytes.isNotEmpty()) spoken = audio.play(bytes) { _audioSessionId.value = it }
            }
            if (!spoken) audio.speak(text, language)
        } finally {
            _audioSessionId.value = 0
            player.setVolume(1f)
        }
    }

    private fun stopVoice() {
        speakJob?.cancel()
        audio.stop()
        _audioSessionId.value = 0
        player.setVolume(1f)
    }

    fun release() {
        scope.cancel()
        audio.stop()
    }

    private companion object {
        const val STATUS_IDLE = "Toca el orbe para iniciar"
        const val DUCK_VOLUME = 0.2f
        const val MAX_EXPANSIONS = 4
        const val MAX_RECENT_LINES = 6
    }
}
