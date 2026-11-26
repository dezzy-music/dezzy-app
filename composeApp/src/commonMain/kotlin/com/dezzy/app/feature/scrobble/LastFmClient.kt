/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.scrobble

import com.dezzy.app.core.crypto.md5Hex
import com.dezzy.app.core.prefs.SecureStorage
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.TimeSource

/** Resultado de enviar algo a un servicio de scrobbling. */
enum class SubmitResult {
    OK,

    /** Fallo pasajero (sin red, servicio caído): se puede reintentar. */
    RETRY,

    /** El servicio lo rechazó (o no hay cuenta): reintentar no sirve. */
    REJECTED,
}

/** Lo mínimo de una canción que se manda a Last.fm y ListenBrainz. */
data class ScrobbleTrack(
    val artist: String,
    val title: String,
    val album: String?,
    val durationSec: Long,
)

/** Clave de la aplicación de Last.fm que pega el usuario (BYOK). */
@Serializable
data class LastFmApp(val key: String, val secret: String)

@Serializable
data class LastFmSession(val name: String, val sessionKey: String)

data class LastFmState(
    val loaded: Boolean = false,
    val app: LastFmApp? = null,
    val session: LastFmSession? = null,
    val scrobbles: Long? = null,
    val connecting: Boolean = false,
    val error: String? = null,
    /** Last.fm dijo que la sesión ya no vale; el usuario tiene que volver a conectar. */
    val needsReconnect: Boolean = false,
)

private class LastFmException(val code: Int, message: String) : Exception(message)

/**
 * Cliente de Last.fm (auth web + scrobbling). La clave de la app y el secreto los pega el usuario;
 * se guardan en [SecureStorage] y nunca se escriben en logs.
 */
class LastFmClient(
    private val http: HttpClient,
    private val storage: SecureStorage,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(LastFmState())
    val state: StateFlow<LastFmState> = _state.asStateFlow()
    private var connectJob: Job? = null

    /** Lee de [SecureStorage] la clave de la app y la sesión. */
    suspend fun load() {
        val app = storage.get(KEY_APP)?.let { runCatching { json.decodeFromString<LastFmApp>(it) }.getOrNull() }
        val session = storage.get(KEY_SESSION)?.let { runCatching { json.decodeFromString<LastFmSession>(it) }.getOrNull() }
        _state.update { it.copy(loaded = true, app = app, session = session) }
        if (session != null && app != null) refreshScrobbleCount()
    }

    /** Guarda la clave y el secreto. Devuelve false (y deja el error) si no tienen pinta de válidos. */
    suspend fun saveApp(key: String, secret: String): Boolean {
        val k = key.trim()
        val s = secret.trim()
        if (!HEX32.matches(k) || !HEX32.matches(s)) {
            _state.update { it.copy(error = "La clave y el secreto de Last.fm tienen 32 caracteres (letras a-f y números)") }
            return false
        }
        val app = LastFmApp(k, s)
        storage.set(KEY_APP, json.encodeToString(LastFmApp.serializer(), app))
        _state.update { it.copy(app = app, error = null) }
        return true
    }

    /** Olvida la clave de la app (sólo si no hay sesión conectada). */
    suspend fun clearApp() {
        if (_state.value.session != null) return
        storage.remove(KEY_APP)
        _state.update { it.copy(app = null, error = null) }
    }

    /**
     * Pide un token y devuelve la dirección que hay que abrir en el navegador; mientras, empieza a
     * sondear la sesión cada 3 s durante 2 min. Null si no se pudo (el motivo queda en el estado).
     */
    suspend fun startConnect(): String? {
        val app = _state.value.app ?: return null
        if (_state.value.connecting) return null
        _state.update { it.copy(connecting = true, error = null) }
        val token = try {
            call("auth.getToken", emptyMap(), app, post = false)["token"]?.jsonPrimitive?.contentOrNull
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(connecting = false, error = describe(e)) }
            return null
        }
        if (token.isNullOrBlank()) {
            _state.update { it.copy(connecting = false, error = "Last.fm no devolvió un token") }
            return null
        }
        connectJob?.cancel()
        connectJob = scope.launch { pollSession(app, token) }
        return "https://www.last.fm/api/auth/?api_key=${app.key.encodeURLParameter()}&token=${token.encodeURLParameter()}"
    }

    /** Deja de esperar la autorización del navegador. */
    fun cancelConnect() {
        connectJob?.cancel()
        connectJob = null
        _state.update { it.copy(connecting = false) }
    }

    private suspend fun pollSession(app: LastFmApp, token: String) {
        val mark = TimeSource.Monotonic.markNow()
        while (mark.elapsedNow().inWholeMilliseconds < CONNECT_TIMEOUT_MS) {
            delay(POLL_MS)
            try {
                val session = call("auth.getSession", mapOf("token" to token), app, post = false)["session"]?.jsonObject
                val name = session?.get("name")?.jsonPrimitive?.contentOrNull
                val key = session?.get("key")?.jsonPrimitive?.contentOrNull
                if (!name.isNullOrBlank() && !key.isNullOrBlank()) {
                    val saved = LastFmSession(name.take(64), key)
                    storage.set(KEY_SESSION, json.encodeToString(LastFmSession.serializer(), saved))
                    _state.update { it.copy(session = saved, connecting = false, error = null, needsReconnect = false) }
                    refreshScrobbleCount()
                    return
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LastFmException) {
                // 4 = token inválido, 15 = caducado: no tiene sentido seguir esperando.
                if (e.code == 4 || e.code == 15) {
                    _state.update { it.copy(connecting = false, error = "La autorización caducó. Vuelve a intentarlo.") }
                    return
                }
                // 14 = aún no ha aceptado; el resto, fallo pasajero.
            } catch (e: Exception) {
                // sin red: se sigue probando
            }
        }
        _state.update { it.copy(connecting = false, error = "No se completó la conexión. Vuelve a intentarlo.") }
    }

    /** Borra la sesión (deja la clave de la app). */
    suspend fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        storage.remove(KEY_SESSION)
        _state.update { it.copy(session = null, scrobbles = null, connecting = false, error = null, needsReconnect = false) }
    }

    /** Actualiza el número de scrobbles (`user.getInfo`). Sin red no cambia nada. */
    suspend fun refreshScrobbleCount() {
        val app = _state.value.app ?: return
        val user = _state.value.session?.name ?: return
        try {
            val n = call("user.getInfo", mapOf("user" to user), app, post = false)["user"]
                ?.jsonObject?.get("playcount")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            if (n != null) _state.update { it.copy(scrobbles = n) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // sin red
        }
    }

    suspend fun updateNowPlaying(track: ScrobbleTrack): SubmitResult =
        submit("track.updateNowPlaying", track, timestampSec = null)

    suspend fun scrobble(track: ScrobbleTrack, timestampSec: Long): SubmitResult {
        val result = submit("track.scrobble", track, timestampSec)
        if (result == SubmitResult.OK) _state.update { s -> s.copy(scrobbles = s.scrobbles?.plus(1)) }
        return result
    }

    private suspend fun submit(method: String, track: ScrobbleTrack, timestampSec: Long?): SubmitResult {
        val app = _state.value.app ?: return SubmitResult.REJECTED
        val session = _state.value.session ?: return SubmitResult.REJECTED
        val params = buildMap {
            put("artist", track.artist)
            put("track", track.title)
            put("sk", session.sessionKey)
            if (timestampSec != null) put("timestamp", timestampSec.toString())
            if (!track.album.isNullOrBlank()) put("album", track.album)
            if (track.durationSec > 0) put("duration", track.durationSec.toString())
        }
        return try {
            call(method, params, app, post = true)
            SubmitResult.OK
        } catch (e: CancellationException) {
            throw e
        } catch (e: LastFmException) {
            when (e.code) {
                // 9 = sesión inválida, 4 = autenticación, 14 = token sin autorizar: hay que volver a conectar.
                4, 9, 14 -> {
                    _state.update { it.copy(needsReconnect = true) }
                    SubmitResult.REJECTED
                }
                // -1 = respuesta ilegible, 11/16 = servicio no disponible, 29 = límite de peticiones.
                -1, 11, 16, 29 -> SubmitResult.RETRY
                else -> SubmitResult.REJECTED
            }
        } catch (e: Exception) {
            SubmitResult.RETRY
        }
    }

    private suspend fun call(method: String, params: Map<String, String>, app: LastFmApp, post: Boolean): JsonObject {
        val all = params + mapOf("method" to method, "api_key" to app.key)
        val form = all + mapOf("api_sig" to sign(all, app.secret), "format" to "json")
        val response = if (post) {
            http.submitForm(API, Parameters.build { form.forEach { (k, v) -> append(k, v) } })
        } else {
            http.get(API) { url { form.forEach { (k, v) -> parameters.append(k, v) } } }
        }
        val obj = try {
            json.parseToJsonElement(response.bodyAsText()).jsonObject
        } catch (e: Exception) {
            throw LastFmException(-1, "Respuesta ilegible de Last.fm")
        }
        val code = obj["error"]?.jsonPrimitive?.intOrNull
        if (code != null) throw LastFmException(code, obj["message"]?.jsonPrimitive?.contentOrNull ?: "Error de Last.fm")
        return obj
    }

    private fun describe(e: Exception): String = when {
        e is LastFmException && e.code == 10 -> "Last.fm no reconoce esa clave de API"
        e is LastFmException && e.code != -1 -> e.message ?: "Error de Last.fm"
        else -> "No se pudo conectar con Last.fm"
    }

    private companion object {
        const val API = "https://ws.audioscrobbler.com/2.0/"
        const val KEY_APP = "account_lastfm_app"
        const val KEY_SESSION = "account_lastfm"
        const val POLL_MS = 3_000L
        const val CONNECT_TIMEOUT_MS = 2 * 60_000L
        val HEX32 = Regex("^[0-9a-fA-F]{32}$")

        /** api_sig: parámetros ordenados por nombre (sin `format`), nombre+valor, más el secreto, en MD5. */
        fun sign(params: Map<String, String>, secret: String): String {
            val base = params.entries
                .filter { it.key != "format" && it.key != "callback" }
                .sortedBy { it.key }
                .joinToString("") { it.key + it.value }
            return md5Hex(base + secret)
        }
    }
}
