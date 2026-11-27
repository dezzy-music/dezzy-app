/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.ai

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Un turno de la charla: [fromUser] true = lo dijo el oyente. */
class IaTurn(val fromUser: Boolean, val text: String)

/** Lo que Dezzy IA quiere hacer además de hablar. */
enum class IaActionType { PLAY_MUSIC, RECOMMEND, ADD_TO_QUEUE }

class IaAction(val type: IaActionType, val query: String)

/** Respuesta ya separada: [text] es lo que se enseña; [action] (si hay) lo ejecuta la pantalla. */
class IaReply(val text: String, val action: IaAction?)

/**
 * Chat de Dezzy IA con Gemini. La clave es del usuario (BYOK, la misma que usa el DJ) y se manda
 * en la cabecera `x-goog-api-key`; nunca se guarda aquí ni va en la URL.
 */
class DezzyIaClient(private val http: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Manda la charla a Gemini. [system] lleva las reglas y el contexto musical del oyente. */
    suspend fun chat(key: String, system: String, history: List<IaTurn>): Result<String> {
        // Gemini exige que la charla empiece por el oyente.
        val turns = history.dropWhile { !it.fromUser }
        if (turns.isEmpty()) return Result.failure(IllegalArgumentException("No hay nada que responder"))
        return try {
            val body = buildJsonObject {
                put("systemInstruction", buildJsonObject {
                    put("parts", JsonArray(listOf(buildJsonObject { put("text", system) })))
                })
                put("contents", JsonArray(turns.map { turn ->
                    buildJsonObject {
                        put("role", if (turn.fromUser) "user" else "model")
                        put("parts", JsonArray(listOf(buildJsonObject { put("text", turn.text) })))
                    }
                }))
                put("generationConfig", buildJsonObject {
                    put("maxOutputTokens", 700)
                    put("temperature", 0.8)
                })
            }
            val response = http.post("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent") {
                header("x-goog-api-key", key.trim())
                contentType(ContentType.Application.Json)
                timeout { requestTimeoutMillis = 30_000 }
                setBody(body.toString())
            }
            if (!response.status.isSuccess()) {
                return Result.failure(IllegalStateException("Gemini respondió ${response.status.value}. Revisa tu clave en Ajustes del DJ."))
            }
            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val parts = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject?.get("parts")?.jsonArray
            val text = parts?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }?.joinToString("")?.trim()
            if (text.isNullOrBlank()) Result.failure(IllegalStateException("Gemini no devolvió respuesta")) else Result.success(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    companion object {
        private const val MODEL = "gemini-flash-lite-latest"

        /** Reglas fijas de Dezzy IA; el contexto del oyente se añade debajo. */
        fun systemPrompt(userContext: String): String = """
            Eres Dezzy, el asistente musical con IA de la app Dezzy. Habla siempre en español, cercano y breve.
            Puedes conversar de música, recomendar y poner canciones. Si el oyente quiere escuchar algo,
            termina tu respuesta con UNA línea de JSON con esta forma exacta:
            {"action":"PLAY_MUSIC","query":"búsqueda corta"}
            Acciones posibles: "PLAY_MUSIC" (poner música ya), "RECOMMEND" (enseñar canciones sugeridas sin ponerlas)
            y "ADD_TO_QUEUE" (añadir a la cola). La búsqueda es de YouTube Music, máximo 8 palabras.
            Si sólo se conversa, no pongas JSON. No inventes datos que no conozcas.

            $userContext
        """.trimIndent()

        /** Separa el JSON de acción (si lo hay) del texto que se enseña. */
        fun parseReply(raw: String): IaReply {
            val match = Regex("\\{[^{}]*\"action\"[^{}]*\\}").findAll(raw).lastOrNull()
                ?: return IaReply(raw.trim(), null)
            val action = try {
                val o = Json.parseToJsonElement(match.value).jsonObject
                val type = IaActionType.entries.firstOrNull {
                    it.name == o["action"]?.jsonPrimitive?.contentOrNull.orEmpty().trim().uppercase()
                }
                val query = o["query"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                if (type != null && query.isNotBlank()) IaAction(type, query) else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
            val clean = raw.replace(match.value, "").replace("```json", "").replace("```", "").trim()
            return IaReply(clean, action)
        }
    }
}
