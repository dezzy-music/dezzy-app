/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.kit

import androidx.compose.runtime.staticCompositionLocalOf
import com.dezzy.app.core.database.MusicRepository
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.personalize.SongMod
import com.dezzy.app.feature.personalize.withMods
import com.dezzy.app.feature.lyrics.LyricsManager
import com.dezzy.app.feature.lyrics.LyricsState
import com.dezzy.app.feature.lyrics.toLrc
import com.dezzy.app.feature.video.VideoDownloadState
import com.dezzy.app.feature.video.VideoModeController
import com.dezzy.app.feature.video.VideoPlayerHandle
import com.dezzy.app.feature.player.PlayerController
import com.dezzy.app.feature.player.RepeatMode
import com.dezzy.app.source.youtube.YouTubeMusicClient
import com.dezzy.app.ui.navigation.Navigator
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.navigation.SettingsPage
import com.dezzy.app.ui.navigation.Tab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/*
 * Kit de los estilos de reproductor (fase 5c). Da a los 13 estilos portados las mismas piezas que
 * usaban en Dezzy 3.x —conexión con el reproductor, metadatos, navegación por rutas, hoja del
 * reproductor— construidas sobre el reproductor nuevo, para que el código de cada estilo cambie
 * lo mínimo.
 */

// ── Metadatos ────────────────────────────────────────────────────────────────────────────────

data class MediaArtist(val id: String?, val name: String)

data class MediaAlbum(val id: String?, val title: String)

/** Lo que el reproductor muestra de la canción actual. */
data class MediaMetadata(
    val id: String,
    val title: String,
    val artists: List<MediaArtist>,
    val album: MediaAlbum? = null,
    val thumbnailUrl: String? = null,
    /** Duración en segundos; -1 si no se sabe. */
    val duration: Int = -1,
    val explicit: Boolean = false,
    val track: Track,
)

fun Track.toMediaMetadata(): MediaMetadata = MediaMetadata(
    id = videoId ?: spotifyId ?: title,
    title = title,
    artists = artists.split(", ", " & ", " y ").map { it.trim() }.filter { it.isNotEmpty() }
        .ifEmpty { listOf(artists) }.map { MediaArtist(null, it) },
    album = album?.let { MediaAlbum(null, it) },
    thumbnailUrl = thumbnail,
    duration = durationSeconds?.toInt() ?: -1,
    track = this,
)

/** Datos de biblioteca de la canción actual (me gusta, año). */
data class CurrentSong(val song: SongInfo, val album: AlbumInfo? = null)
data class SongInfo(val liked: Boolean, val year: Int? = null)
data class AlbumInfo(val year: Int? = null)

/** Una entrada de la cola tal como la leen los carruseles de carátulas. */
class QueueWindow(val mediaItem: QueueMediaItem)
class QueueMediaItem(val metadata: MediaMetadata?)

/** Letra de la canción actual (llega en la fase 7). */
data class LyricsEntity(val id: String, val lyrics: String) {
    companion object { const val LYRICS_NOT_FOUND = "LYRICS_NOT_FOUND" }
}

/** Formato del audio que suena (códec y bitrate). */
data class AudioFormat(val mimeType: String?, val bitrate: Int?)

/** Mismas constantes que usaban los estilos (valores de Media3). */
object Player {
    const val STATE_IDLE = 1
    const val STATE_BUFFERING = 2
    const val STATE_READY = 3
    const val STATE_ENDED = 4
    const val REPEAT_MODE_OFF = 0
    const val REPEAT_MODE_ONE = 1
    const val REPEAT_MODE_ALL = 2
}

/** «3:07» o «1:02:44». */
fun makeTimeString(ms: Long?): String {
    if (ms == null || ms < 0) return ""
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}

/** Pide la carátula a más resolución cuando el servidor lo permite (googleusercontent / ytimg). */
fun String.highRes(size: Int = 1080): String = when {
    contains("googleusercontent.com") || contains("ggpht.com") ->
        replace(Regex("=w\\d+-h\\d+[^&]*$"), "=w$size-h$size-l90-rj").let { if (it == this && !contains('=')) "$this=w$size-h$size" else it }
    contains("i.ytimg.com") -> replace(Regex("/(default|mqdefault|hqdefault|sddefault)\\.jpg"), "/maxresdefault.jpg")
    else -> this
}

// ── Conexión con el reproductor ──────────────────────────────────────────────────────────────

/** Mandos de bajo nivel que los estilos llamaban sobre el reproductor. */
class PlayerFacade internal constructor(private val c: PlayerController) {
    val playbackState: Int get() = c.state.value.playbackStateInt()
    val currentPosition: Long get() = c.state.value.positionMs
    val duration: Long get() = c.state.value.durationMs
    val audioSessionId: Int get() = c.audioSessionId
    var playWhenReady: Boolean
        get() = c.state.value.isPlaying
        set(value) = if (value) c.play() else c.pause()
    var shuffleModeEnabled: Boolean
        get() = c.state.value.queue.shuffled
        set(value) { if (value != c.state.value.queue.shuffled) c.toggleShuffle() }

    fun play() = c.play()
    fun pause() = c.pause()
    fun togglePlayPause() = c.togglePlay()
    fun seekTo(positionMs: Long) = c.seekTo(positionMs)
    fun seekToNext() = c.next()
    fun seekToPrevious() = c.previous()
    fun seekToDefaultPosition() = c.seekTo(0)
    fun toggleRepeatMode() = c.cycleRepeat()
}

private fun com.dezzy.app.feature.player.PlaybackState.playbackStateInt(): Int = when {
    queue.current == null -> Player.STATE_IDLE
    isBuffering -> Player.STATE_BUFFERING
    !isPlaying && durationMs > 0 && positionMs >= durationMs - 300 -> Player.STATE_ENDED
    else -> Player.STATE_READY
}

/** Volumen de la app como lo leían los estilos (`service.playerVolume`). */
class PlayerServiceCompat internal constructor(c: PlayerController, scope: CoroutineScope) {
    val playerVolume = MutableStateFlow(c.state.value.volume)

    init {
        scope.launch { playerVolume.drop(1).collect { c.setVolume(it) } }
    }
}

/**
 * Lo que cada estilo lee y manda: el equivalente de la conexión con el reproductor de Dezzy 3.x.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    val controller: PlayerController,
    private val repository: MusicRepository,
    val client: YouTubeMusicClient,
    private val scope: CoroutineScope,
    /** Aviso corto abajo. */
    val toast: (String) -> Unit,
    /** Mods de canción (título, artista, carátula) que se aplican a lo que ven todos los reproductores. */
    private val mods: Flow<Map<String, SongMod>> = flowOf(emptyMap()),
    /** Abre la pantalla de una función que llega en otra fase. */
    val pending: (title: String, phase: String) -> Unit,
) {
    private val state = controller.state
    val player = PlayerFacade(controller)
    val service = PlayerServiceCompat(controller, scope)

    val mediaMetadata: StateFlow<MediaMetadata?> =
        combine(state.map { it.queue.current?.track }.distinctUntilChanged(), mods) { t, m -> t?.withMods(m)?.toMediaMetadata() }
            .distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, state.value.queue.current?.track?.toMediaMetadata())
    val isPlaying: StateFlow<Boolean> = state.map { it.isPlaying }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, state.value.isPlaying)
    val playbackState: StateFlow<Int> = state.map { it.playbackStateInt() }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, Player.STATE_IDLE)
    val shuffleModeEnabled: StateFlow<Boolean> = state.map { it.queue.shuffled }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)
    val repeatMode: StateFlow<Int> = state.map {
        when (it.queue.repeat) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, Player.REPEAT_MODE_OFF)
    val queueTitle: StateFlow<String?> = state.map { it.queue.source?.substringAfter(':')?.ifBlank { null } }
        .distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, null)
    val queueWindows: StateFlow<List<QueueWindow>> = combine(state.map { s -> s.queue.items }.distinctUntilChanged(), mods) { items, m ->
        items.map { QueueWindow(QueueMediaItem(it.track.withMods(m).toMediaMetadata())) }
    }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val currentWindowIndex: StateFlow<Int> = state.map { it.queue.index }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, -1)

    val currentSongLiked: StateFlow<Boolean> = mediaMetadata.flatMapLatest { m ->
        if (m == null) flowOf(false) else repository.getFavorites().map { list -> list.any { it.videoId == m.track.videoId } }
    }.stateIn(scope, SharingStarted.Eagerly, false)
    val currentSong: StateFlow<CurrentSong?> = combine(mediaMetadata, currentSongLiked) { m, liked ->
        m?.let { CurrentSong(SongInfo(liked)) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Letra de la canción actual, tal como la deja el gestor de letras (sincronizada en formato LRC, o plana). */
    val currentLyrics: StateFlow<LyricsEntity?> = flow {
        val manager = KoinPlatform.getKoin().get<LyricsManager>()
        manager.start()
        emitAll(manager.state)
    }.map { st ->
        val found = st as? LyricsState.Found
        val synced = found?.result?.synced
        val plain = found?.result?.plain
        when {
            found == null -> null
            !synced.isNullOrEmpty() -> LyricsEntity(found.videoId, synced.toLrc())
            !plain.isNullOrBlank() -> LyricsEntity(found.videoId, plain)
            else -> null
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)
    /** Códec y bitrate de lo que suena, según lo último que resolvió el resolvedor de streams. */
    val currentFormat: StateFlow<AudioFormat?> = run {
        val resolver = runCatching {
            KoinPlatform.getKoin().getOrNull<com.dezzy.app.source.stream.StreamUrlResolver>()
        }.getOrNull()
        if (resolver == null) {
            MutableStateFlow<AudioFormat?>(null).asStateFlow()
        } else {
            combine(mediaMetadata, resolver.resolved) { meta, map ->
                meta?.id?.let { map[it] }?.let { AudioFormat(it.mimeType, it.bitrateKbps * 1000) }
            }.stateIn(scope, SharingStarted.Eagerly, null)
        }
    }

    // Modo vídeo: lo lleva el motor; donde no hay (escritorio), todo queda apagado.
    private val video: VideoModeController? = controller.video
    val videoModeActive: StateFlow<Boolean> = video?.active ?: MutableStateFlow(false).asStateFlow()
    val videoModeLoading: StateFlow<Boolean> = video?.loading ?: MutableStateFlow(false).asStateFlow()
    val videoModeError: StateFlow<String?> = video?.error ?: MutableStateFlow<String?>(null).asStateFlow()
    val videoPlayerInstance: StateFlow<VideoPlayerHandle?> = video?.player ?: MutableStateFlow<VideoPlayerHandle?>(null).asStateFlow()
    val videoQualityOptions: StateFlow<List<Int>> = video?.qualityOptions ?: MutableStateFlow(emptyList<Int>()).asStateFlow()
    val videoSelectedHeight: StateFlow<Int?> = video?.selectedHeight ?: MutableStateFlow<Int?>(null).asStateFlow()
    val videoPlayingHeight: StateFlow<Int?> = video?.playingHeight ?: MutableStateFlow<Int?>(null).asStateFlow()
    val videoDownloadState: StateFlow<VideoDownloadState> = video?.download ?: MutableStateFlow<VideoDownloadState>(VideoDownloadState.Idle).asStateFlow()
    fun enableVideoMode() { video?.enable() ?: toast("El modo vídeo no está disponible aquí") }
    fun disableVideoMode() { video?.disable() }
    fun retryVideoMode() { video?.retry() }
    fun clearVideoModeError() { video?.clearError() }
    fun setVideoQuality(height: Int?) { video?.setQuality(height) }
    fun downloadCurrentVideo() { video?.downloadCurrent() }
    fun clearVideoDownloadResult() { video?.clearDownloadResult() }
    /** Pide reactivar el modo vídeo al llegar a la canción [videoId] («vídeos similares»). */
    fun setPendingVideoMode(videoId: String?) { video?.pendingVideoId = videoId }

    fun seekToNext() = controller.next()
    fun seekToPrevious() = controller.previous()

    fun toggleLike() {
        val track = mediaMetadata.value?.track ?: return
        val liked = currentSongLiked.value
        scope.launch { repository.setFavorite(track, !liked) }
    }

    /** Reproduce o, si terminó, vuelve a empezar. */
    fun playOrReplay() {
        if (player.playbackState == Player.STATE_ENDED) {
            controller.seekTo(0)
            controller.play()
        } else {
            controller.togglePlay()
        }
    }

    /** Pone una radio a partir de una canción (lo que antes era «cola de YouTube»). */
    fun playQueue(track: Track) = controller.playRadio(track, "radio:${track.title}")
}

val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { null }

// ── Hoja del reproductor y navegación ────────────────────────────────────────────────────────

/** La hoja del reproductor expandido: los estilos sólo la pliegan. */
class BottomSheetState(private val onCollapse: () -> Unit) {
    val isExpanded: Boolean get() = true
    fun collapseSoft() = onCollapse()
    fun collapse() = onCollapse()
}

/**
 * Navegación por las mismas rutas que usaban los estilos («artist/…», «album/…»…). Pliega el
 * reproductor antes de abrir la pantalla.
 */
class NavController(
    private val navigator: Navigator,
    private val client: YouTubeMusicClient,
    private val scope: CoroutineScope,
    private val sheet: BottomSheetState,
    private val toast: (String) -> Unit,
) {
    fun navigate(route: String) {
        val id = route.substringAfter('/', "")
        when {
            route.startsWith("artist/") -> openArtist(id)
            route.startsWith("album/") -> go(Screen.Album(id))
            route.startsWith("online_playlist/") || route.startsWith("playlist/") -> go(Screen.Playlist(id))
            route.startsWith("search/") -> go(Screen.Search(id))
            route == "settings/music_together" -> go(Screen.Together())
            route == "settings" -> go(Screen.Settings(SettingsPage.MAIN))
            route == "settings/appearance" -> go(Screen.Settings(SettingsPage.APPEARANCE))
            route == "settings/player" -> go(Screen.Settings(SettingsPage.PLAYER))
            route == "settings/privacy" -> go(Screen.Settings(SettingsPage.PRIVACY))
            route == "settings/storage" -> go(Screen.Settings(SettingsPage.STORAGE))
            route == "settings/about" -> go(Screen.Settings(SettingsPage.ABOUT))
            route == "settings/accounts" -> go(Screen.Settings(SettingsPage.ACCOUNTS))
            route == "settings/equalizer" -> go(Screen.Settings(SettingsPage.EQUALIZER))
            route == "settings/spotify" -> go(Screen.Settings(SettingsPage.SPOTIFY))
            route == "login" -> go(Screen.Settings(SettingsPage.YOUTUBE_LOGIN))
            route == "spotify_login" -> go(Screen.Settings(SettingsPage.SPOTIFY_LOGIN))
            route == "dj_dezzy" -> go(Screen.Dj)
            route.startsWith("songdna/") -> go(Screen.SongDna(id))
            else -> go(Screen.Pending(route, "posterior"))
        }
    }

    /** Abre un artista por id de canal («UC…») o, si sólo hay nombre, lo busca. */
    fun openArtist(idOrName: String) {
        if (idOrName.startsWith("UC") || idOrName.startsWith("MPLA")) {
            go(Screen.Artist(idOrName))
            return
        }
        scope.launch {
            val found = client.findArtistId(idOrName).getOrNull()
            if (found != null) go(Screen.Artist(found)) else toast("No encontré a $idOrName")
        }
    }

    fun openArtist(artist: MediaArtist) = openArtist(artist.id ?: artist.name)

    private fun go(screen: Screen) {
        sheet.collapseSoft()
        navigator.push(screen)
    }
}
