/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social.nostr

import androidx.compose.runtime.Immutable
import com.dezzy.app.feature.social.crypto.hexToBytes
import com.dezzy.app.feature.social.crypto.isHex64
import com.dezzy.app.feature.social.crypto.nostrCrypto
import com.dezzy.app.feature.social.crypto.toHex
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.Clock

/** Los mismos relés que usa Dezzy Desktop (`link.ts`). */
val DEFAULT_RELAYS: List<String> = listOf(
    "wss://relay.damus.io",
    "wss://nos.lol",
    "wss://relay.primal.net",
    "wss://nostr.mom",
    "wss://offchain.pub",
    "wss://relay.nostr.band",
)

/** Relé con búsqueda de texto (NIP-50) para encontrar gente por nombre. */
const val SEARCH_RELAY: String = "wss://relay.nostr.band"

/** Par de claves Nostr. La privada sólo vive en memoria y en el almacén cifrado del dispositivo. */
class NostrKeys(val privateKey: ByteArray) {
    val publicKey: String = nostrCrypto.pubKeyXOnly(privateKey).toHex()

    companion object {
        fun generate(): NostrKeys {
            while (true) {
                val candidate = nostrCrypto.randomBytes(32)
                if (nostrCrypto.isValidPrivateKey(candidate)) return NostrKeys(candidate)
            }
        }
    }
}

/** Evento Nostr (NIP-01). [sig] vacío = «rumor» sin firmar de NIP-59. */
@Immutable
@Serializable
data class NostrEvent(
    val id: String,
    val pubkey: String,
    @SerialName("created_at") val created_at: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String,
) {
    /** Alias cómodo con el nombre en Kotlin. */
    val createdAt: Long get() = created_at

    /** Primer valor de la etiqueta [name] (p. ej. `tag("d")`). */
    fun tag(name: String): String? = tags.firstOrNull { it.size > 1 && it[0] == name }?.get(1)

    fun tagValues(name: String): List<String> = tags.filter { it.size > 1 && it[0] == name }.map { it[1] }

    fun fullTag(name: String): List<String>? = tags.firstOrNull { it.isNotEmpty() && it[0] == name }

    fun hasValidId(): Boolean = id == computeId(pubkey, created_at, kind, tags, content)

    fun verify(): Boolean = id.isHex64() && pubkey.isHex64() && sig.length == 128 && hasValidId() &&
        runCatching { nostrCrypto.verifySchnorr(sig.hexToBytes(), id.hexToBytes(), pubkey.hexToBytes()) }
            .getOrDefault(false)

    fun toJsonObject(includeSig: Boolean = true): JsonObject = buildJsonObject {
        put("id", id)
        put("pubkey", pubkey)
        put("created_at", created_at)
        put("kind", kind)
        put("tags", buildJsonArray { tags.forEach { t -> add(JsonArray(t.map { JsonPrimitive(it) })) } })
        put("content", content)
        if (includeSig) put("sig", sig)
    }

    fun toJson(includeSig: Boolean = true): String = toJsonObject(includeSig).toString()

    companion object {
        val json = Json { ignoreUnknownKeys = true }

        /** Segundos desde 1970. */
        fun now(): Long = Clock.System.now().toEpochMilliseconds() / 1000

        fun computeId(pubkey: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): String {
            val serialized = buildString {
                append("[0,\"").append(pubkey).append("\",").append(createdAt).append(',').append(kind).append(",[")
                tags.forEachIndexed { i, tag ->
                    if (i > 0) append(',')
                    append('[')
                    tag.forEachIndexed { j, value ->
                        if (j > 0) append(',')
                        appendJsonString(value)
                    }
                    append(']')
                }
                append("],")
                appendJsonString(content)
                append(']')
            }
            return nostrCrypto.sha256(serialized.encodeToByteArray()).toHex()
        }

        /** Escapa como `JSON.stringify` (lo que usan relés y clientes para calcular el id). */
        private fun StringBuilder.appendJsonString(value: String) {
            append('"')
            for (c in value) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    else -> if (c.code < 0x20) {
                        append("\\u").append(c.code.toString(16).padStart(4, '0'))
                    } else {
                        append(c)
                    }
                }
            }
            append('"')
        }

        fun sign(keys: NostrKeys, kind: Int, tags: List<List<String>>, content: String, createdAt: Long = now()): NostrEvent =
            sign(keys.privateKey, keys.publicKey, kind, tags, content, createdAt)

        fun sign(
            privateKey: ByteArray,
            publicKey: String,
            kind: Int,
            tags: List<List<String>>,
            content: String,
            createdAt: Long = now(),
        ): NostrEvent {
            val id = computeId(publicKey, createdAt, kind, tags, content)
            val sig = nostrCrypto.signSchnorr(id.hexToBytes(), privateKey).toHex()
            return NostrEvent(id, publicKey, createdAt, kind, tags, content, sig)
        }

        /** Rumor de NIP-59: evento con id pero sin firma (no se puede reenviar como prueba). */
        fun rumor(pubkey: String, kind: Int, tags: List<List<String>>, content: String, createdAt: Long = now()): NostrEvent =
            NostrEvent(computeId(pubkey, createdAt, kind, tags, content), pubkey, createdAt, kind, tags, content, "")

        fun fromJson(obj: JsonObject): NostrEvent? = runCatching {
            NostrEvent(
                id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                pubkey = obj["pubkey"]!!.jsonPrimitive.content,
                created_at = obj["created_at"]!!.jsonPrimitive.longOrNull ?: 0L,
                kind = obj["kind"]!!.jsonPrimitive.content.toInt(),
                tags = obj["tags"]?.jsonArray?.map { tag -> tag.jsonArray.map { it.jsonPrimitive.content } }.orEmpty(),
                content = obj["content"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                sig = obj["sig"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }.getOrNull()

        fun fromJson(text: String): NostrEvent? =
            runCatching { fromJson(json.parseToJsonElement(text).jsonObject) }.getOrNull()
    }
}

/**
 * Filtro de suscripción (NIP-01) como [JsonObject]. Las etiquetas van como `"#p" to listOf(...)`.
 */
fun nostrFilter(
    ids: List<String>? = null,
    authors: List<String>? = null,
    kinds: List<Int>? = null,
    tags: Map<String, List<String>> = emptyMap(),
    since: Long? = null,
    until: Long? = null,
    limit: Int? = null,
    search: String? = null,
): JsonObject = buildJsonObject {
    ids?.let { v -> put("ids", JsonArray(v.map { JsonPrimitive(it) })) }
    authors?.let { v -> put("authors", JsonArray(v.map { JsonPrimitive(it) })) }
    kinds?.let { v -> put("kinds", JsonArray(v.map { JsonPrimitive(it) })) }
    tags.forEach { (key, values) -> put(key, JsonArray(values.map { JsonPrimitive(it) })) }
    since?.let { put("since", it) }
    until?.let { put("until", it) }
    limit?.let { put("limit", it) }
    search?.let { put("search", it) }
}

/** Copia de un filtro con otro `since` / `limit`. */
internal fun JsonObject.withFilter(since: Long? = null, limit: Int? = null): JsonObject {
    val map = LinkedHashMap<String, JsonElement>(this)
    since?.let { map["since"] = JsonPrimitive(it) }
    limit?.let { map["limit"] = JsonPrimitive(it) }
    return JsonObject(map)
}

/** Tipos de evento que usa Dezzy (los estándar de Nostr + etiquetas propias). */
object Kinds {
    const val METADATA = 0
    const val TEXT_NOTE = 1
    const val CONTACTS = 3
    const val DELETION = 5
    const val REACTION = 7
    const val SEAL = 13
    const val CHAT_MESSAGE = 14
    const val GIFT_WRAP = 1059
    const val DM_RELAYS = 10050
    const val LINK = 21337
    const val USER_STATUS = 30315
    const val BLOSSOM_AUTH = 24242
}
