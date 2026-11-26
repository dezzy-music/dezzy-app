/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.scrobble

import com.dezzy.app.core.AppVersionName
import com.dezzy.app.core.prefs.SecureStorage
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable
data class ListenBrainzAccount(val token: String, val userName: String)

data class ListenBrainzState(
    val loaded: Boolean = false,
    val account: ListenBrainzAccount? = null,
    val busy: Boolean = false,
    val error: String? = null,
    /** ListenBrainz rechazó el token (401): hay que volver a conectar. */
    val needsReconnect: Boolean = false,
)

/** Cliente de ListenBrainz: token pegado por el usuario, guardado en [SecureStorage]. */
class ListenBrainzClient(
    private val http: HttpClient,
    private val storage: SecureStorage,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(ListenBrainzState())
    val state: StateFlow<ListenBrainzState> = _state.asStateFlow()

    suspend fun load() {
        val account = storage.get(KEY)?.let { runCatching { json.decodeFromString<ListenBrainzAccount>(it) }.getOrNull() }
        _state.update { it.copy(loaded = true, account = account) }
    }

    /** Valida el token con `validate-token` y, si vale, lo guarda. */
    suspend fun connect(token: String) {
        val clean = token.trim()
        if (!TOKEN_FORMAT.matches(clean)) {
            _state.update { it.copy(error = "Ese token no parece válido") }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        try {
            val response = http.get("$API/validate-token") { header(HttpHeaders.Authorization, "Token $clean") }
            val obj = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val valid = obj["valid"]?.jsonPrimitive?.boolean == true
            val user = obj["user_name"]?.jsonPrimitive?.contentOrNull
            if (!valid || user.isNullOrBlank()) {
                _state.update { it.copy(busy = false, error = "ListenBrainz no reconoce ese token") }
                return
            }
            val account = ListenBrainzAccount(clean, user.take(64))
            storage.set(KEY, json.encodeToString(ListenBrainzAccount.serializer(), account))
            _state.update { it.copy(busy = false, account = account, error = null, needsReconnect = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, error = "No se pudo comprobar el token") }
        }
    }

    suspend fun disconnect() {
        storage.remove(KEY)
        _state.update { it.copy(account = null, error = null, needsReconnect = false) }
    }

    suspend fun playingNow(track: ScrobbleTrack): SubmitResult = submit("playing_now", track, listenedAtSec = null)

    suspend fun listen(track: ScrobbleTrack, listenedAtSec: Long): SubmitResult = submit("single", track, listenedAtSec)

    private suspend fun submit(type: String, track: ScrobbleTrack, listenedAtSec: Long?): SubmitResult {
        val account = _state.value.account ?: return SubmitResult.REJECTED
        val listen = buildJsonObject {
            if (listenedAtSec != null) put("listened_at", listenedAtSec)
            put("track_metadata", buildJsonObject {
                put("artist_name", track.artist)
                put("track_name", track.title)
                if (!track.album.isNullOrBlank()) put("release_name", track.album)
                put("additional_info", buildJsonObject {
                    put("media_player", "Dezzy")
                    put("submission_client", "Dezzy")
                    put("submission_client_version", AppVersionName)
                    if (track.durationSec > 0) put("duration_ms", track.durationSec * 1000)
                })
            })
        }
        val body = buildJsonObject {
            put("listen_type", type)
            put("payload", JsonArray(listOf(listen)))
        }
        return try {
            val response = http.post("$API/submit-listens") {
                header(HttpHeaders.Authorization, "Token ${account.token}")
                setBody(TextContent(body.toString(), ContentType.Application.Json))
            }
            val code = response.status.value
            when {
                response.status == HttpStatusCode.OK -> SubmitResult.OK
                response.status == HttpStatusCode.Unauthorized -> {
                    _state.update { it.copy(needsReconnect = true) }
                    SubmitResult.REJECTED
                }
                code == 429 || code >= 500 -> SubmitResult.RETRY
                else -> SubmitResult.REJECTED
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SubmitResult.RETRY
        }
    }

    private companion object {
        const val API = "https://api.listenbrainz.org/1"
        const val KEY = "account_listenbrainz"
        val TOKEN_FORMAT = Regex("^[0-9a-fA-F-]{20,64}$")
    }
}
