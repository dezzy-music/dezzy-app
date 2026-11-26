/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.video

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Ajustes del modo vídeo (mismos nombres que en Dezzy 3.x).
val VideoMaxQualityKey = stringPreferencesKey("videoMaxQuality")
val VideoResizeModeKey = stringPreferencesKey("videoResizeMode")
val VideoKeepModeOnSongChangeKey = booleanPreferencesKey("videoKeepModeOnSongChange")
val VideoPipEnabledKey = booleanPreferencesKey("videoPipEnabled")
val VideoRecentSearchesKey = stringPreferencesKey("video_feed_recent_searches")

/** Tope de calidad del videoclip (ajuste «Calidad máxima del video»). */
enum class VideoQuality(val maxHeight: Int) {
    P360(360),
    P480(480),
    P720(720),
    P1080(1080),
}

/** Cómo encaja el vídeo en su hueco: entero, recortado o estirado. */
enum class VideoResizeMode { FIT, CROP, FILL }

/** Lo que el reproductor de vídeo cuenta a la interfaz (lo que antes daba `Player.Listener`). */
interface VideoPlayerListener {
    fun onPlayWhenReadyChanged(playWhenReady: Boolean) {}
    fun onPlaybackStateChanged(playbackState: Int) {}
    fun onIsPlayingChanged(isPlaying: Boolean) {}
    /** Un salto (doble toque, barra, notificación…). */
    fun onPositionDiscontinuity() {}
}

/**
 * El reproductor del videoclip que suena. Los estados son los de `kit.Player.STATE_*`.
 * [platformPlayer] es el reproductor nativo (ExoPlayer en Android) para pintarlo.
 */
interface VideoPlayerHandle {
    val platformPlayer: Any
    val isPlaying: Boolean
    var playWhenReady: Boolean
    val playbackState: Int
    val currentPosition: Long
    /** Duración en ms; 0 o menos si aún no se sabe. */
    val duration: Long
    fun seekTo(positionMs: Long)
    fun addListener(listener: VideoPlayerListener)
    fun removeListener(listener: VideoPlayerListener)
}

/** Descarga del videoclip a Películas/Dezzy. */
sealed interface VideoDownloadState {
    data object Idle : VideoDownloadState
    /** [progress] entre 0 y 1, o null si no se sabe. */
    data class Running(val title: String, val progress: Float?) : VideoDownloadState
    data class Done(val message: String) : VideoDownloadState
    data class Failed(val message: String) : VideoDownloadState
}

/**
 * Modo vídeo: el videoclip de la canción en un reproductor aparte. Mientras está activo, el
 * vídeo es lo que suena (la canción queda en pausa debajo) y la sesión del sistema lo sigue.
 */
interface VideoModeController {
    val active: StateFlow<Boolean>
    val loading: StateFlow<Boolean>
    val error: StateFlow<String?>
    val player: StateFlow<VideoPlayerHandle?>
    /** Alturas disponibles (1080, 720…), de mayor a menor. */
    val qualityOptions: StateFlow<List<Int>>
    /** Altura elegida a mano; null = automática. */
    val selectedHeight: StateFlow<Int?>
    /** Altura que se está reproduciendo de verdad. */
    val playingHeight: StateFlow<Int?>
    val download: StateFlow<VideoDownloadState>

    /**
     * Al tocar un «vídeo similar», la cola salta a esa canción y, al llegar a ella, se vuelve
     * a activar el modo vídeo solo. Es el videoId de esa canción.
     */
    var pendingVideoId: String?

    fun enable()
    fun disable()
    /** «Reintentar» del aviso: vuelve a resolver sin la caché que falló. */
    fun retry()
    fun clearError()
    fun setQuality(height: Int?)
    fun downloadCurrent()
    fun clearDownloadResult()
}

/** El sistema tiene la app en imagen en imagen (PiP). */
object VideoPipState {
    val inPip = MutableStateFlow(false)
    /** El vídeo puede pasar a PiP al salir de la app (lo decide VideoPipLayer). */
    val eligible = MutableStateFlow(false)
}

/** Pantalla completa del vídeo girada: la actividad no debe girar mientras dure. */
object VideoFullscreenOrientation {
    val active = MutableStateFlow(false)
}
