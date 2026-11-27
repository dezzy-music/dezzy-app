/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.dj

import androidx.datastore.preferences.core.stringPreferencesKey
import com.dezzy.app.core.prefs.DezzyPreferences
import com.dezzy.app.core.prefs.SecureStorage
import com.dezzy.app.feature.together.TogetherNameKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** Como [runCatching] pero deja pasar la cancelación de la corrutina. */
internal inline fun <T> djCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

/**
 * Ajustes del DJ. Las claves de Gemini y de la voz (BYOK) van en [SecureStorage]; el resto en
 * [DezzyPreferences] (el nombre del DJ reutiliza `djName`, la clave que ya tenía la app).
 */
class DjSettings(
    private val prefs: DezzyPreferences,
    private val secure: SecureStorage,
) {
    /** Nombre propio del DJ («DJ <nombre>»); vacío = «DJ Dezzy». */
    val name: Flow<String> = prefs.djName

    suspend fun setName(value: String) = prefs.set(DezzyPreferences.KEY_DJ_NAME, value.trim().take(24))

    /** "auto", "es" o "en". */
    val language: Flow<String> = prefs.value(KEY_LANGUAGE, "auto")

    suspend fun setLanguage(value: String) = prefs.set(KEY_LANGUAGE, value)

    /** "radio", "party" o "chill". */
    val style: Flow<String> = prefs.value(KEY_STYLE, "radio")

    suspend fun setStyle(value: String) = prefs.set(KEY_STYLE, value)

    /** "start" (sólo al empezar), "blocks" (al cambiar de bloque) o "often" (cada 2 canciones). */
    val talk: Flow<String> = prefs.value(KEY_TALK, "blocks")

    suspend fun setTalk(value: String) = prefs.set(KEY_TALK, value)

    /** Proveedor de voz: [DjVoice.FREE], [DjVoice.ELEVENLABS] u [DjVoice.OPENAI]. */
    val provider: Flow<String> = prefs.value(KEY_PROVIDER, DjVoice.FREE)

    val voice: Flow<String> = prefs.value(KEY_VOICE, "")

    val voiceName: Flow<String> = prefs.value(KEY_VOICE_NAME, "")

    /** Lo que el oyente contó que le gusta la primera vez ("" = aún no). */
    suspend fun taste(): String = prefs.value(KEY_TASTE, "").first()

    suspend fun setTaste(value: String) = prefs.set(KEY_TASTE, value)

    /** Nombre del oyente (el de las salas de «Escuchar juntos»; "" si no lo ha puesto). */
    suspend fun username(): String = prefs.value(TogetherNameKey, "").first().trim()

    suspend fun djName(): String = prefs.djName.first().trim().let { if (it.isNotBlank()) "DJ $it" else "DJ Dezzy" }

    // ── Claves (cifradas por la plataforma) ─────────────────────────────────────────────────────

    private val _keysVersion = MutableStateFlow(0)

    /** Sube cada vez que se guarda o borra una clave: la interfaz lo observa para refrescarse. */
    val keysVersion: StateFlow<Int> = _keysVersion.asStateFlow()

    suspend fun geminiKey(): String = secure.get(SECURE_GEMINI).orEmpty()

    suspend fun saveGeminiKey(key: String) {
        if (key.isBlank()) secure.remove(SECURE_GEMINI) else secure.set(SECURE_GEMINI, key.trim())
        _keysVersion.value++
    }

    suspend fun ttsKey(): String = secure.get(SECURE_TTS).orEmpty()

    suspend fun currentProvider(): String = provider.first()

    /** Guarda proveedor, clave y voz de una vez (lo que deja el asistente). */
    suspend fun saveVoice(provider: String, key: String, voice: String, voiceName: String) {
        if (provider == DjVoice.FREE || key.isBlank()) secure.remove(SECURE_TTS) else secure.set(SECURE_TTS, key.trim())
        prefs.set(KEY_PROVIDER, provider)
        prefs.set(KEY_VOICE, voice)
        prefs.set(KEY_VOICE_NAME, voiceName)
        _keysVersion.value++
    }

    /** Borra la clave de la voz y vuelve a la voz gratuita. */
    suspend fun forgetVoice() = saveVoice(DjVoice.FREE, "", "", "")

    /** ¿Hay una voz de pago configurada? (proveedor distinto de gratis y con clave). */
    suspend fun hasPaidVoice(): Boolean = currentProvider() != DjVoice.FREE && ttsKey().isNotBlank()

    private companion object {
        val KEY_LANGUAGE = stringPreferencesKey("dj_language")
        val KEY_STYLE = stringPreferencesKey("dj_style")
        val KEY_TALK = stringPreferencesKey("dj_talk")
        val KEY_PROVIDER = stringPreferencesKey("dj_tts_provider")
        val KEY_VOICE = stringPreferencesKey("dj_voice")
        val KEY_VOICE_NAME = stringPreferencesKey("dj_voice_name")
        val KEY_TASTE = stringPreferencesKey("dj_taste")
        const val SECURE_GEMINI = "account_dj_gemini_key"
        const val SECURE_TTS = "account_dj_tts_key"
    }
}
