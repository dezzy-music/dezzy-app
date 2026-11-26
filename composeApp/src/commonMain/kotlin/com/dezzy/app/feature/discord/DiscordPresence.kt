/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.discord

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.dezzy.app.core.prefs.DezzyPreferences
import com.dezzy.app.core.prefs.SecureStorage
import com.dezzy.app.feature.player.PlaybackState
import com.dezzy.app.feature.player.PlayerController
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Clock

val DiscordEnabledKey = booleanPreferencesKey("discord_enabled")
val DiscordShowCoverKey = booleanPreferencesKey("discord_show_cover")

/** Lo que la pantalla de ajustes necesita saber. */
data class DiscordState(
    val signedIn: Boolean = false,
    val userName: String? = null,
    /** Discord rechazó el token (4004): hay que volver a entrar. */
    val needsRelogin: Boolean = false,
    val connected: Boolean = false,
)

@Serializable
private data class StoredDiscord(val token: String, val userName: String? = null)

private class DiscordAccount(val token: String, val userName: String?)

/**
 * Muestra en el perfil de Discord lo que suena. Vive en su propio scope, así que sigue con la app en segundo plano.
 * Usa la cuenta del usuario por el Gateway (Discord no lo admite oficialmente).
 */
@OptIn(FlowPreview::class)
class DiscordPresence(
    private val player: PlayerController,
    private val storage: SecureStorage,
    private val prefs: DezzyPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gateway = DiscordGateway()
    private val json = Json { ignoreUnknownKeys = true }

    private val account = MutableStateFlow<DiscordAccount?>(null)
    private val needsRelogin = MutableStateFlow(false)
    private var started = false

    val state: StateFlow<DiscordState> = combine(account, needsRelogin, gateway.isReady) { acc, relogin, ready ->
        DiscordState(signedIn = acc != null, userName = acc?.userName, needsRelogin = relogin, connected = ready)
    }.stateIn(scope, SharingStarted.Eagerly, DiscordState())

    private val coverMutex = Mutex()
    private val coverCache = mutableMapOf<String, String?>()
    private var lastSentAt = 0L

    /** Idempotente. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            val saved = readStored()
            if (saved != null) account.value = DiscordAccount(saved.token, saved.userName)
        }
        // Conexión: sólo mientras haya cuenta y esté activado.
        scope.launch {
            combine(account, prefs.value(DiscordEnabledKey, false)) { acc, enabled -> if (enabled) acc else null }
                .distinctUntilChanged { a, b -> a?.token == b?.token }
                .collectLatest { acc ->
                    if (acc == null) {
                        gateway.forgetSession()
                    } else {
                        connectLoop(acc.token)
                    }
                }
        }
        // Presencia: al cambiar canción o play/pausa (o la opción de carátula).
        scope.launch {
            combine(
                player.state.map { it.snapshot() },
                prefs.value(DiscordShowCoverKey, true),
            ) { snap, cover -> snap to cover }
                .distinctUntilChanged()
                .debounce(1_000)
                .collectLatest { (snap, _) ->
                    val trackChanged = snap.trackKey != lastTrackKey
                    if (!trackChanged) {
                        val wait = MIN_GAP_MS - (nowMs() - lastSentAt)
                        if (wait > 0) delay(wait)
                    }
                    pushPresence()
                }
        }
        // Al quedar lista la conexión, publicar lo que suena.
        scope.launch {
            gateway.isReady.filter { it }.collect { pushPresence() }
        }
    }

    /** Valida [token] contra Discord y, si vale, lo guarda. Falla con «No se pudo iniciar sesión». */
    suspend fun signIn(token: String): Result<String> {
        val clean = token.trim().trim('"')
        val failure = Result.failure<String>(IllegalStateException("No se pudo iniciar sesión"))
        if (clean.isEmpty()) return failure
        val name = try {
            val resp = gateway.client.get("https://discord.com/api/v10/users/@me") {
                header(HttpHeaders.Authorization, clean)
                timeout { requestTimeoutMillis = 10_000 }
            }
            if (!resp.status.isSuccess()) return failure
            val obj = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            obj["global_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: obj["username"]?.jsonPrimitive?.contentOrNull
                ?: "Discord"
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return failure
        }
        storage.set(STORAGE_KEY, json.encodeToString(StoredDiscord.serializer(), StoredDiscord(clean, name)))
        gateway.forgetSession()
        needsRelogin.value = false
        account.value = DiscordAccount(clean, name)
        return Result.success(name)
    }

    /** Borra la cuenta guardada (sólo lo pide el usuario). */
    suspend fun signOut() {
        storage.remove(STORAGE_KEY)
        gateway.forgetSession()
        needsRelogin.value = false
        account.value = null
    }

    private suspend fun readStored(): StoredDiscord? {
        val raw = try {
            storage.get(STORAGE_KEY)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return null
        return try {
            json.decodeFromString(StoredDiscord.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun connectLoop(token: String) {
        var backoff = MIN_BACKOFF_MS
        while (true) {
            val outcome = gateway.runSession(token) { buildActivities(player.state.value, allowFetch = false) }
            if (outcome.invalidToken) {
                needsRelogin.value = true
                return
            }
            if (outcome.reachedReady) {
                needsRelogin.value = false
                backoff = MIN_BACKOFF_MS
            }
            delay(backoff)
            backoff = min(backoff * 2, MAX_BACKOFF_MS)
        }
    }

    private var lastTrackKey: String? = null

    private suspend fun pushPresence() {
        val st = player.state.value
        lastTrackKey = st.snapshot().trackKey
        lastSentAt = nowMs()
        gateway.sendPresence(buildActivities(st, allowFetch = true))
    }

    private data class Snapshot(val trackKey: String?, val playing: Boolean)

    private fun PlaybackState.snapshot(): Snapshot {
        val t = queue.current?.track
        return Snapshot(t?.let { it.videoId ?: it.title }, isPlaying && t != null)
    }

    private suspend fun buildActivities(st: PlaybackState, allowFetch: Boolean): JsonArray {
        val track = st.queue.current?.track
        if (track == null || !st.isPlaying) return JsonArray(emptyList())
        val showCover = prefs.value(DiscordShowCoverKey, true).first()
        val coverPath: String? = if (showCover) coverFor(track.thumbnail, allowFetch) else null

        val now = nowMs()
        val start = now - max(0L, st.positionMs)
        val durationMs = if (st.durationMs > 0) st.durationMs else (track.durationSeconds ?: 0L) * 1000L
        val activity = buildJsonObject {
            put("application_id", APPLICATION_ID)
            put("name", "Dezzy")
            put("type", 2)
            put("details", clip(track.title))
            put("state", clip(track.artists.ifBlank { "Dezzy" }))
            put("timestamps", buildJsonObject {
                put("start", start)
                if (durationMs > 0) put("end", start + durationMs)
            })
            if (coverPath != null) {
                put("assets", buildJsonObject {
                    put("large_image", "mp:$coverPath")
                    put("large_text", clip(track.album ?: track.title))
                })
            }
            val id = track.videoId
            if (!id.isNullOrBlank()) {
                put("buttons", JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive("Escuchar"))))
                put("metadata", buildJsonObject {
                    put(
                        "button_urls",
                        JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive("https://music.youtube.com/watch?v=$id"))),
                    )
                })
            }
        }
        return JsonArray(listOf<JsonElement>(activity))
    }

    /** Sube la carátula como recurso externo de Discord. Sin permiso de red o si falla: sin carátula. */
    private suspend fun coverFor(url: String?, allowFetch: Boolean): String? {
        if (url.isNullOrBlank() || !url.startsWith("https://")) return null
        val acc = account.value ?: return null
        coverMutex.withLock {
            if (coverCache.containsKey(url)) return coverCache[url]
            if (!allowFetch) return null
            val path = try {
                val resp = gateway.client.post("https://discord.com/api/v10/applications/$APPLICATION_ID/external-assets") {
                    header(HttpHeaders.Authorization, acc.token)
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("urls", JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive(url)))) }.toString())
                    timeout { requestTimeoutMillis = 10_000 }
                }
                if (resp.status.isSuccess()) {
                    json.parseToJsonElement(resp.bodyAsText()).jsonArray.firstOrNull()
                        ?.jsonObject?.get("external_asset_path")?.jsonPrimitive?.contentOrNull
                } else {
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            coverCache[url] = path
            return path
        }
    }

    private fun clip(s: String): String = s.take(128).padEnd(2, ' ')

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    private companion object {
        const val STORAGE_KEY = "account_discord"
        /** Id público de la aplicación de Dezzy en Discord (no es un secreto). */
        const val APPLICATION_ID = "1165706613961789445"
        const val MIN_GAP_MS = 15_000L
        const val MIN_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 60_000L
    }
}
