/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dezzy.app.core.prefs.DezzyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Preferencias de la red social (claves propias sobre [DezzyPreferences]). */
class P2PSettings(private val prefs: DezzyPreferences) {

    val listeningVisibility: Flow<ListeningVisibility> = prefs.value(KEY_LISTENING, ListeningVisibility.EVERYONE.name)
        .map { runCatching { ListeningVisibility.valueOf(it) }.getOrDefault(ListeningVisibility.EVERYONE) }

    suspend fun setListeningVisibility(value: ListeningVisibility) = prefs.set(KEY_LISTENING, value.name)

    val nearbyVisible: Flow<Boolean> = prefs.value(KEY_NEARBY_VISIBLE, true)

    suspend fun setNearbyVisible(value: Boolean) = prefs.set(KEY_NEARBY_VISIBLE, value)

    internal suspend fun lastInboxSync(): Long = prefs.value(KEY_LAST_SYNC, 0L).first()

    internal suspend fun setLastInboxSync(value: Long) = prefs.set(KEY_LAST_SYNC, value)

    /** Desde cuándo avisar: lo anterior (historial recuperado al restaurar la cuenta) no notifica. 0 = sin fijar. */
    internal suspend fun notifyFrom(): Long = prefs.value(KEY_NOTIFY_FROM, 0L).first()

    internal suspend fun setNotifyFrom(value: Long) = prefs.set(KEY_NOTIFY_FROM, value)

    internal suspend fun dmRelaysPublished(): Boolean = prefs.value(KEY_DM_RELAYS, false).first()

    internal suspend fun setDmRelaysPublished(value: Boolean) = prefs.set(KEY_DM_RELAYS, value)

    private companion object {
        val KEY_LISTENING = stringPreferencesKey("p2p_listening_visibility")
        val KEY_NEARBY_VISIBLE = booleanPreferencesKey("p2p_nearby_visible")
        val KEY_LAST_SYNC = longPreferencesKey("p2p_last_inbox_sync")
        val KEY_NOTIFY_FROM = longPreferencesKey("p2p_notify_from")
        val KEY_DM_RELAYS = booleanPreferencesKey("p2p_dm_relays_published")
    }
}
