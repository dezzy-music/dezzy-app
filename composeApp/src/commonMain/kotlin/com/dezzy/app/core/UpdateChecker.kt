/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Última versión publicada (tag sin «v», p. ej. «3.0.8») y la página de su release. */
data class LatestRelease(val version: String, val url: String)

private const val LatestReleaseApi = "https://api.github.com/repos/dezzy-music/Dezzy/releases/latest"

/** Pide a GitHub la última release pública de Dezzy. */
suspend fun fetchLatestRelease(http: HttpClient): LatestRelease {
    val text = http.get(LatestReleaseApi) { header("Accept", "application/vnd.github+json") }.bodyAsText()
    val obj = Json.parseToJsonElement(text).jsonObject
    val tag = obj["tag_name"]?.jsonPrimitive?.content ?: error("Respuesta sin versión")
    val url = obj["html_url"]?.jsonPrimitive?.content ?: "https://github.com/dezzy-music/Dezzy/releases/latest"
    return LatestRelease(tag.removePrefix("v").trim(), url)
}

/** true si [remote] es más nueva que [local] comparando número a número («3.0.10» > «3.0.9»). */
fun isNewerVersion(remote: String, local: String): Boolean {
    fun parts(v: String) = v.removePrefix("v").split('.', '-', ' ').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
    val r = parts(remote)
    val l = parts(local)
    for (i in 0 until maxOf(r.size, l.size)) {
        val a = r.getOrElse(i) { 0 }
        val b = l.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}
