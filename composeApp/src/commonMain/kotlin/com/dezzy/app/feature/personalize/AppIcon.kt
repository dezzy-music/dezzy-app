/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.personalize

import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dezzy.app.core.prefs.DezzyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Icono de la app: dos formas (d·play, la predeterminada, y la estrella de siempre) y tres
 * colores libres (fondo, figura y acento). Dentro de la app se dibuja con los colores exactos;
 * el launcher de Android sólo admite iconos del APK, así que usa la paleta más parecida
 * (2 formas × paletas como activity-alias, ver [LauncherIcons]).
 */
enum class AppIconShape(val id: String, val label: String) {
    D_PLAY("d", "d · play"),
    STAR("star", "Estrella");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: D_PLAY
    }
}

enum class AppIconFinish {
    SOLID,
    NOTHING_DOTS,
    LIQUID_GLASS_DARK,
}

/** Colores en ARGB (Int), igual que se guardan en las preferencias. */
@Immutable
data class AppIconPalette(
    val name: String,
    val bg: Int,
    val fg: Int,
    val ac: Int,
    val finish: AppIconFinish = AppIconFinish.SOLID,
)

@Immutable
data class AppIconStyle(
    val shape: AppIconShape,
    val bg: Int,
    val fg: Int,
    val ac: Int,
    val finish: AppIconFinish = AppIconFinish.SOLID,
) {
    /** Paleta del APK idéntica a estos colores y acabado, o -1 si son colores propios. */
    val paletteIndex: Int
        get() = AppIcon.PALETTES.indexOfFirst {
            it.bg == bg && it.fg == fg && it.ac == ac && it.finish == finish
        }

    /** Paleta del APK más parecida (si coincide exactamente usa 0..10; colores propios usan 0..7). */
    val nearestPalette: Int
        get() {
            val exact = paletteIndex
            if (exact >= 0) return exact
            return (0 until AppIcon.CLASSIC_PALETTE_COUNT).minBy { i ->
                val p = AppIcon.PALETTES[i]
                3 * dist(p.bg, bg) + 2 * dist(p.fg, fg) + dist(p.ac, ac)
            }
        }

    companion object {
        val Default = AppIconStyle(
            AppIconShape.D_PLAY,
            AppIcon.PALETTES[0].bg,
            AppIcon.PALETTES[0].fg,
            AppIcon.PALETTES[0].ac,
            AppIcon.PALETTES[0].finish,
        )

        private fun dist(a: Int, b: Int): Double {
            val la = labOf(a)
            val lb = labOf(b)
            return sqrt((la[0] - lb[0]).pow(2) + (la[1] - lb[1]).pow(2) + (la[2] - lb[2]).pow(2))
        }

        /** sRGB (ARGB Int) → CIE L*a*b* con blanco D65. */
        private fun labOf(argb: Int): DoubleArray {
            fun lin(c: Int): Double {
                val v = c / 255.0
                return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
            }
            val r = lin((argb shr 16) and 0xFF)
            val g = lin((argb shr 8) and 0xFF)
            val b = lin(argb and 0xFF)
            val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
            val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
            val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883
            fun f(t: Double) = if (t > 216.0 / 24389.0) t.pow(1.0 / 3.0) else (24389.0 / 27.0 * t + 16.0) / 116.0
            val fx = f(x)
            val fy = f(y)
            val fz = f(z)
            return doubleArrayOf(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
        }
    }
}

object AppIcon {
    /** Las primeras 8 paletas son las «clásicas»; las colores propios caen siempre en una de ellas. */
    const val CLASSIC_PALETTE_COUNT = 8

    val PALETTES = listOf(
        AppIconPalette("Original", 0xFF7C3AED.toInt(), 0xFF0B0B10.toInt(), 0xFF34D399.toInt()),
        AppIconPalette("Medianoche", 0xFF0B0B10.toInt(), 0xFFF7F5FF.toInt(), 0xFF7C3AED.toInt()),
        AppIconPalette("Dezzy", 0xFFED5564.toInt(), 0xFFFFFFFF.toInt(), 0xFF2A0E14.toInt()),
        AppIconPalette("Lima", 0xFFC6F432.toInt(), 0xFF0B0B10.toInt(), 0xFF7C3AED.toInt()),
        AppIconPalette("Océano", 0xFF0EA5E9.toInt(), 0xFF0B1220.toInt(), 0xFFFDE047.toInt()),
        AppIconPalette("Coral", 0xFFFF7A59.toInt(), 0xFF1B1B1F.toInt(), 0xFFFFF1E6.toInt()),
        AppIconPalette("Bosque", 0xFF14532D.toInt(), 0xFFECFDF5.toInt(), 0xFFFACC15.toInt()),
        AppIconPalette("Papel", 0xFFF7F5FF.toInt(), 0xFF0B0B10.toInt(), 0xFF7C3AED.toInt()),
        AppIconPalette("Nothing OLED", 0xFF07080B.toInt(), 0xFFF4F4F0.toInt(), 0xFFD71920.toInt(), AppIconFinish.NOTHING_DOTS),
        AppIconPalette("Nothing Claro", 0xFFF2F2EC.toInt(), 0xFF121316.toInt(), 0xFFD71920.toInt(), AppIconFinish.NOTHING_DOTS),
        AppIconPalette("Liquid Glass", 0xFF0B0D14.toInt(), 0xFFE2E8F0.toInt(), 0xFF38BDF8.toInt(), AppIconFinish.LIQUID_GLASS_DARK),
    )

    /** Alias del launcher que corresponde a [style]: misma forma y paleta más parecida («d_0», «star_8»…). */
    fun launcherAliasFor(style: AppIconStyle) = "${style.shape.id}_${style.nearestPalette}"

    /** Estilo del icono de un alias del launcher como «star_3». */
    fun styleForLauncherAlias(alias: String?): AppIconStyle {
        val shapeId = alias?.substringBefore('_')
        val palIdx = alias?.substringAfter('_')?.toIntOrNull() ?: 0
        val p = PALETTES.getOrElse(palIdx) { PALETTES[0] }
        return AppIconStyle(AppIconShape.of(shapeId), p.bg, p.fg, p.ac, p.finish)
    }

    /** Todos los alias que debe declarar el manifest: forma × paleta. */
    fun allAliases(): List<String> = AppIconShape.entries.flatMap { s -> PALETTES.indices.map { "${s.id}_$it" } }
}

/**
 * Guarda el estilo del icono en las preferencias de la app y lo expone como flujo: cualquier cambio
 * en Ajustes repinta todos los logos al momento. Claves con los mismos nombres que en Dezzy 3.x.
 */
class AppIconStore(private val prefs: DezzyPreferences) {
    private companion object {
        val ShapeKey = stringPreferencesKey("appIconShape")
        val BgKey = intPreferencesKey("appIconBg")
        val FgKey = intPreferencesKey("appIconFg")
        val AcKey = intPreferencesKey("appIconAc")
        val FinishKey = stringPreferencesKey("appIconFinish")

        /** Alias del launcher activo («d_0», «star_8»…), guardado al pulsar «Aplicar al launcher». */
        val LauncherKey = stringPreferencesKey("appIconLauncher")
    }

    val style: Flow<AppIconStyle> = combine(
        prefs.value(ShapeKey, AppIconShape.D_PLAY.id),
        prefs.value(BgKey, AppIconStyle.Default.bg),
        prefs.value(FgKey, AppIconStyle.Default.fg),
        prefs.value(AcKey, AppIconStyle.Default.ac),
        prefs.value(FinishKey, AppIconFinish.SOLID.name),
    ) { shape, bg, fg, ac, finish ->
        AppIconStyle(
            shape = AppIconShape.of(shape),
            bg = bg,
            fg = fg,
            ac = ac,
            finish = runCatching { AppIconFinish.valueOf(finish) }.getOrDefault(AppIconFinish.SOLID),
        )
    }.distinctUntilChanged()

    /** Alias del launcher aplicado la última vez desde Ajustes (null si nunca). */
    val launcherAlias: Flow<String?> = prefs.value(LauncherKey, "").map { it.ifEmpty { null } }

    suspend fun current(): AppIconStyle = style.first()

    suspend fun save(style: AppIconStyle) {
        prefs.set(ShapeKey, style.shape.id)
        prefs.set(BgKey, style.bg)
        prefs.set(FgKey, style.fg)
        prefs.set(AcKey, style.ac)
        prefs.set(FinishKey, style.finish.name)
    }

    suspend fun saveLauncherAlias(alias: String) {
        prefs.set(LauncherKey, alias)
    }
}
