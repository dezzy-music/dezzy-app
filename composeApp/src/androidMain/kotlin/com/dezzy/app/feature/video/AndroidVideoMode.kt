/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.video

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.dezzy.app.core.model.Track
import com.dezzy.app.core.prefs.DezzyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Modo vídeo en Android: un segundo ExoPlayer, aparte del de la canción, para que la tubería
 * de audio (resolución, caché, normalización) no se toque nunca. Mientras está activo, el vídeo
 * es lo que suena: la canción queda en pausa debajo y, al volver, sigue donde corresponde.
 *
 * Todo en el hilo principal.
 */
@OptIn(UnstableApi::class)
class AndroidVideoMode internal constructor(
    private val context: Context,
    private val resolver: MusicVideoResolver,
    private val downloader: MusicVideoDownloader,
    private val prefs: DezzyPreferences,
    private val scope: CoroutineScope,
    private val host: Host,
) : VideoModeController {

    /** Lo que el modo vídeo necesita del reproductor de la canción. */
    internal interface Host {
        /** Reproductor de la canción, si ya existe. */
        val audioPlayer: ExoPlayer?
        /** Entrada de la cola que suena (id único) y su canción. */
        fun currentEntryId(): String?
        fun currentTrack(): Track?
        /** Posición de la canción aunque aún no esté cargada (cola recuperada). */
        fun audioPosition(): Long
        fun audioPlayWhenReady(): Boolean
        /** Duración de la canción en ms, o null. */
        fun audioDuration(): Long?
        /** Pausa la canción sin pasar por el modo vídeo. */
        fun pauseAudio()
        /** Vuelve a la canción en [positionMs], sonando si [play]. */
        fun resumeAudio(positionMs: Long, play: Boolean)
        val repeatOne: Boolean
        fun hasNext(): Boolean
        fun skipToNext()
        val volume: Float
        /** El estado que ve la interfaz y la sesión del sistema cambió. */
        fun onVideoStateChanged()
    }

    private var videoPlayer: ExoPlayer? = null
    private var videoModeEntryId: String? = null

    override var pendingVideoId: String? = null

    /** Número de la última petición: las respuestas de peticiones anteriores se tiran. */
    private var videoRequestSeq = 0
    /** Entrada cuyo videoclip se está resolviendo ahora mismo. */
    private var videoLoadingEntryId: String? = null

    private val _active = MutableStateFlow(false)
    private val _loading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _player = MutableStateFlow<VideoPlayerHandle?>(null)
    private val _qualityOptions = MutableStateFlow<List<Int>>(emptyList())
    private val _selectedHeight = MutableStateFlow<Int?>(null)
    private val _playingHeight = MutableStateFlow<Int?>(null)

    override val active: StateFlow<Boolean> = _active.asStateFlow()
    override val loading: StateFlow<Boolean> = _loading.asStateFlow()
    override val error: StateFlow<String?> = _error.asStateFlow()
    override val player: StateFlow<VideoPlayerHandle?> = _player.asStateFlow()
    override val qualityOptions: StateFlow<List<Int>> = _qualityOptions.asStateFlow()
    override val selectedHeight: StateFlow<Int?> = _selectedHeight.asStateFlow()
    override val playingHeight: StateFlow<Int?> = _playingHeight.asStateFlow()
    override val download: StateFlow<VideoDownloadState> get() = downloader.state

    private var videoTrackSelector: DefaultTrackSelector? = null
    /** Videoclip resuelto que suena ahora (para descargar o cambiar de calidad). */
    private var currentResolvedVideo: ResolvedVideo? = null
    private var currentVideoIsHls = false

    // Punto de anclaje para volver al audio: el videoclip puede tener intro o escenas extra,
    // así que al cerrar el vídeo la canción avanza lo que se ha visto desde que se abrió.
    private var videoAnchorAudioMs = 0L
    private var videoAnchorVideoMs = 0L

    private var ticker: Job? = null

    /** El vídeo es el que suena: la sesión y la interfaz siguen su estado. */
    val isDriving: Boolean get() = _active.value && videoPlayer != null
    val drivingPlayer: ExoPlayer? get() = videoPlayer.takeIf { _active.value }

    private suspend fun maxHeightPref(): Int =
        runCatching { VideoQuality.valueOf(prefs.value(VideoMaxQualityKey, VideoQuality.P720.name).first()) }
            .getOrDefault(VideoQuality.P720).maxHeight

    override fun enable() {
        val entryId = host.currentEntryId() ?: return
        val track = host.currentTrack() ?: return
        val mediaId = track.videoId ?: return
        // Ya se está resolviendo ESTA canción: nada que hacer. Si es otra, la petición nueva
        // deja obsoleta la anterior.
        if (_loading.value && videoLoadingEntryId == entryId) return
        if (_active.value && videoModeEntryId == entryId) return
        val seq = ++videoRequestSeq
        videoLoadingEntryId = entryId
        _error.value = null
        _loading.value = true
        scope.launch {
            val maxHeight = maxHeightPref()
            val durationSec = (host.audioDuration()?.div(1000L))?.toInt()
                ?: track.durationSeconds?.toInt()
            val result = resolver.resolveForSong(
                mediaId = mediaId,
                title = track.title,
                artist = track.artists,
                durationSec = durationSec,
                maxHeight = maxHeight,
            )
            // Petición obsoleta: se volvió a «Canción» (o se pidió otro vídeo) mientras se
            // resolvía. Sin esto el vídeo arrancaba igualmente y pausaba la canción.
            if (seq != videoRequestSeq) return@launch
            _loading.value = false
            videoLoadingEntryId = null
            result.onSuccess { video ->
                // Cambió la canción mientras se resolvía: no se engancha un vídeo viejo.
                if (host.currentEntryId() != entryId) return@onSuccess
                val finalPositionMs = host.audioPosition()
                val finalWasPlaying = host.audioPlayWhenReady()
                videoModeEntryId = entryId
                _active.value = true
                videoAnchorAudioMs = finalPositionMs
                videoAnchorVideoMs = finalPositionMs
                _selectedHeight.value = null
                startResolvedVideo(video, finalPositionMs, finalWasPlaying, maxHeight)
            }.onFailure { e ->
                _error.value = MusicVideoResolver.userMessageFor(e)
            }
        }
    }

    override fun retry() {
        currentResolvedVideo?.videoId?.let { resolver.invalidate(it) }
        _error.value = null
        if (_active.value) disable()
        enable()
    }

    override fun clearError() {
        _error.value = null
    }

    override fun disable() {
        // Invalida cualquier resolución en curso y el salto pendiente de «vídeos similares»:
        // «Canción» tiene que ganar aunque el vídeo aún no hubiera empezado.
        videoRequestSeq++
        pendingVideoId = null
        if (_loading.value) {
            _loading.value = false
            videoLoadingEntryId = null
        }
        if (!_active.value && videoPlayer == null) return
        val vp = videoPlayer
        val positionMs = vp?.let { audioPositionForVideo(it.currentPosition) } ?: host.audioPosition()
        val wasPlaying = vp?.playWhenReady ?: host.audioPlayWhenReady()
        stopVideoPlayerInternal()
        _active.value = false
        videoModeEntryId = null
        if (host.currentEntryId() != null) host.resumeAudio(positionMs, wasPlaying)
        host.onVideoStateChanged()
    }

    /**
     * Posición de la CANCIÓN equivalente a [videoPositionMs]: lo visto desde que se abrió el
     * vídeo, sumado a donde iba la canción. Si los dos duran lo mismo (±3 s) el vídeo está
     * alineado y se usa su posición tal cual.
     */
    private fun audioPositionForVideo(videoPositionMs: Long): Long {
        val audioDuration = host.audioDuration()?.takeIf { it > 0 }
        val videoDuration = videoPlayer?.duration?.takeIf { it > 0 && it != C.TIME_UNSET }
        val aligned = audioDuration != null && videoDuration != null &&
            abs(audioDuration - videoDuration) <= 3_000L
        val raw = if (aligned) videoPositionMs else videoAnchorAudioMs + (videoPositionMs - videoAnchorVideoMs)
        val max = audioDuration?.let { (it - 1_000L).coerceAtLeast(0L) } ?: Long.MAX_VALUE
        return raw.coerceIn(0L, max)
    }

    /** Cambió la canción de la cola (siguiente, anterior, toque en la cola…). */
    internal fun onSongChanged() {
        val entryId = host.currentEntryId()
        // Con el vídeo de otra canción puesto: se cierra y, con «Mantener el modo vídeo»
        // (activo por defecto), se abre el de la nueva.
        if (_active.value && videoModeEntryId != entryId) {
            stopVideoPlayerInternal()
            _active.value = false
            videoModeEntryId = null
            host.onVideoStateChanged()
            scope.launch {
                if (prefs.value(VideoKeepModeOnSongChangeKey, true).first()) enable()
            }
        }
        // Salto desde «vídeos similares»: al llegar a esa canción se reactiva solo.
        val videoId = host.currentTrack()?.videoId
        if (pendingVideoId != null && pendingVideoId == videoId) {
            pendingVideoId = null
            if (!_active.value) enable()
        }
    }

    /** Fin del videoclip: sigue la cola como lo haría la canción. */
    private fun onVideoEnded() {
        if (!_active.value) return
        when {
            host.repeatOne -> videoPlayer?.let {
                it.seekTo(0L)
                it.playWhenReady = true
            }
            // El cambio de canción cierra este vídeo y, con «Mantener el modo vídeo», abre el siguiente.
            host.hasNext() -> host.skipToNext()
            else -> {
                stopVideoPlayerInternal()
                _active.value = false
                videoModeEntryId = null
                host.resumeAudio(0L, false)
                host.onVideoStateChanged()
            }
        }
    }

    // Mandos (la sesión y la app los envían aquí mientras el vídeo suena) -----------------------

    fun play() {
        val vp = videoPlayer ?: return
        if (vp.playbackState == Player.STATE_IDLE) vp.prepare()
        if (vp.playbackState == Player.STATE_ENDED) vp.seekTo(0)
        vp.play()
    }

    fun pause() {
        videoPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        videoPlayer?.seekTo(positionMs.coerceAtLeast(0))
        host.onVideoStateChanged()
    }

    fun setVolume(volume: Float) {
        videoPlayer?.volume = volume
    }

    /**
     * Calidad del vídeo: null = automática. En HLS se elige la pista directamente; en los
     * streams progresivos (una sola calidad) se vuelve a resolver con ese tope.
     */
    override fun setQuality(height: Int?) {
        val vp = videoPlayer ?: return
        val selector = videoTrackSelector ?: return
        _selectedHeight.value = height
        if (currentVideoIsHls) {
            val params = selector.buildUponParameters()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            if (height != null) {
                val group = vp.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
                val index = group?.let { g ->
                    (0 until g.length).filter { g.isTrackSupported(it) }
                        .minByOrNull { abs(g.getTrackFormat(it).height - height) }
                }
                if (group != null && index != null) {
                    params.addOverride(TrackSelectionOverride(group.mediaTrackGroup, index))
                }
            }
            selector.setParameters(params)
            return
        }
        // Progresivo: re-resolver con el nuevo tope y seguir en el mismo punto.
        val entryId = videoModeEntryId ?: return
        val track = host.currentTrack() ?: return
        val mediaId = track.videoId ?: return
        val resumeAt = vp.currentPosition
        val resumePlay = vp.playWhenReady
        val maxHeight = height ?: Int.MAX_VALUE
        _loading.value = true
        scope.launch {
            val result = resolver.resolveForSong(
                mediaId = mediaId,
                title = track.title,
                artist = track.artists,
                durationSec = host.audioDuration()?.let { (it / 1000L).toInt() },
                maxHeight = maxHeight,
            )
            _loading.value = false
            if (videoModeEntryId != entryId || !_active.value) return@launch
            result.onSuccess { video -> startResolvedVideo(video, resumeAt, resumePlay, maxHeight) }
                .onFailure { e -> _error.value = MusicVideoResolver.userMessageFor(e) }
        }
    }

    /** Descarga el videoclip actual (con su audio) a Películas/Dezzy. */
    override fun downloadCurrent() {
        val video = currentResolvedVideo
        if (video == null) {
            downloader.reportUnavailable("El video aún no está listo")
            return
        }
        val track = host.currentTrack()
        scope.launch { downloader.download(video, track?.title, track?.artists) }
    }

    override fun clearDownloadResult() = downloader.clearResult()

    private fun refreshVideoQualityState(vp: ExoPlayer) {
        val group = vp.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
        val heights = group?.let { g ->
            (0 until g.length).filter { g.isTrackSupported(it) }
                .map { g.getTrackFormat(it).height }
                .filter { it > 0 }
        }.orEmpty().distinct().sortedDescending()
        _qualityOptions.value = if (currentVideoIsHls || heights.size > 1) {
            heights
        } else {
            // Progresivo: las calidades estándar (se re-resuelve al elegir).
            VideoQuality.entries.map { it.maxHeight }.sortedDescending()
        }
        _playingHeight.value = vp.videoFormat?.height?.takeIf { it > 0 }
    }

    /** Reproduce un videoclip resuelto: HLS primero y, si falla, los streams progresivos. */
    private fun startResolvedVideo(
        video: ResolvedVideo,
        positionMs: Long,
        autoPlay: Boolean,
        maxHeight: Int,
        skipHls: Boolean = false,
    ) {
        val useHls = !skipHls && video.hlsUrl != null
        val progressiveUrl = video.videoUrl ?: video.muxedUrl
        val url = (if (useHls) video.hlsUrl else progressiveUrl) ?: run {
            _error.value = "No se puede reproducir este videoclip."
            disable()
            return
        }
        val audioUrl = if (!useHls && url == video.videoUrl) video.audioUrl else null
        currentResolvedVideo = video
        val retryProgressive: (Long, Boolean) -> Unit = { resumeAt, resumePlay ->
            if (_active.value) startResolvedVideo(video, resumeAt, resumePlay, maxHeight, skipHls = true)
        }
        startVideoPlayer(
            videoUrl = url,
            audioUrl = audioUrl,
            positionMs = positionMs,
            autoPlay = autoPlay,
            isHls = useHls,
            maxHeight = maxHeight,
            resolvedVideoId = video.videoId,
            onFatalError = retryProgressive.takeIf { useHls && progressiveUrl != null },
            songMediaId = host.currentTrack()?.videoId,
        )
    }

    private fun startVideoPlayer(
        videoUrl: String,
        audioUrl: String?,
        positionMs: Long,
        autoPlay: Boolean,
        isHls: Boolean,
        maxHeight: Int,
        resolvedVideoId: String?,
        onFatalError: ((Long, Boolean) -> Unit)?,
        songMediaId: String?,
    ) {
        stopVideoPlayerInternal()
        host.pauseAudio()
        currentVideoIsHls = isHls

        // Caché propia del modo vídeo: nunca toca las de audio.
        val mediaSourceFactory = DefaultMediaSourceFactory(MusicVideoCache.dataSourceFactory(context))
        val manualHeight = _selectedHeight.value
        val trackSelector = DefaultTrackSelector(context).apply {
            // Con calidad manual en HLS el tope se quita y la pista se fija al conocerse las pistas.
            val cap = if (isHls && manualHeight != null) Int.MAX_VALUE else maxHeight
            setParameters(buildUponParameters().setMaxVideoSize(Int.MAX_VALUE, cap))
        }
        videoTrackSelector = trackSelector
        var manualOverrideApplied = false

        val vp = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            // Foco de audio propio (llamadas, otras apps) y pausa al desconectar auriculares.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        videoPlayer = vp
        vp.apply {
            // Misma sesión de audio que la canción: los efectos del sistema y los visualizadores
            // también se aplican al vídeo.
            runCatching { host.audioPlayer?.audioSessionId?.takeIf { it > 0 }?.let { setAudioSessionId(it) } }
            volume = host.volume
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        // Diferido: no se libera un reproductor dentro de su propio aviso.
                        scope.launch { onVideoEnded() }
                    }
                    if (videoPlayer === vp) host.onVideoStateChanged()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (videoPlayer !== vp) return
                    if (isPlaying) startTicker()
                    host.onVideoStateChanged()
                }

                override fun onTracksChanged(tracks: Tracks) {
                    val self = videoPlayer ?: return
                    if (isHls && manualHeight != null && !manualOverrideApplied &&
                        tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO }
                    ) {
                        manualOverrideApplied = true
                        setQuality(manualHeight)
                    }
                    refreshVideoQualityState(self)
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.height > 0) _playingHeight.value = videoSize.height
                }

                override fun onPlayerError(error: PlaybackException) {
                    resolvedVideoId?.let { resolver.invalidate(it) }
                    if (onFatalError != null) {
                        val resumeAt = currentPosition
                        val resumePlay = playWhenReady
                        // Diferido: no se libera un reproductor dentro de su propio aviso.
                        scope.launch { onFatalError(resumeAt, resumePlay) }
                        return
                    }
                    _error.value = "No se pudo reproducir el videoclip (${error.errorCodeName})."
                }
            })

            val videoItem = MediaItem.Builder()
                .setUri(videoUrl)
                .apply { songMediaId?.let { setMediaId(it) } }
                .apply { if (isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
                .build()
            if (audioUrl != null) {
                setMediaSource(
                    MergingMediaSource(
                        true,
                        mediaSourceFactory.createMediaSource(videoItem),
                        mediaSourceFactory.createMediaSource(MediaItem.fromUri(audioUrl)),
                    ),
                )
            } else {
                setMediaItem(videoItem)
            }
            prepare()
            seekTo(positionMs)
            playWhenReady = autoPlay
        }
        _player.value = ExoVideoHandle(vp)
        host.onVideoStateChanged()
    }

    /** Mientras el vídeo avanza, la interfaz y la sesión ven su posición cada medio segundo. */
    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                val vp = videoPlayer ?: break
                host.onVideoStateChanged()
                if (!vp.isPlaying) break
                delay(500)
            }
        }
    }

    private fun stopVideoPlayerInternal() {
        val vp = videoPlayer
        // Primero se suelta la referencia: así los avisos que dispara stop() ya no creen que
        // el vídeo sigue llevando la sesión.
        videoPlayer = null
        videoTrackSelector = null
        _player.value = null
        _qualityOptions.value = emptyList()
        _playingHeight.value = null
        ticker?.cancel()
        vp?.let {
            it.stop()
            it.release()
        }
    }
}

/** El ExoPlayer del vídeo visto desde la interfaz común. */
@OptIn(UnstableApi::class)
private class ExoVideoHandle(private val exo: ExoPlayer) : VideoPlayerHandle {
    private val wrappers = HashMap<VideoPlayerListener, Player.Listener>()

    override val platformPlayer: Any get() = exo
    override val isPlaying: Boolean get() = exo.isPlaying
    override var playWhenReady: Boolean
        get() = exo.playWhenReady
        set(value) {
            if (value && exo.playbackState == Player.STATE_IDLE) exo.prepare()
            if (value && exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
            exo.playWhenReady = value
        }
    override val playbackState: Int get() = exo.playbackState
    override val currentPosition: Long get() = exo.currentPosition
    override val duration: Long get() = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
    override fun seekTo(positionMs: Long) = exo.seekTo(positionMs.coerceAtLeast(0))

    override fun addListener(listener: VideoPlayerListener) {
        val wrapper = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) =
                listener.onPlayWhenReadyChanged(playWhenReady)
            override fun onPlaybackStateChanged(playbackState: Int) = listener.onPlaybackStateChanged(playbackState)
            override fun onIsPlayingChanged(isPlaying: Boolean) = listener.onIsPlayingChanged(isPlaying)
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) = listener.onPositionDiscontinuity()
        }
        wrappers[listener] = wrapper
        exo.addListener(wrapper)
    }

    override fun removeListener(listener: VideoPlayerListener) {
        wrappers.remove(listener)?.let(exo::removeListener)
    }
}
