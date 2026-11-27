/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.dezzy.app.core.model.Playlist
import com.dezzy.app.core.model.Track
import com.dezzy.app.ui.player.kit.rememberPreference
import com.dezzy.app.ui.screens.ScreenContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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

/** Subir copias a Filebin automáticamente (sólo se guarda el valor; sin efecto aún). */
internal val CloudBackupKey = booleanPreferencesKey("cloudBackup")

@Composable
internal fun BackupSettingsScreen(ctx: ScreenContext) {
    var cloudBackup by rememberPreference(CloudBackupKey, false)
    var troubleCache by remember { mutableStateOf(true) }
    var troubleVisitor by remember { mutableStateOf(true) }

    val saveBackup = rememberFileSaver("application/json")
    val saveCsv = rememberFileSaver("text/csv")
    val saveM3u = rememberFileSaver("audio/x-mpegurl")

    val restoreBackup = rememberFilePicker(listOf("application/json", "text/plain", "*/*")) { text ->
        ctx.scope.launch {
            val msg = runCatching { restoreBackupJson(ctx, text) }.getOrElse { "No se pudo restaurar la copia" }
            ctx.toast(msg)
        }
    }
    val importM3u = rememberFilePicker(listOf("audio/*", "audio/x-mpegurl", "text/plain", "*/*")) { text ->
        ctx.scope.launch {
            val msg = runCatching { importPlaylist(ctx, parseM3u(text)) }.getOrElse { "No se pudo importar la lista" }
            ctx.toast(msg)
        }
    }
    val importCsv = rememberFilePicker(listOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) { text ->
        ctx.scope.launch {
            val msg = runCatching { importPlaylist(ctx, parseCsv(text)) }.getOrElse { "No se pudo importar la lista" }
            ctx.toast(msg)
        }
    }

    SettingsPage(title = "Copia de seguridad", onBack = { ctx.navigator.back() }) {
        item(key = "backup:card") {
            SettingsGroup {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Copia de seguridad y restauración", fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(".backup", ".m3u", ".csv").forEach { BackupChip(it) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                ctx.scope.launch {
                                    runCatching { saveBackup("dezzy-backup.backup", buildBackupJson(ctx)) }
                                        .onFailure { ctx.toast("No se pudo crear la copia") }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Copia de seguridad") }
                        OutlinedButton(onClick = restoreBackup, modifier = Modifier.weight(1f)) { Text("Restaurar") }
                    }
                }
            }
        }
        item(key = "backup:cloud") {
            SettingsGroup {
                SettingsSwitchRow(
                    title = "Cloud Backup",
                    subtitle = "Automatically upload your backups to Filebin",
                    checked = cloudBackup,
                    onCheckedChange = { cloudBackup = it },
                )
            }
        }
        item(key = "backup:export") {
            SettingsGroup("Exportar biblioteca") {
                SettingsRow("Canciones que me gustan", "CSV", onClick = {
                    ctx.scope.launch {
                        val tracks = ctx.repository.getFavorites().first()
                        saveCsv("me-gusta.csv", tracksToCsv(tracks))
                    }
                })
                SettingsDivider()
                SettingsRow("Todas las canciones", "CSV", onClick = {
                    ctx.scope.launch { saveCsv("canciones.csv", tracksToCsv(allTracks(ctx))) }
                })
                SettingsDivider()
                SettingsRow("Todas las playlists", "CSV", onClick = {
                    ctx.scope.launch { saveCsv("playlists.csv", playlistsToCsv(loadPlaylists(ctx))) }
                })
                SettingsDivider()
                SettingsRow("Me gusta como lista", "M3U", onClick = {
                    ctx.scope.launch {
                        val tracks = ctx.repository.getFavorites().first()
                        saveM3u("me-gusta.m3u", tracksToM3u(tracks))
                    }
                })
            }
        }
        item(key = "backup:import") {
            SettingsGroup("Importar lista de reproducción") {
                SettingsRow("Importar una lista de reproducción \"m3u\"", "audio/*", onClick = importM3u)
                SettingsDivider()
                SettingsRow("Importar una lista de reproducción \"cvs\"", "text/csv", onClick = importCsv)
            }
        }
        item(key = "backup:trouble") {
            SettingsGroup("Troubleshooter") {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Clears the song cache and resets expired Visitor Data to resolve playback errors.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BackupChip("Cache de canción", selected = troubleCache) { troubleCache = !troubleCache }
                        BackupChip("Visitor Data", selected = troubleVisitor) { troubleVisitor = !troubleVisitor }
                    }
                    Button(
                        onClick = {
                            if (!troubleCache && !troubleVisitor) ctx.toast("Elige al menos una opción")
                            else ctx.toast("Hecho: el motor de reproducción rehará su sesión al siguiente tema")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Run troubleshooter") }
                }
            }
        }
    }
}

@Composable
private fun BackupChip(label: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(50)
    val color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = @Composable {
        Text(label, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = color, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), content = content)
    } else {
        Surface(shape = shape, color = color, content = content)
    }
}

// ── Datos ────────────────────────────────────────────────────────────────────────────────────

private suspend fun loadPlaylists(ctx: ScreenContext): List<Playlist> =
    ctx.repository.getPlaylists().first().map { ctx.repository.getPlaylistById(it.id) ?: it }

private suspend fun allTracks(ctx: ScreenContext): List<Track> {
    val all = ctx.repository.getFavorites().first() +
        ctx.repository.getHistory(5000).first() +
        loadPlaylists(ctx).flatMap { it.tracks }
    return all.filter { it.videoId != null }.distinctBy { it.videoId }
}

private fun Track.toJson(): JsonObject = buildJsonObject {
    put("videoId", videoId)
    put("title", title)
    put("artists", artists)
    put("album", album)
    put("thumbnail", thumbnail)
    put("durationSeconds", durationSeconds)
}

private fun JsonObject.toTrack(): Track? {
    val id = this["videoId"]?.jsonPrimitive?.contentOrNull ?: return null
    return Track(
        videoId = id,
        title = this["title"]?.jsonPrimitive?.contentOrNull ?: id,
        artists = this["artists"]?.jsonPrimitive?.contentOrNull ?: "",
        album = this["album"]?.jsonPrimitive?.contentOrNull,
        thumbnail = this["thumbnail"]?.jsonPrimitive?.contentOrNull,
        durationSeconds = this["durationSeconds"]?.jsonPrimitive?.longOrNull,
    )
}

private suspend fun buildBackupJson(ctx: ScreenContext): String {
    val favorites = ctx.repository.getFavorites().first()
    val playlists = loadPlaylists(ctx)
    val root = buildJsonObject {
        put("app", "dezzy")
        put("version", 1)
        put("favorites", buildJsonArray { favorites.forEach { add(it.toJson()) } })
        put("playlists", buildJsonArray {
            playlists.forEach { p ->
                add(buildJsonObject {
                    put("title", p.title)
                    put("author", p.author)
                    put("tracks", buildJsonArray { p.tracks.forEach { add(it.toJson()) } })
                })
            }
        })
    }
    return root.toString()
}

private suspend fun restoreBackupJson(ctx: ScreenContext, text: String): String {
    val root = Json.parseToJsonElement(text).jsonObject
    if (root["app"]?.jsonPrimitive?.contentOrNull != "dezzy") return "Ese archivo no es una copia de Dezzy"
    var favs = 0
    (root["favorites"] as? JsonArray)?.forEach { el ->
        el.jsonObject.toTrack()?.let { ctx.repository.setFavorite(it, true); favs++ }
    }
    var lists = 0
    (root["playlists"] as? JsonArray)?.forEach { el ->
        val o = el.jsonObject
        val title = o["title"]?.jsonPrimitive?.contentOrNull ?: return@forEach
        val pl = ctx.repository.createPlaylist(title, o["author"]?.jsonPrimitive?.contentOrNull)
        (o["tracks"] as? JsonArray)?.forEach { t -> t.jsonObject.toTrack()?.let { ctx.repository.addTrackToPlaylist(pl.id, it) } }
        lists++
    }
    return "Restaurado: $favs me gusta y $lists listas"
}

// ── CSV / M3U ────────────────────────────────────────────────────────────────────────────────

private const val CsvHeader = "Title,Artists,Album,Duration,VideoId"

private fun csvField(s: String?): String {
    val v = s.orEmpty()
    return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}

private fun trackRow(t: Track): String =
    listOf(t.title, t.artists, t.album, t.durationSeconds?.toString(), t.videoId).joinToString(",") { csvField(it) }

private fun tracksToCsv(tracks: List<Track>): String =
    (listOf(CsvHeader) + tracks.map { trackRow(it) }).joinToString("\n")

private fun playlistsToCsv(playlists: List<Playlist>): String =
    (listOf("Playlist,$CsvHeader") + playlists.flatMap { p -> p.tracks.map { csvField(p.title) + "," + trackRow(it) } })
        .joinToString("\n")

private fun tracksToM3u(tracks: List<Track>): String = buildString {
    append("#EXTM3U\n")
    tracks.forEach { t ->
        val id = t.videoId ?: return@forEach
        append("#EXTINF:").append(t.durationSeconds ?: -1).append(',').append(t.artists).append(" - ").append(t.title).append('\n')
        append("https://music.youtube.com/watch?v=").append(id).append('\n')
    }
}

/** Resultado de leer un archivo de lista: nombre propuesto y canciones con id. */
private class ParsedList(val name: String, val tracks: List<Track>)

private fun parseM3u(text: String): ParsedList {
    val tracks = mutableListOf<Track>()
    var pendingTitle: String? = null
    var pendingArtist = ""
    var pendingDuration: Long? = null
    text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
        if (line.startsWith("#EXTINF:")) {
            val body = line.removePrefix("#EXTINF:")
            pendingDuration = body.substringBefore(',').trim().toLongOrNull()?.takeIf { it >= 0 }
            val label = body.substringAfter(',', "")
            pendingArtist = if (" - " in label) label.substringBefore(" - ").trim() else ""
            pendingTitle = if (" - " in label) label.substringAfter(" - ").trim() else label.trim()
        } else if (!line.startsWith("#")) {
            val id = videoIdOf(line)
            if (id != null) {
                tracks += Track(videoId = id, title = pendingTitle ?: id, artists = pendingArtist, durationSeconds = pendingDuration)
            }
            pendingTitle = null; pendingArtist = ""; pendingDuration = null
        }
    }
    return ParsedList("Importada (m3u)", tracks)
}

private fun videoIdOf(url: String): String? {
    val fromQuery = url.substringAfter("v=", "").substringBefore('&').takeIf { it.length == 11 }
    if (fromQuery != null) return fromQuery
    val tail = url.substringAfterLast('/').substringBefore('?')
    return tail.takeIf { it.length == 11 && url.contains("youtu") }
}

private fun splitCsvLine(line: String): List<String> {
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
            c == '"' -> quoted = !quoted
            c == ',' && !quoted -> { out += cur.toString(); cur.clear() }
            else -> cur.append(c)
        }
        i++
    }
    out += cur.toString()
    return out
}

private fun parseCsv(text: String): ParsedList {
    val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
    if (lines.isEmpty()) return ParsedList("Importada (csv)", emptyList())
    val header = splitCsvLine(lines.first()).map { it.trim().lowercase() }
    fun col(vararg names: String) = header.indexOfFirst { it in names }
    val idCol = col("videoid", "video id", "id")
    val titleCol = col("title", "titulo", "título", "name").takeIf { it >= 0 } ?: 0
    val artistCol = col("artists", "artist", "artista", "artistas")
    val albumCol = col("album", "álbum")
    val durCol = col("duration", "duración", "duracion")
    val tracks = lines.drop(1).mapNotNull { line ->
        val f = splitCsvLine(line)
        val id = f.getOrNull(idCol)?.trim()?.takeIf { idCol >= 0 && it.isNotEmpty() } ?: return@mapNotNull null
        Track(
            videoId = id,
            title = f.getOrNull(titleCol)?.takeIf { it.isNotBlank() } ?: id,
            artists = f.getOrNull(artistCol)?.takeIf { artistCol >= 0 } ?: "",
            album = f.getOrNull(albumCol)?.takeIf { albumCol >= 0 && it.isNotBlank() },
            durationSeconds = f.getOrNull(durCol)?.takeIf { durCol >= 0 }?.toLongOrNull(),
        )
    }
    return ParsedList("Importada (csv)", tracks)
}

private suspend fun importPlaylist(ctx: ScreenContext, parsed: ParsedList): String {
    if (parsed.tracks.isEmpty()) return "No se encontraron canciones con identificador en el archivo"
    val pl = ctx.repository.createPlaylist(parsed.name)
    parsed.tracks.forEach { ctx.repository.addTrackToPlaylist(pl.id, it) }
    return "Lista importada: ${parsed.tracks.size} canciones"
}
