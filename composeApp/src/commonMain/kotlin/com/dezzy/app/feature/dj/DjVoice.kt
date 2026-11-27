/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.dj

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Proveedores de voz del DJ y datos de cada uno. Las claves son del usuario (BYOK). */
object DjVoice {
    const val FREE = "free"
    const val ELEVENLABS = "elevenlabs"
    const val OPENAI = "openai"

    const val ELEVENLABS_DEFAULT = "EXAVITQu4vr4xnSDxMaL"
    const val OPENAI_DEFAULT = "nova"

    const val GEMINI_KEY_URL = "https://aistudio.google.com/apikey"
    const val ELEVENLABS_KEY_URL = "https://elevenlabs.io/app/settings/api-keys"
    const val OPENAI_KEY_URL = "https://platform.openai.com/api-keys"

    /** Una voz disponible. [previewUrl] sólo existe en ElevenLabs. */
    class Voice(val id: String, val name: String, val description: String, val previewUrl: String? = null)

    /** Resultado de probar una clave. [remaining]: caracteres que quedan (sólo ElevenLabs). */
    class KeyCheck(val ok: Boolean, val message: String, val voices: List<Voice> = emptyList(), val remaining: Long? = null)

    val OPENAI_VOICES = listOf(
        Voice("nova", "Nova", "Cálida y cercana"),
        Voice("coral", "Coral", "Amable y clara"),
        Voice("shimmer", "Shimmer", "Suave y brillante"),
        Voice("sage", "Sage", "Tranquila y serena"),
        Voice("alloy", "Alloy", "Neutra y equilibrada"),
        Voice("ash", "Ash", "Grave y relajada"),
        Voice("echo", "Echo", "Masculina y clara"),
        Voice("onyx", "Onyx", "Masculina y profunda"),
        Voice("fable", "Fable", "Expresiva, de narrador"),
    )

    fun keyUrl(provider: String): String = when (provider) {
        ELEVENLABS -> ELEVENLABS_KEY_URL
        OPENAI -> OPENAI_KEY_URL
        else -> GEMINI_KEY_URL
    }

    fun providerName(provider: String): String = when (provider) {
        ELEVENLABS -> "ElevenLabs"
        OPENAI -> "OpenAI"
        else -> "Voz gratuita"
    }

    fun defaultVoice(provider: String): String = when (provider) {
        ELEVENLABS -> ELEVENLABS_DEFAULT
        OPENAI -> OPENAI_DEFAULT
        else -> ""
    }

    /** La voz guardada, o la de por defecto del proveedor si no hay ninguna. */
    fun voiceFor(provider: String, saved: String): String = saved.ifBlank { defaultVoice(provider) }
}

/** Llamadas a los proveedores de voz y a Gemini (comprobar clave). Todo con Ktor y la clave del usuario. */
class DjVoiceApi(private val http: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Comprueba una clave de Gemini con una petición mínima. */
    suspend fun checkGemini(key: String): DjVoice.KeyCheck = djCatching {
        val response = http.post("https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent") {
            header("x-goog-api-key", key.trim())
            contentType(ContentType.Application.Json)
            timeout { requestTimeoutMillis = 20_000 }
            setBody(
                buildJsonObject {
                    put("contents", JsonArray(listOf(buildJsonObject {
                        put("parts", JsonArray(listOf(buildJsonObject { put("text", "Responde solo: ok") })))
                    })))
                }.toString(),
            )
        }
        when {
            response.status.isSuccess() -> DjVoice.KeyCheck(true, "Clave de Gemini correcta.")
            response.status.value == 400 || response.status.value == 403 -> DjVoice.KeyCheck(false, "Gemini no acepta esa clave.")
            response.status.value == 429 -> DjVoice.KeyCheck(true, "Clave correcta (ahora mismo Gemini pide esperar un poco).")
            else -> DjVoice.KeyCheck(false, "Gemini respondió ${response.status.value}.")
        }
    }.getOrElse { DjVoice.KeyCheck(false, "No se pudo conectar con Gemini.") }

    /** Comprueba la clave de [provider] y devuelve sus voces. */
    suspend fun check(provider: String, key: String): DjVoice.KeyCheck = djCatching {
        when (provider) {
            DjVoice.ELEVENLABS -> checkElevenLabs(key.trim())
            DjVoice.OPENAI -> checkOpenAi(key.trim())
            else -> DjVoice.KeyCheck(true, "La voz gratuita no necesita clave.")
        }
    }.getOrElse { DjVoice.KeyCheck(false, "No se pudo conectar. Revisa tu conexión.") }

    private suspend fun checkElevenLabs(key: String): DjVoice.KeyCheck {
        val response = http.get("https://api.elevenlabs.io/v1/voices") {
            header("xi-api-key", key)
            timeout { requestTimeoutMillis = 20_000 }
        }
        if (response.status.value == 401) return DjVoice.KeyCheck(false, "ElevenLabs no acepta esa clave.")
        if (!response.status.isSuccess()) return DjVoice.KeyCheck(false, "ElevenLabs respondió ${response.status.value}.")
        val voices = json.parseToJsonElement(response.bodyAsText()).jsonObject["voices"]?.jsonArray.orEmpty().mapNotNull { v ->
            val o = v as? JsonObject ?: return@mapNotNull null
            val id = o["voice_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val labels = o["labels"] as? JsonObject
            val desc = listOfNotNull(
                labels?.get("gender")?.jsonPrimitive?.contentOrNull,
                labels?.get("accent")?.jsonPrimitive?.contentOrNull,
                labels?.get("descriptive")?.jsonPrimitive?.contentOrNull,
            ).joinToString(" · ")
            DjVoice.Voice(id, o["name"]?.jsonPrimitive?.contentOrNull ?: id, desc, o["preview_url"]?.jsonPrimitive?.contentOrNull)
        }
        val remaining = djCatching {
            val sub = http.get("https://api.elevenlabs.io/v1/user/subscription") {
                header("xi-api-key", key)
                timeout { requestTimeoutMillis = 15_000 }
            }
            if (!sub.status.isSuccess()) null else {
                val o = json.parseToJsonElement(sub.bodyAsText()).jsonObject
                val limit = o["character_limit"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                val used = o["character_count"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                if (limit != null && used != null) (limit - used).coerceAtLeast(0) else null
            }
        }.getOrNull()
        return DjVoice.KeyCheck(true, "Clave de ElevenLabs correcta.", voices, remaining)
    }

    private suspend fun checkOpenAi(key: String): DjVoice.KeyCheck {
        val response = http.get("https://api.openai.com/v1/models") {
            header("Authorization", "Bearer $key")
            timeout { requestTimeoutMillis = 20_000 }
        }
        return when {
            response.status.isSuccess() -> DjVoice.KeyCheck(true, "Clave de OpenAI correcta.", DjVoice.OPENAI_VOICES)
            response.status.value == 401 -> DjVoice.KeyCheck(false, "OpenAI no acepta esa clave.")
            else -> DjVoice.KeyCheck(false, "OpenAI respondió ${response.status.value}.")
        }
    }

    /**
     * Convierte [text] en audio mp3 con el proveedor de pago. [style] es "radio", "party" o "chill"
     * y sólo afecta a OpenAI (instrucciones de entonación).
     */
    suspend fun synthesize(provider: String, key: String, voice: String, text: String, style: String): Result<ByteArray> = djCatching {
        when (provider) {
            DjVoice.ELEVENLABS -> {
                val response = http.post("https://api.elevenlabs.io/v1/text-to-speech/${DjVoice.voiceFor(provider, voice)}?output_format=mp3_44100_64") {
                    header("xi-api-key", key.trim())
                    contentType(ContentType.Application.Json)
                    timeout { requestTimeoutMillis = 45_000 }
                    setBody(
                        buildJsonObject {
                            put("text", text)
                            put("model_id", "eleven_multilingual_v2")
                        }.toString(),
                    )
                }
                if (!response.status.isSuccess()) error("ElevenLabs ${response.status.value}")
                response.body<ByteArray>()
            }
            DjVoice.OPENAI -> {
                val response = http.post("https://api.openai.com/v1/audio/speech") {
                    header("Authorization", "Bearer ${key.trim()}")
                    contentType(ContentType.Application.Json)
                    timeout { requestTimeoutMillis = 45_000 }
                    setBody(
                        buildJsonObject {
                            put("model", "gpt-4o-mini-tts")
                            put("voice", DjVoice.voiceFor(provider, voice))
                            put("input", text)
                            put("response_format", "mp3")
                            put("instructions", openAiInstructions(style))
                        }.toString(),
                    )
                }
                if (!response.status.isSuccess()) error("OpenAI ${response.status.value}")
                response.body<ByteArray>()
            }
            else -> error("Proveedor sin síntesis propia: $provider")
        }
    }

    private fun openAiInstructions(style: String): String = when (style) {
        "party" -> "Habla como un DJ de fiesta: enérgico, alegre y con ritmo, sin gritar."
        "chill" -> "Habla como un DJ de noche: pausado, cálido y relajado, casi susurrando."
        else -> "Habla como un locutor de radio: cercano, natural y con buen ritmo."
    }

    private companion object {
        const val GEMINI_MODEL = "gemini-flash-lite-latest"
    }
}
