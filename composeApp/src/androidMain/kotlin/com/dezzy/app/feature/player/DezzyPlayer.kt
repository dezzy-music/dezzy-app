/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.dezzy.app.core.database.MusicRepository
import com.dezzy.app.core.database.db.DezzyDatabase
import com.dezzy.app.core.model.QueueItem
import com.dezzy.app.core.model.Track
import com.dezzy.app.core.net.HttpClientFactory
import com.dezzy.app.core.prefs.DezzyPreferences
import com.dezzy.app.feature.video.AndroidVideoMode
import com.dezzy.app.feature.video.MusicVideoDownloader
import com.dezzy.app.feature.video.MusicVideoResolver
import com.dezzy.app.feature.video.VideoModeController
import com.dezzy.app.source.stream.AudioQuality
import com.dezzy.app.source.stream.StreamPriority
import com.dezzy.app.source.stream.StreamUrlResolver
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import kotlin.math.abs
import kotlin.math.min

/**
 * El reproductor de Dezzy. La cola ([PlayerQueue]) manda; ExoPlayer sólo tiene la canción
 * que suena. Comportamiento portado de Dezzy Desktop: fundido lineal, normalización que sólo
 * baja, precarga de las 2 siguientes, vigilante de 15 s con un reintento, salto tras error,
 * temporizador y cola guardada.
 *
 * Todo en el hilo principal. [DezzyPlaybackService] lo publica en la sesión multimedia
 * (notificación, pantalla de bloqueo, auriculares).
 */
@OptIn(UnstableApi::class)
class DezzyPlayer(
    private val context: Context,
    private val resolver: StreamUrlResolver,
    private val repository: MusicRepository,
    private val db: DezzyDatabase,
    private val prefs: DezzyPreferences,
    private val videoResolver: MusicVideoResolver,
) : PlayerController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = PlayerQueue()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    // Ajustes ------------------------------------------------------------------
    @Volatile private var quality = AudioQuality.AUTO
    private var crossfadeMs = 0L
    private var normalize = false
    private var autoSkip = true
    private var persistentQueue = true

    // Volumen -----------------------------------------------------------------
    private var normGain = 1f
    private var fadeGain = 1f
    private var appliedVolume = -1f
    private var userVolume = 1f

    override val audioSessionId: Int get() = exo?.audioSessionId ?: 0

    override fun setVolume(volume: Float) {
        userVolume = volume.coerceIn(0f, 1f)
        _state.update { it.copy(volume = userVolume) }
        applyVolume()
        videoModeInstance?.setVolume(userVolume)
    }

    // Modo vídeo ------------------------------------------------------------------
    private var videoModeInstance: AndroidVideoMode? = null

    private val videoMode: AndroidVideoMode
        get() = videoModeInstance ?: AndroidVideoMode(
            context = context,
            resolver = videoResolver,
            downloader = MusicVideoDownloader(context),
            prefs = prefs,
            scope = scope,
            host = videoHost,
        ).also { videoModeInstance = it }

    override val video: VideoModeController get() = videoMode

    /** El ExoPlayer del vídeo cuando es él quien suena; null si suena la canción. */
    private val drivingVideo: ExoPlayer? get() = videoModeInstance?.takeIf { it.isDriving }?.drivingPlayer

    private val videoHost = object : AndroidVideoMode.Host {
        override val audioPlayer: ExoPlayer? get() = exo
        override fun currentEntryId(): String? = queue.state.value.current?.queueEntryId
        override fun currentTrack(): Track? = queue.state.value.current?.track
        override fun audioPosition(): Long {
            val player = exo
            return if (player != null && player.mediaItemCount > 0) player.currentPosition.coerceAtLeast(0)
            else pendingResumeMs ?: 0L
        }
        override fun audioPlayWhenReady(): Boolean = exo?.playWhenReady ?: false
        override fun audioDuration(): Long? =
            exo?.takeIf { it.mediaItemCount > 0 }?.let(::knownDuration)?.takeIf { it > 0 }
                ?: queue.state.value.current?.track?.durationSeconds?.takeIf { it > 0 }?.times(1000)
        override fun pauseAudio() {
            exo?.pause()
        }
        override fun resumeAudio(positionMs: Long, play: Boolean) {
            val player = exo
            if (player == null || player.mediaItemCount == 0 || player.playbackState == Player.STATE_IDLE) {
                pendingResumeMs = positionMs
                if (play) play() else publishPlayer()
                return
            }
            player.seekTo(positionMs)
            if (play) play() else player.pause()
            updateFade()
            publishPlayer()
        }
        override val repeatOne: Boolean get() = queue.state.value.repeat == RepeatMode.ONE
        override fun hasNext(): Boolean = queue.nextIndex() != null
        override fun skipToNext() = next()
        override val volume: Float get() = userVolume
        override fun onVideoStateChanged() {
            publishPlayer()
            notifySessionOfVideo()
        }
    }

    // Carga en curso ------------------------------------------------------------
    private var loadToken = 0
    private var retried = false
    private var soundStarted = false
    private var pendingResumeMs: Long? = null
    private var watchdog: Job? = null
    private var skipJob: Job? = null
    private var ticker: Job? = null
    private var sleepJob: Job? = null
    private var radioJob: Job? = null

    // Tiempo escuchado y cola guardada -----------------------------------------
    private val listen = ListenAccumulator()
    private var listenVideoId: String? = null
    private var restored = false
    private var lastSavedJson: String? = null

    /** Quien sepa dar «más canciones como esta» para la radio (catálogo, fase 5). */
    var radioProvider: (suspend (Track) -> List<Track>)? = null

    private var controllerFuture: ListenableFuture<MediaController>? = null

    private var exo: ExoPlayer? = null
    val exoPlayer: ExoPlayer get() = exo ?: buildPlayer().also { exo = it }

    /** Lo que ve la sesión multimedia: ⏮/⏭ van a la cola, no a ExoPlayer. */
    val sessionPlayer: Player by lazy { QueuePlayer(exoPlayer) }

    init {
        scope.launch { prefs.audioQuality.collect { quality = parseQuality(it) } }
        scope.launch { prefs.crossfadeSeconds.collect { crossfadeMs = it.coerceIn(0, 12) * 1000L; updateFade() } }
        scope.launch { prefs.normalizeVolume.collect { normalize = it; applyVolume() } }
        scope.launch { prefs.autoSkip.collect { autoSkip = it } }
        scope.launch { queue.state.collect { q -> _state.update { it.copy(queue = q) } } }
        scope.launch {
            restoreQueue()
            prefs.persistentQueue.collect { enabled ->
                persistentQueue = enabled
                if (!enabled) clearSavedQueue()
            }
        }
        scope.launch {
            while (isActive) {
                delay(SAVE_EVERY_MS)
                saveQueue()
            }
        }
    }

    // Mando ----------------------------------------------------------------------

    override fun playTracks(tracks: List<Track>, start: Int, source: String?) {
        if (queue.load(tracks, start, source)) loadIndex(queue.state.value.index)
    }

    override fun playRadio(track: Track, source: String) {
        queue.loadRadio(track, source)
        loadIndex(0)
    }

    override fun playAt(index: Int) {
        if (index in queue.state.value.items.indices) loadIndex(index)
    }

    override fun playNext(track: Track) {
        val wasEmpty = queue.state.value.current == null
        queue.playNext(track)
        if (wasEmpty) loadIndex(0) else afterQueueChange()
    }

    override fun addToQueue(track: Track) {
        val wasEmpty = queue.state.value.current == null
        queue.addToQueue(track)
        if (wasEmpty) loadIndex(0) else afterQueueChange()
    }

    override fun removeAt(index: Int) {
        queue.removeAt(index)
        afterQueueChange()
    }

    override fun move(from: Int, to: Int) {
        queue.move(from, to)
        afterQueueChange()
    }

    override fun clearQueue() {
        queue.clear()
        afterQueueChange()
    }

    override fun play() {
        if (drivingVideo != null) {
            videoMode.play()
            return
        }
        val index = queue.state.value.index
        val player = exoPlayer
        if (player.mediaItemCount == 0 || player.playbackState == Player.STATE_IDLE) {
            // Nada cargado (cola recuperada) o tras un error: se carga donde se quedó.
            if (index >= 0) {
                val at = pendingResumeMs ?: player.currentPosition.coerceAtLeast(0)
                loadIndex(index, startMs = at)
            }
            return
        }
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
        if (!soundStarted) startWatchdog()
    }

    override fun pause() {
        if (drivingVideo != null) {
            videoMode.pause()
            return
        }
        exo?.pause()
    }

    override fun togglePlay() {
        drivingVideo?.let { vp ->
            val active = vp.playWhenReady && vp.playbackState != Player.STATE_ENDED
            if (active) pause() else play()
            return
        }
        val player = exo
        val active = player != null && player.playWhenReady &&
            player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED
        if (active) pause() else play()
    }

    override fun next() {
        queue.nextIndex()?.let { loadIndex(it) }
    }

    override fun previous() {
        val vp = drivingVideo
        val position = vp?.currentPosition ?: exo?.currentPosition ?: 0L
        when (val action = queue.previous(position)) {
            PreviousAction.Restart -> {
                if (vp != null) {
                    videoMode.seekTo(0)
                    return
                }
                val player = exo
                if (player == null || player.mediaItemCount == 0) {
                    pendingResumeMs = 0
                    publishPlayer()
                } else {
                    player.seekTo(0)
                }
            }
            is PreviousAction.Load -> loadIndex(action.index)
        }
    }

    override fun seekTo(positionMs: Long) {
        if (drivingVideo != null) {
            videoMode.seekTo(positionMs)
            publishPlayer()
            return
        }
        val player = exo
        if (player == null || player.mediaItemCount == 0) pendingResumeMs = positionMs.coerceAtLeast(0)
        else player.seekTo(positionMs.coerceAtLeast(0))
        updateFade()
        publishPlayer()
    }

    override fun toggleShuffle() {
        queue.toggleShuffle()
        afterQueueChange()
    }

    override fun cycleRepeat() {
        queue.cycleRepeat()
        updateFade()
    }

    override fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _state.update { it.copy(sleepAtMillis = null) }
            return
        }
        val ms = minutes * 60_000L
        _state.update { it.copy(sleepAtMillis = System.currentTimeMillis() + ms) }
        sleepJob = scope.launch {
            delay(ms)
            pause()
            _state.update { it.copy(sleepAtMillis = null) }
        }
    }

    override fun setSleepAtEndOfSong(enabled: Boolean) {
        _state.update { it.copy(sleepAtEndOfSong = enabled) }
    }

    // Carga ------------------------------------------------------------------------

    private fun loadIndex(index: Int, startMs: Long = 0, autoplay: Boolean = true, isRetry: Boolean = false) {
        queue.setIndex(index)
        val item = queue.state.value.current ?: return
        finishListen()
        val token = ++loadToken
        retried = isRetry
        soundStarted = false
        pendingResumeMs = null
        skipJob?.cancel()
        watchdog?.cancel()
        _state.update { it.copy(error = null) }

        val videoId = item.track.videoId
        if (videoId == null) {
            skipAfterError("Esta canción no tiene audio")
            return
        }
        ensureService()

        val player = exoPlayer
        normGain = 1f
        fadeGain = if (crossfadeMs > 0 && queue.state.value.repeat != RepeatMode.ONE) {
            PlaybackMath.crossfadeGain(startMs, 0, crossfadeMs)
        } else 1f
        applyVolume()
        player.setMediaItem(mediaItemFor(item, videoId), startMs)
        player.prepare()
        player.playWhenReady = autoplay

        // El volumen normalizado sale de la misma resolución que usa la fuente de audio.
        scope.launch {
            val loudness = resolver.resolveStream(videoId, StreamPriority.PLAYBACK, quality).getOrNull()?.loudnessDb
            if (token == loadToken) {
                normGain = PlaybackMath.normalizationGain(loudness)
                applyVolume()
            }
        }
        if (autoplay) startWatchdog()
        afterQueueChange()
        publishPlayer()
        scope.launch { saveQueue() }
        // El vídeo de la canción anterior se cierra (y, si toca, se abre el de esta).
        videoModeInstance?.onSongChanged()
    }

    private fun startWatchdog() {
        watchdog?.cancel()
        val token = loadToken
        watchdog = scope.launch {
            delay(LOAD_TIMEOUT_MS)
            if (token == loadToken && !soundStarted && exo?.playWhenReady == true) {
                retryOrSkip("La canción tardó demasiado en cargar")
            }
        }
    }

    /** Un reintento con la URL rehecha; si vuelve a fallar, se salta. */
    private fun retryOrSkip(reason: String) {
        val state = queue.state.value
        val videoId = state.current?.track?.videoId
        if (retried || videoId == null) {
            skipAfterError(reason)
            return
        }
        val index = state.index
        val position = exo?.currentPosition?.coerceAtLeast(0) ?: 0L
        val token = loadToken
        scope.launch {
            resolver.invalidate(videoId)
            if (token == loadToken) loadIndex(index, startMs = position, autoplay = true, isRetry = true)
        }
    }

    private fun skipAfterError(reason: String) {
        watchdog?.cancel()
        _state.update { it.copy(error = reason) }
        if (!autoSkip) {
            exo?.pause()
            return
        }
        val token = loadToken
        skipJob?.cancel()
        skipJob = scope.launch {
            delay(SKIP_DELAY_MS)
            if (token != loadToken) return@launch
            val next = queue.nextIndexAfterError()
            if (next != null) loadIndex(next) else exo?.pause()
        }
    }

    private fun handleEnded() {
        finishListen()
        val stopHere = _state.value.sleepAtEndOfSong
        if (stopHere) _state.update { it.copy(sleepAtEndOfSong = false) }
        val player = exoPlayer
        when (val action = queue.onEnded()) {
            EndedAction.RepeatOne -> {
                soundStarted = false
                player.seekTo(0)
                player.playWhenReady = !stopHere
            }
            is EndedAction.Load -> loadIndex(action.index, autoplay = !stopHere)
            EndedAction.Stop -> {
                soundStarted = false
                player.playWhenReady = false
                player.seekTo(0)
            }
        }
    }

    /** Precarga de las 2 siguientes y, en radio, pedir más cuando se acaban. */
    private fun afterQueueChange() {
        queue.upcoming(PREFETCH_COUNT).mapNotNull { it.track.videoId }.forEach { resolver.prefetch(it, quality) }
        maybeExtendRadio()
    }

    private fun maybeExtendRadio() {
        if (!queue.needsRadioExtension() || radioJob?.isActive == true) return
        val provider = radioProvider ?: return
        val seed = queue.state.value.current?.track ?: return
        radioJob = scope.launch {
            val more = runCatching { provider(seed) }.getOrNull().orEmpty()
            if (more.isNotEmpty()) {
                queue.appendRadio(more)
                queue.upcoming(PREFETCH_COUNT).mapNotNull { it.track.videoId }.forEach { resolver.prefetch(it, quality) }
            }
        }
    }

    // Sonido -----------------------------------------------------------------------

    private fun onSoundStarted() {
        if (soundStarted) return
        soundStarted = true
        watchdog?.cancel()
        _state.update { it.copy(error = null) }
        val track = queue.state.value.current?.track ?: return
        listenVideoId = track.videoId
        scope.launch { runCatching { repository.addToHistory(track) } }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            var lastPublish = 0L
            while (isActive) {
                val player = exo ?: break
                val now = System.currentTimeMillis()
                listen.tick(now, player.isPlaying)
                listen.takeFull()?.let(::writeListen)
                updateFade()
                if (now - lastPublish >= PUBLISH_EVERY_MS) {
                    lastPublish = now
                    publishPlayer()
                }
                if (!player.isPlaying) break
                delay(PlaybackMath.TICK_MS)
            }
        }
    }

    private fun updateFade() {
        val player = exo ?: return
        fadeGain = if (crossfadeMs <= 0 || queue.state.value.repeat == RepeatMode.ONE) {
            1f
        } else {
            PlaybackMath.crossfadeGain(player.currentPosition, knownDuration(player), crossfadeMs)
        }
        applyVolume()
    }

    private fun applyVolume() {
        val player = exo ?: return
        val level = min(1f, if (normalize) normGain else 1f) * fadeGain * userVolume
        if (abs(level - appliedVolume) > 0.001f) {
            appliedVolume = level
            player.volume = level
        }
    }

    private fun knownDuration(player: Player): Long =
        player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L

    // Tiempo escuchado -------------------------------------------------------------

    private fun writeListen(segment: ListenAccumulator.Segment) {
        val videoId = listenVideoId ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { db.dezzyQueries.addListen(videoId, segment.startedAtMillis, segment.seconds) }
        }
    }

    /** Cambio de canción: lo que no llega a 5 s se tira. */
    private fun finishListen() {
        listen.drain()?.let(::writeListen)
        listenVideoId = null
    }

    // Cola guardada ----------------------------------------------------------------

    private suspend fun restoreQueue() {
        try {
            persistentQueue = prefs.persistentQueue.first()
            if (!persistentQueue || queue.state.value.current != null) return
            val json = withContext(Dispatchers.IO) { db.dezzyQueries.getKv(QUEUE_KEY).executeAsOneOrNull() } ?: return
            val snapshot = runCatching { Json.decodeFromString(QueueSnapshot.serializer(), json) }.getOrNull() ?: return
            if (queue.state.value.current != null) return
            queue.restore(snapshot)
            pendingResumeMs = snapshot.positionMs
            lastSavedJson = json
            publishPlayer()
        } finally {
            restored = true
        }
    }

    private suspend fun saveQueue() {
        if (!restored || !persistentQueue) return
        val state = queue.state.value
        if (state.items.isEmpty()) {
            clearSavedQueue()
            return
        }
        val player = exo
        val position = if (player != null && player.mediaItemCount > 0) player.currentPosition.coerceAtLeast(0) else pendingResumeMs ?: 0L
        val json = Json.encodeToString(QueueSnapshot.serializer(), queue.snapshot(position))
        if (json == lastSavedJson) return
        lastSavedJson = json
        withContext(Dispatchers.IO) { runCatching { db.dezzyQueries.putKv(QUEUE_KEY, json) } }
    }

    private suspend fun clearSavedQueue() {
        if (lastSavedJson == "") return
        lastSavedJson = ""
        withContext(Dispatchers.IO) { runCatching { db.dezzyQueries.deleteKv(QUEUE_KEY) } }
    }

    // Estado -----------------------------------------------------------------------

    private fun publishPlayer() {
        val player = exo
        val current = queue.state.value.current
        val fallbackDuration = (current?.track?.durationSeconds ?: 0L) * 1000
        val vp = drivingVideo
        _state.update {
            if (vp != null) {
                it.copy(
                    isPlaying = vp.isPlaying,
                    isBuffering = (vp.playbackState == Player.STATE_BUFFERING || vp.playbackState == Player.STATE_IDLE) &&
                        vp.playWhenReady,
                    positionMs = vp.currentPosition.coerceAtLeast(0),
                    durationMs = knownDuration(vp).takeIf { d -> d > 0 } ?: fallbackDuration,
                )
            } else if (player == null || player.mediaItemCount == 0) {
                it.copy(isPlaying = false, isBuffering = false, positionMs = pendingResumeMs ?: 0L, durationMs = fallbackDuration)
            } else {
                it.copy(
                    isPlaying = player.isPlaying,
                    isBuffering = player.playbackState == Player.STATE_BUFFERING && player.playWhenReady,
                    positionMs = player.currentPosition.coerceAtLeast(0),
                    durationMs = knownDuration(player).takeIf { d -> d > 0 } ?: fallbackDuration,
                )
            }
        }
    }

    // Servicio y sesión ------------------------------------------------------------

    /** Engancha el servicio (notificación y controles del sistema) la primera vez que suena algo. */
    private fun ensureService() {
        val current = controllerFuture
        if (current != null) {
            val alive = !current.isDone || runCatching { current.get().isConnected }.getOrDefault(false)
            if (alive) return
            MediaController.releaseFuture(current)
        }
        val token = SessionToken(context, ComponentName(context, DezzyPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
    }

    /** El servicio se va (se quitó la app estando en pausa): se suelta para que pueda pararse. */
    internal fun releaseService() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    /** El servicio se destruye: guarda lo escuchado y la cola. */
    internal fun onServiceDestroyed() {
        listen.drain(keepShort = true)?.let(::writeListen)
        scope.launch { saveQueue() }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                onSoundStarted()
                startTicker()
            } else {
                listen.drain(keepShort = true)?.let(::writeListen)
                scope.launch { saveQueue() }
            }
            publishPlayer()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) handleEnded()
            publishPlayer()
        }

        override fun onPlayerError(error: PlaybackException) {
            retryOrSkip(error.message ?: "No se pudo reproducir")
        }
    }

    private fun buildPlayer(): ExoPlayer {
        val http = OkHttpDataSource.Factory(OkHttpClient()).setUserAgent(HttpClientFactory.DEFAULT_USER_AGENT)
        val source = StreamDataSource.Factory(resolver, http) { quality }
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(source))
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also {
                it.addListener(listener)
                appliedVolume = -1f
            }
    }

    private fun mediaItemFor(item: QueueItem, videoId: String): MediaItem {
        val track = item.track
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artists)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.thumbnail?.let(Uri::parse))
            .build()
        return MediaItem.Builder()
            .setMediaId(item.queueEntryId)
            .setUri(StreamDataSource.uriFor(videoId))
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * ExoPlayer sólo tiene una canción; la sesión debe ofrecer ⏮/⏭ igualmente y mandarlos a
     * la cola. Reproducir y pausar también pasan por aquí para recargar si hace falta.
     */
    // Quien escucha a la sesión (notificación, pantalla de bloqueo) y lo último que se le dijo
    // del vídeo: el ExoPlayer de la canción no avisa de lo que hace el vídeo.
    private val sessionListeners = mutableListOf<Player.Listener>()
    private var notifiedVideoPlaying: Boolean? = null
    private var notifiedVideoPlayWhenReady: Boolean? = null
    private var notifiedVideoState: Int? = null
    private var notifiedVideoDriving = false

    private fun notifySessionOfVideo() {
        if (sessionListeners.isEmpty()) return
        val session = sessionPlayer
        val driving = drivingVideo != null
        val playing = session.isPlaying
        val playWhenReady = session.playWhenReady
        val playbackState = session.playbackState
        val switched = driving != notifiedVideoDriving
        notifiedVideoDriving = driving
        val listeners = sessionListeners.toList()
        if (switched || playWhenReady != notifiedVideoPlayWhenReady) {
            listeners.forEach { it.onPlayWhenReadyChanged(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) }
        }
        if (switched || playbackState != notifiedVideoState) {
            listeners.forEach { it.onPlaybackStateChanged(playbackState) }
        }
        if (switched || playing != notifiedVideoPlaying) {
            listeners.forEach { it.onIsPlayingChanged(playing) }
        }
        notifiedVideoPlaying = playing
        notifiedVideoPlayWhenReady = playWhenReady
        notifiedVideoState = playbackState
    }

    private fun notifySessionSeek() {
        val session = sessionPlayer
        val position = Player.PositionInfo(
            null, session.currentMediaItemIndex, session.currentMediaItem, null, 0,
            session.currentPosition, session.contentPosition, C.INDEX_UNSET, C.INDEX_UNSET,
        )
        sessionListeners.toList().forEach {
            it.onPositionDiscontinuity(position, position, Player.DISCONTINUITY_REASON_SEEK)
        }
    }

    private inner class QueuePlayer(player: Player) : ForwardingPlayer(player) {
        override fun addListener(listener: Player.Listener) {
            sessionListeners += listener
            super.addListener(listener)
        }

        override fun removeListener(listener: Player.Listener) {
            sessionListeners -= listener
            super.removeListener(listener)
        }

        // Mientras suena el vídeo, la sesión ve su estado y no el de la canción en pausa.
        override fun isPlaying(): Boolean = drivingVideo?.isPlaying ?: super.isPlaying()
        override fun getPlayWhenReady(): Boolean = drivingVideo?.playWhenReady ?: super.getPlayWhenReady()
        override fun getPlaybackState(): Int = drivingVideo?.playbackState
            ?.let { if (it == Player.STATE_IDLE) Player.STATE_BUFFERING else it }
            ?: super.getPlaybackState()
        override fun getCurrentPosition(): Long = drivingVideo?.currentPosition ?: super.getCurrentPosition()
        override fun getContentPosition(): Long = drivingVideo?.currentPosition ?: super.getContentPosition()
        override fun getBufferedPosition(): Long = drivingVideo?.bufferedPosition ?: super.getBufferedPosition()
        override fun getContentBufferedPosition(): Long =
            drivingVideo?.bufferedPosition ?: super.getContentBufferedPosition()
        override fun getDuration(): Long = drivingVideo?.duration ?: super.getDuration()
        override fun getContentDuration(): Long = drivingVideo?.duration ?: super.getContentDuration()
        override fun seekTo(positionMs: Long) {
            if (drivingVideo != null) {
                this@DezzyPlayer.seekTo(positionMs)
                notifySessionSeek()
            } else {
                super.seekTo(positionMs)
            }
        }
        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            if (drivingVideo != null) seekTo(positionMs) else super.seekTo(mediaItemIndex, positionMs)
        }
        override fun seekBack() {
            val vp = drivingVideo ?: return super.seekBack()
            seekTo((vp.currentPosition - vp.seekBackIncrement).coerceAtLeast(0))
        }
        override fun seekForward() {
            val vp = drivingVideo ?: return super.seekForward()
            seekTo(vp.currentPosition + vp.seekForwardIncrement)
        }

        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon().addAll(*QUEUE_COMMANDS).build()

        override fun isCommandAvailable(command: Int): Boolean =
            command in QUEUE_COMMANDS || super.isCommandAvailable(command)

        override fun hasNextMediaItem(): Boolean = queue.nextIndex() != null
        override fun hasPreviousMediaItem(): Boolean = queue.state.value.current != null
        override fun seekToNext() = next()
        override fun seekToNextMediaItem() = next()
        override fun seekToPrevious() = previous()
        override fun seekToPreviousMediaItem() = previous()
        override fun play() = this@DezzyPlayer.play()
        override fun pause() = this@DezzyPlayer.pause()
        override fun setPlayWhenReady(playWhenReady: Boolean) = if (playWhenReady) play() else pause()

        /** La carga la decide [play]; un «preparar» del sistema no debe reabrir una URL vieja. */
        override fun prepare() = Unit
        override fun stop() = this@DezzyPlayer.pause()
    }

    private companion object {
        val QUEUE_COMMANDS = intArrayOf(
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_PLAY_PAUSE,
        )

        const val QUEUE_KEY = "player.queue"
        const val LOAD_TIMEOUT_MS = 15_000L
        const val SKIP_DELAY_MS = 1_500L
        const val SAVE_EVERY_MS = 4_000L
        const val PUBLISH_EVERY_MS = 500L
        const val PREFETCH_COUNT = 2

        fun parseQuality(value: String): AudioQuality =
            AudioQuality.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: AudioQuality.AUTO
    }
}
