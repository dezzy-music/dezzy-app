/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dezzy.app.core.prefs.DezzyPreferences
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// ── Estilos y opciones ───────────────────────────────────────────────────────────────────────

/** Los 13 estilos del reproductor, en el orden del selector. */
enum class PlayerDesignStyle { V1, V2, V3, V4, V5, V6, V7, IPOD, SPOTIFY, CAR, APPLE_MUSIC, LIVE_MESH, AURORA }

/** Nombre visible de cada estilo. */
fun PlayerDesignStyle.label(): String = when (this) {
    PlayerDesignStyle.V1 -> "Clásico"
    PlayerDesignStyle.V2 -> "Moderno"
    PlayerDesignStyle.V3 -> "Mínimo"
    PlayerDesignStyle.V4 -> "Cinemático"
    PlayerDesignStyle.V5 -> "Dezzy"
    PlayerDesignStyle.V6 -> "Expresivo"
    PlayerDesignStyle.V7 -> "Deezer"
    PlayerDesignStyle.IPOD -> "iPod"
    PlayerDesignStyle.SPOTIFY -> "Spotify"
    PlayerDesignStyle.CAR -> "Car Mode"
    PlayerDesignStyle.APPLE_MUSIC -> "Apple Music"
    PlayerDesignStyle.LIVE_MESH -> "Dezzy Style"
    PlayerDesignStyle.AURORA -> "Aurora"
}

/** Los 12 estilos de la app actual, en su orden. Slim es «Simple» (nombre antiguo guardado). */
enum class SliderStyle {
    Standard, Wavy, Thick, Circular, Slim, Neon, Capsule, DoubleLine, Dots, AudioWave, ShootingStar, Aurora,
}
enum class PlayerBackgroundStyle { DEFAULT, GRADIENT, GLOW, COLORING, CUSTOM }
enum class AuroraMode { DYNAMIC, CUSTOM }
enum class IpodThemeMode { DYNAMIC, CLASSIC }

/** Fondo del minirreproductor. */
enum class MiniPlayerBackground { DEFAULT, COVER_COLOR, TRANSPARENT, IMAGE }

/** «Estilo de fondo del mini reproductor» de la app actual (5 opciones, en su orden). */
enum class MiniBackgroundStyle { DEFAULT, BLUR, GRADIENT, GLOW_ANIMATED, DEZZY_STYLE }

fun MiniBackgroundStyle.label(): String = when (this) {
    MiniBackgroundStyle.DEFAULT -> "Default"
    MiniBackgroundStyle.BLUR -> "Blur"
    MiniBackgroundStyle.GRADIENT -> "Gradient"
    MiniBackgroundStyle.GLOW_ANIMATED -> "Glow Animated"
    MiniBackgroundStyle.DEZZY_STYLE -> "Dezzy Style"
}

/** Estilo del minirreproductor: píldora actual o tarjeta ancha (BAR). */
enum class MiniPlayerStyle { PILL, BAR }

/** Fondo de la pantalla de letras (independiente del reproductor). */
enum class LyricsBackground { SAME_AS_PLAYER, COVER_GRADIENT, SOLID, IMAGE }
enum class CanvasMode { NONE, SPOTIFY, SPOTIFY_WITH_FALLBACK, APPLE, APPLE_WITH_FALLBACK }

// ── Claves ───────────────────────────────────────────────────────────────────────────────────

val PlayerDesignStyleKey = stringPreferencesKey("playerDesignStyle")
val SliderStyleKey = stringPreferencesKey("sliderStyle")
val PlayerBackgroundStyleKey = stringPreferencesKey("playerBackgroundStyle")
val PlayerCustomImageUriKey = stringPreferencesKey("playerCustomImageUri")
val PlayerCustomBlurKey = floatPreferencesKey("playerCustomBlur")
val PlayerCustomContrastKey = floatPreferencesKey("playerCustomContrast")
val PlayerCustomBrightnessKey = floatPreferencesKey("playerCustomBrightness")
val MiniPlayerBackgroundKey = stringPreferencesKey("miniPlayerBackground")
val MiniPlayerStyleKey = stringPreferencesKey("miniPlayerStyle")
val MiniBackgroundStyleKey = stringPreferencesKey("miniBackgroundStyle")
val MiniDynamicColorKey = booleanPreferencesKey("miniDynamicColor")
val MiniPlayerImageUriKey =stringPreferencesKey("miniPlayerImageUri")
val LyricsBackgroundKey = stringPreferencesKey("lyricsBackground")
val LyricsBgImageUriKey = stringPreferencesKey("lyricsBgImageUri")
val LyricsBgBlurKey = floatPreferencesKey("lyricsBgBlur")
val LyricsBgDimKey = floatPreferencesKey("lyricsBgDim")
val BlurRadiusKey =floatPreferencesKey("blurRadius")
val DisableBlurKey = booleanPreferencesKey("disableBlur")
val ShowCodecOnPlayerKey = booleanPreferencesKey("showCodecOnPlayer")
val CanvasModeKey = stringPreferencesKey("canvasMode")
val AuroraModeKey = stringPreferencesKey("auroraMode")
val AuroraColor1Key = intPreferencesKey("auroraColor1")
val AuroraColor2Key = intPreferencesKey("auroraColor2")
val AuroraColor3Key = intPreferencesKey("auroraColor3")
val IpodThemeModeKey = stringPreferencesKey("ipodThemeMode")
val DezzyVinylThemeKey = stringPreferencesKey("dezzyVinylTheme")
val DezzyVinylStyleKey = stringPreferencesKey("dezzyVinylStyle")
val DezzyVinylSpeedKey = stringPreferencesKey("dezzyVinylSpeed")
val VisualizerStyleKey = stringPreferencesKey("visualizerStyle")
val VisualizerSwipeKey = booleanPreferencesKey("visualizerSwipe")

// ── Lectura y escritura desde la interfaz ────────────────────────────────────────────────────

/** Estado que lee un ajuste y lo guarda al asignarlo (como en Dezzy 3.x). */
private class PrefState<T>(
    private val read: () -> T,
    private val write: (T) -> Unit,
) : MutableState<T> {
    override var value: T
        get() = read()
        set(v) = write(v)
    override fun component1(): T = value
    override fun component2(): (T) -> Unit = { value = it }
}

@Composable
fun <T> rememberPreference(key: Preferences.Key<T>, defaultValue: T): MutableState<T> {
    val prefs: DezzyPreferences = koinInject()
    val scope = rememberCoroutineScope()
    val current by remember(key) { prefs.value(key, defaultValue) }.collectAsState(defaultValue)
    val holder = remember(key) { mutableStateOf(defaultValue) }
    LaunchedEffect(current) { holder.value = current }
    return remember(key) {
        PrefState(read = { holder.value }, write = { v ->
            holder.value = v
            scope.launch { prefs.set(key, v) }
        })
    }
}

@Composable
inline fun <reified E : Enum<E>> rememberEnumPreference(key: Preferences.Key<String>, defaultValue: E): MutableState<E> {
    val raw = rememberPreference(key, defaultValue.name)
    return remember(raw) {
        object : MutableState<E> {
            override var value: E
                get() = enumValues<E>().firstOrNull { it.name == raw.value } ?: defaultValue
                set(v) { raw.value = v.name }
            override fun component1(): E = value
            override fun component2(): (E) -> Unit = { value = it }
        }
    }
}
