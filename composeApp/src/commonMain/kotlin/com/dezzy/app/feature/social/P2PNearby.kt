/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social

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
import kotlin.time.Clock

/**
 * Radar «Cerca»: descubre a otras personas con Dezzy a unos metros usando Bluetooth LE.
 * Sin internet, sin GPS y sin servidores: cada dispositivo anuncia su clave pública y escucha
 * las de los demás. Sólo funciona en el móvil; en el escritorio [status] es UNSUPPORTED.
 */
class P2PNearby(
    private val ble: P2PBle,
    private val identity: P2PIdentity,
    private val settings: P2PSettings,
    private val social: () -> P2PSocial,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val found = MutableStateFlow<Map<String, P2PNearbyPerson>>(emptyMap())
    private var pruneJob: Job? = null

    val status: StateFlow<NearbyStatus> get() = ble.status

    private val _people = MutableStateFlow<List<P2PNearbyPerson>>(emptyList())

    /** Gente detectada cerca, la de señal más fuerte primero. */
    val people: StateFlow<List<P2PNearbyPerson>> = _people.asStateFlow()

    /** Permisos que hay que pedir antes de [start] (vacío si no hacen falta). */
    fun requiredPermissions(): List<String> = ble.requiredPermissions()

    fun hasPermissions(): Boolean = ble.hasPermissions()

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    private fun onFound(pubkey: String, rssi: Int) {
        if (pubkey == identity.state.value.pubkeyHex) return
        val isNew = !found.value.containsKey(pubkey)
        found.update { it + (pubkey to P2PNearbyPerson(pubkey, rssi, nowMs())) }
        publish()
        if (isNew) scope.launch { runCatching { social().loadProfiles(listOf(pubkey)) } }
    }

    private fun publish() {
        val cutoff = nowMs() - STALE_MS
        found.update { map -> map.filterValues { it.lastSeen >= cutoff } }
        _people.value = found.value.values.sortedByDescending { it.rssi }
    }

    /** Empieza a anunciarme (si soy visible) y a escuchar a los de alrededor. */
    fun start() {
        val myKey = identity.state.value.pubkeyHex ?: return
        scope.launch {
            val visible = settings.nearbyVisible.first()
            ble.start(myKey, visible) { pubkey, rssi -> onFound(pubkey, rssi) }
        }
        if (pruneJob?.isActive != true) {
            pruneJob = scope.launch {
                while (isActive) {
                    delay(5_000)
                    publish()
                }
            }
        }
    }

    fun stop() {
        pruneJob?.cancel()
        pruneJob = null
        ble.stop()
    }

    private companion object {
        const val STALE_MS = 45_000L
    }
}
