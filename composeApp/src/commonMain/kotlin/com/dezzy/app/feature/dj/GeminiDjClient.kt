/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.dj

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Lo que decide el DJ ante una petición libre del oyente. */
enum class DjAction { PLAY_MUSIC, ADD_TO_PLAYLIST, RESPOND_ONLY }

class DjAgentResponse(val action: DjAction, val query: String, val message: String)

/** Una tarjeta de vibra de la pantalla del DJ. */
class DjVibeCard(val title: String, val query: String)

/**
 * Cliente de Gemini del DJ. La clave es del usuario (BYOK) y se manda en la cabecera
 * `x-goog-api-key`; nunca se guarda ni se pone en la URL.
 */
class GeminiDjClient(private val http: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun generate(
        key: String,
        prompt: String,
        maxTokens: Int,
        temperature: Double,
        jsonMode: Boolean = false,
    ): String? = djCatching {
        val body = buildJsonObject {
            put("contents", JsonArray(listOf(buildJsonObject {
                put("parts", JsonArray(listOf(buildJsonObject { put("text", prompt) })))
            })))
            put("generationConfig", buildJsonObject {
                put("maxOutputTokens", maxTokens)
                put("temperature", temperature)
                if (jsonMode) put("responseMimeType", "application/json")
            })
        }
        val response = http.post("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent") {
            header("x-goog-api-key", key.trim())
            contentType(ContentType.Application.Json)
            timeout { requestTimeoutMillis = 25_000 }
            setBody(body.toString())
        }
        if (!response.status.isSuccess()) return@djCatching null
        val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val parts = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
        parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun languageRule(language: String): String = when (language) {
        "es" -> "Habla siempre en español."
        "en" -> "Always speak in English."
        else -> "Habla en el idioma del oyente; por defecto, español."
    }

    private fun styleRule(style: String): String = when (style) {
        "party" -> "Tono: de fiesta, enérgico y divertido."
        "chill" -> "Tono: relajado, pausado y cálido."
        else -> "Tono: de locutor de radio, cercano y natural."
    }

    /**
     * Frase corta que dice el DJ antes de una canción. [context] explica por qué viene (bloque y
     * dato del historial). Devuelve null si Gemini no responde (se usa una frase fija).
     */
    suspend fun generateScript(
        key: String,
        djName: String,
        username: String,
        title: String,
        artist: String,
        context: String,
        first: Boolean,
        language: String,
        style: String,
        taste: String,
        recentLines: List<String>,
    ): String? {
        val angles = listOf(
            "un dato curioso o un recuerdo ligado a la canción o al artista, sin inventar cifras",
            "cómo suena: el ambiente o el momento del día en que encaja",
            "una pequeña reacción personal, como si la hubieras elegido tú",
            "un guiño al oyente por su historial, si el contexto da pie",
        )
        val intro = if (first) {
            "Esta es la PRIMERA canción: preséntate como $djName" + (if (username.isNotBlank()) " y saluda a $username" else "") + "."
        } else {
            "NO saludes ni te presentes: ya llevas rato al aire."
        }
        val memory = if (recentLines.isEmpty()) "" else
            "\nLo último que dijiste (no repitas ideas ni arranques):\n" + recentLines.joinToString("\n") { "- $it" }
        val tasteLine = if (taste.isBlank()) "" else "\nLo que el oyente contó que le gusta: $taste"
        val prompt = """
            Eres $djName, un DJ de radio que presenta canciones. ${languageRule(language)} ${styleRule(style)}
            $intro
            Canción que va a sonar: «$title» de $artist.
            Contexto: $context$tasteLine$memory
            Ángulo para esta vez: ${angles.random()}.
            Reglas: una o dos frases cortas (máximo 35 palabras), sin emojis, sin comillas, sin hashtags,
            sin decir «a continuación». Nombra la canción o el artista. No inventes datos que no conozcas.
            Responde SOLO con lo que dirás en voz alta.
        """.trimIndent()
        return generate(key, prompt, maxTokens = 85, temperature = 0.8)?.trim('"', '«', '»', ' ')
    }

    /** Pregunta corta de la primera vez para conocer el gusto del oyente. */
    suspend fun generateTasteQuestion(key: String, djName: String, language: String): String? {
        val prompt = "Eres $djName, DJ de radio. ${languageRule(language)} Haz UNA pregunta breve y amable (máximo 20 palabras) " +
            "para saber qué música le gusta al oyente. Sin emojis. Responde solo con la pregunta."
        return generate(key, prompt, maxTokens = 60, temperature = 0.8)
    }

    /** Convierte una petición libre en una búsqueda corta de música. */
    suspend fun generateSearchQuery(key: String, request: String): String? {
        val prompt = "Convierte esta petición de música en una búsqueda corta de YouTube Music (máximo 8 palabras, " +
            "sin comillas). Petición: «$request». Responde SOLO con la búsqueda."
        return generate(key, prompt, maxTokens = 30, temperature = 0.3)?.trim('"', ' ')
    }

    /** Entiende una petición libre y decide qué hacer. Si Gemini falla, devuelve null. */
    suspend fun executeAgentTask(key: String, djName: String, request: String, nowPlaying: String?, language: String): DjAgentResponse? {
        val prompt = """
            Eres $djName, DJ de una app de música. ${languageRule(language)}
            Ahora suena: ${nowPlaying ?: "nada"}.
            El oyente dice: «$request».
            Decide una acción y responde SOLO con JSON con estas claves:
            - "action": "PLAY_MUSIC" (quiere escuchar algo), "ADD_TO_PLAYLIST" (quiere guardar lo que suena) o "RESPOND_ONLY" (solo conversar).
            - "query": búsqueda corta de música si la acción es PLAY_MUSIC, si no "".
            - "message": lo que dirás en voz alta, una frase corta, sin emojis.
        """.trimIndent()
        val text = generate(key, prompt, maxTokens = 160, temperature = 0.5, jsonMode = true) ?: return null
        return djCatching {
            val o = json.parseToJsonElement(text.removePrefix("```json").removeSuffix("```").trim()).jsonObject
            val action = runCatching { DjAction.valueOf(o.str("action").uppercase()) }.getOrDefault(DjAction.RESPOND_ONLY)
            DjAgentResponse(action, o.str("query"), o.str("message"))
        }.getOrNull()
    }

    /** Cuatro vibras para esta hora y este oyente. Si falla, devuelve null (se usan las de siempre). */
    suspend fun generateVibeCards(key: String, hour: Int, taste: String, language: String): List<DjVibeCard>? {
        val prompt = """
            Propón 4 vibras de música para las $hour h. ${languageRule(language)}
            ${if (taste.isBlank()) "" else "Gustos del oyente: $taste."}
            Responde SOLO con JSON: una lista de 4 objetos con "title" (2 a 4 palabras, con un emoji al inicio)
            y "query" (búsqueda corta de YouTube Music).
        """.trimIndent()
        val text = generate(key, prompt, maxTokens = 220, temperature = 0.9, jsonMode = true) ?: return null
        return djCatching {
            json.parseToJsonElement(text.removePrefix("```json").removeSuffix("```").trim()).jsonArray.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val title = o.str("title")
                val query = o.str("query")
                if (title.isBlank() || query.isBlank()) null else DjVibeCard(title, query)
            }.take(4).takeIf { it.size == 4 }
        }.getOrNull()
    }

    private fun JsonObject.str(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty().trim()

    companion object {
        private const val MODEL = "gemini-flash-lite-latest"

        /** Vibras de siempre, cuando no hay Gemini. */
        val DEFAULT_VIBES = listOf(
            DjVibeCard("🔥 Para entrenar", "música para entrenar energía"),
            DjVibeCard("🌙 Para la noche", "música tranquila para la noche"),
            DjVibeCard("🎉 De fiesta", "música para fiesta bailable"),
            DjVibeCard("☕ Para concentrarte", "música para concentrarse y estudiar"),
        )
    }
}
