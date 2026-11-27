/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.musicmap

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.dezzy.app.core.prefs.DezzyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.koin.core.scope.Scope
import kotlin.math.round

/** «Compartir mi ubicación en el Mapa musical». Apagado por defecto. */
val MapShareLocationKey = booleanPreferencesKey("music_map_share_location")

/** Lo que cada plataforma aporta: saber si hay permiso y pedir una posición. */
interface MapLocationSource {
    fun hasPermission(): Boolean

    /** Posición actual (red primero, GPS de respaldo) con tiempo límite, o la última conocida; null si no hay. */
    suspend fun current(): Pair<Double, Double>?

    /** Permisos de Android que hay que pedir para usar la ubicación (vacío en el escritorio). */
    val permissions: List<String> get() = emptyList()
}

/** Android: `LocationManager` del sistema, sin Play Services. Escritorio: siempre null. */
expect fun createMapLocationSource(scope: Scope): MapLocationSource

/**
 * Ubicación aproximada para el Mapa musical.
 *
 * - Se redondea a 2 decimales (~1 km): suficiente para el mapa y no revela la dirección exacta.
 * - Se guarda en el dispositivo para que, tras reiniciar la app, la presencia siga saliendo en el
 *   mapa sin volver a abrirlo.
 * - Sólo se publica a los demás ([sharedLocation]) si el usuario activó [shareEnabled].
 */
class MusicMapLocation(
    private val source: MapLocationSource,
    private val store: MusicMapStore,
    private val prefs: DezzyPreferences,
) {
    val shareEnabled: Flow<Boolean> = prefs.value(MapShareLocationKey, false)

    suspend fun setShare(on: Boolean) {
        prefs.set(MapShareLocationKey, on)
        if (!on) clear()
    }

    fun hasPermission(): Boolean = source.hasPermission()

    /** Permisos a pedir con `rememberPermissionRequester`. */
    val permissions: List<String> get() = source.permissions

    /** Última ubicación guardada, o null. */
    suspend fun stored(): Pair<Double, Double>? {
        val raw = store.get(KEY) ?: return null
        val parts = raw.split(',')
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lng = parts[1].toDoubleOrNull() ?: return null
        return lat to lng
    }

    /** Borra la ubicación guardada (al dejar de compartir). */
    suspend fun clear() = store.delete(KEY)

    /**
     * Pide una ubicación nueva; si no llega, usa la guardada. La guarda ya redondeada.
     */
    suspend fun refresh(): Pair<Double, Double>? {
        if (!source.hasPermission()) return stored()
        val fix = runCatching { source.current() }.getOrNull()
        val point = fix?.let { coarse(it.first) to coarse(it.second) } ?: return stored()
        store.put(KEY, "${point.first},${point.second}")
        return point
    }

    /**
     * Lo que debe recibir `P2PPresence.onNowPlaying(song, location)`: la ubicación guardada si el
     * usuario comparte la suya, o null.
     */
    suspend fun sharedLocation(): Pair<Double, Double>? {
        if (!shareEnabled.first()) return null
        return stored()
    }

    private fun coarse(value: Double): Double = round(value * 100.0) / 100.0

    private companion object {
        const val KEY = "my_location"
    }
}
