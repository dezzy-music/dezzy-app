/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.musicmap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.musicmap.CountryNames
import com.dezzy.app.feature.musicmap.MusicMapGenres
import com.dezzy.app.ui.components.CoverImage
import kotlin.math.roundToInt

/*
 * Mapa musical: hoja del país con géneros, flechas de tendencia y buscador «dónde se escucha más».
 */

/** «1 canción» / «N canciones». */
internal fun cancionesLabel(n: Int): String = if (n == 1) "1 canción" else "$n canciones"

/** Real per-country chart result. */
internal data class CountryChart(val country: MapCountry, val songs: List<Track>)

/** Primer artista de una canción (la clave para clasificar por género). */
internal fun Track.firstArtist(): String = artists.split(",", "&", " x ", " feat.", " ft.").firstOrNull()?.trim().orEmpty()

// ── Géneros ──────────────────────────────────────────────────────────────────

/** Peso de un género en el top de un país. */
internal data class GenreShare(val name: String, val count: Int, val percent: Int)

/** Cuántos géneros se nombran; el resto se junta en «Otros». */
private const val NAMED_GENRES = 5

/**
 * Reparte las canciones por género. Sólo cuentan las que ya tienen género; los géneros pequeños y
 * los no clasificados se juntan en «Otros», que va siempre al final.
 */
internal fun genreShares(songs: List<Track>, genreOf: (Track) -> String?): List<GenreShare> {
    val genres = songs.mapNotNull(genreOf)
    if (genres.isEmpty()) return emptyList()
    val counts = genres.groupingBy { it }.eachCount()
    val named = counts.filterKeys { it != MusicMapGenres.OTHER }
        .entries.sortedByDescending { it.value }
        .take(NAMED_GENRES)
    val namedCount = named.sumOf { it.value }
    val total = genres.size
    fun pct(n: Int) = (n * 100f / total).roundToInt()
    return buildList {
        named.forEach { add(GenreShare(it.key, it.value, pct(it.value))) }
        val rest = total - namedCount
        if (rest > 0) add(GenreShare(MusicMapGenres.OTHER, rest, pct(rest)))
    }
}

/** ¿La canción entra en el chip de género [filter]? «Otros» recoge lo que no tiene chip propio. */
internal fun matchesGenre(genre: String?, filter: String, shares: List<GenreShare>): Boolean {
    if (filter != MusicMapGenres.OTHER) return genre == filter
    val named = shares.map { it.name }.filter { it != MusicMapGenres.OTHER }
    return genre != null && genre !in named
}

@Composable
private fun colorFor(index: Int, share: GenreShare): Color {
    val s = MaterialTheme.colorScheme
    if (share.name == MusicMapGenres.OTHER) return s.outlineVariant
    return listOf(s.primary, s.tertiary, s.secondary, s.inversePrimary, s.outline)[index % NAMED_GENRES]
}

/** Barra con el peso de cada género, leyenda con los números y chips para filtrar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GenreShareSection(
    shares: List<GenreShare>,
    loading: Boolean,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String = "Aún no sabemos los géneros de este top",
    loadingText: String = "Analizando los géneros…",
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        if (shares.isEmpty()) {
            Text(
                text = if (loading) loadingText else emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))
            }
            return@Column
        }

        val description = shares.joinToString(prefix = "Géneros: ") { "${it.name} ${it.percent} %" }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .clearAndSetSemantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            shares.forEachIndexed { index, share ->
                Box(Modifier.weight(share.count.toFloat()).fillMaxHeight().background(colorFor(index, share)))
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            shares.forEachIndexed { index, share ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colorFor(index, share)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${share.name} ${share.percent} %",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GenreChip("Todo", selected == null) { onSelect(null) }
            shares.forEach { share ->
                GenreChip(share.name, selected == share.name) { onSelect(if (selected == share.name) null else share.name) }
            }
        }
    }
}

@Composable
private fun GenreChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
        } else null,
    )
}

// ── Tendencia ────────────────────────────────────────────────────────────────

/**
 * Flecha de tendencia respecto al top anterior. Lleva texto («sube 3») para no depender sólo del
 * color. Sin foto anterior no enseña nada.
 */
@Composable
internal fun TrendBadge(rank: Int, previousRanks: Map<String, Int>, songId: String?) {
    if (previousRanks.isEmpty() || songId == null) return
    val previous = previousRanks[songId]
    val scheme = MaterialTheme.colorScheme
    val text: String
    val icon: androidx.compose.ui.graphics.vector.ImageVector?
    val color: Color
    val spoken: String
    when {
        previous == null -> { text = "Nuevo"; icon = null; color = scheme.tertiary; spoken = "nueva en el top" }
        previous > rank -> { text = "${previous - rank}"; icon = Icons.Rounded.ArrowUpward; color = scheme.primary; spoken = "sube ${previous - rank}" }
        previous < rank -> { text = "${rank - previous}"; icon = Icons.Rounded.ArrowDownward; color = scheme.error; spoken = "baja ${rank - previous}" }
        else -> { text = "—"; icon = null; color = scheme.onSurfaceVariant; spoken = "sin cambios" }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp).clearAndSetSemantics { contentDescription = spoken },
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, fontSize = 12.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

// ── Buscador: dónde se escucha más ───────────────────────────────────────────

internal enum class SearchTab(val label: String) { ARTIST("Artista"), SONGS("Canciones"), COUNTRIES("Países") }

/** Presencia de un artista o canción en el top de un país. */
internal data class CountryHit(val country: MapCountry, val bestRank: Int, val count: Int, val bestSong: Track)

internal data class ArtistResult(val name: String, val thumbnail: String?, val hits: List<CountryHit>)

internal data class SongResult(val song: Track, val hits: List<CountryHit>)

internal data class MapSearch(
    val artist: ArtistResult?,
    val songs: List<SongResult>,
    val countries: List<CountryChart>,
    /** Canciones de todo el catálogo que no están en los tops de hoy. */
    val catalog: List<Track> = emptyList(),
) {
    val isEmpty get() = artist == null && songs.isEmpty() && countries.isEmpty() && catalog.isEmpty()
    val hasSongs get() = songs.isNotEmpty() || catalog.isNotEmpty()

    fun firstTab(): SearchTab = when {
        artist != null -> SearchTab.ARTIST
        hasSongs -> SearchTab.SONGS
        else -> SearchTab.COUNTRIES
    }
}

/** Busca en los tops ya cargados: el artista más presente, las canciones y los países. */
internal fun searchCharts(query: String, charts: List<CountryChart>): MapSearch {
    val q = MusicMapGenres.key(query)
    if (q.isEmpty()) return MapSearch(null, emptyList(), emptyList())

    // Artista: el nombre que más aparece entre los que contienen la búsqueda.
    val artistCounts = HashMap<String, Int>()
    val artistNames = HashMap<String, String>()
    for (chart in charts) {
        for (song in chart.songs) {
            val name = song.firstArtist()
            val k = MusicMapGenres.key(name)
            if (k.contains(q)) {
                artistCounts[k] = (artistCounts[k] ?: 0) + 1
                artistNames.putIfAbsent(k, name)
            }
        }
    }
    val artistKey = artistCounts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { if (it.key == q) 1 else 0 }.thenByDescending { it.value })
        .firstOrNull()?.key
    val artist = artistKey?.let { k ->
        val hits = charts.mapNotNull { chart ->
            val ranked = chart.songs.withIndex().filter { (_, s) -> MusicMapGenres.key(s.firstArtist()) == k }
            val best = ranked.firstOrNull() ?: return@mapNotNull null
            CountryHit(chart.country, best.index + 1, ranked.size, best.value)
        }.sortedWith(compareBy<CountryHit> { it.bestRank }.thenByDescending { it.count })
        ArtistResult(artistNames.getValue(k), hits.firstOrNull()?.bestSong?.thumbnail, hits)
    }

    // Canciones cuyo título contiene la búsqueda.
    val songHits = LinkedHashMap<String, MutableList<CountryHit>>()
    val songById = HashMap<String, Track>()
    for (chart in charts) {
        chart.songs.forEachIndexed { index, song ->
            val id = song.videoId ?: return@forEachIndexed
            if (MusicMapGenres.key(song.title).contains(q)) {
                songById.putIfAbsent(id, song)
                songHits.getOrPut(id) { mutableListOf() } += CountryHit(chart.country, index + 1, 1, song)
            }
        }
    }
    val songs = songHits.map { (id, hits) -> SongResult(songById.getValue(id), hits.sortedBy { it.bestRank }) }
        .sortedWith(compareByDescending<SongResult> { it.hits.size }.thenBy { it.hits.first().bestRank })
        .take(20)

    val countries = charts.filter {
        MusicMapGenres.key(it.country.name).contains(q) || it.country.code.equals(query.trim(), ignoreCase = true)
    }
    return MapSearch(artist, songs, countries)
}

/** Más arriba y más canciones en el top = barra más larga. */
private fun score(hit: CountryHit): Int = (51 - hit.bestRank).coerceAtLeast(1) + (hit.count - 1) * 3

@Composable
private fun SearchSongRow(
    song: Track,
    detail: String,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onStats: () -> Unit,
    onShowOnMap: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = "Escuchar ${song.title}", onClick = onPlay)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        CoverImage(song.thumbnail, Modifier.size(48.dp), RoundedCornerShape(8.dp), sizePx = 144)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) scheme.primary else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onStats) { Icon(Icons.Rounded.BarChart, "Estadísticas de ${song.title}") }
        if (onShowOnMap != null) {
            IconButton(onClick = onShowOnMap) { Icon(Icons.Rounded.Public, "Ver ${song.title} en el mapa") }
        }
    }
}

@Composable
private fun CountryHitRow(hit: CountryHit, detail: String, fraction: Float, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = "Ver el top de ${hit.country.name}", onClick = onClick)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text(CountryNames.flag(hit.country.code), fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(hit.country.name, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth(fraction.coerceIn(0.04f, 1f))
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text("#${hit.bestRank}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (hit.bestRank <= 3) scheme.primary else scheme.onSurface)
    }
}

@Composable
private fun SectionLabel(text: String, onShowOnMap: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
        TextButton(onClick = onShowOnMap) { Text("Ver en el mapa") }
    }
}

/** Resultados del buscador: pestañas Artista / Canciones / Países. */
@Composable
internal fun MapSearchResults(
    query: String,
    search: MapSearch,
    tab: SearchTab,
    countriesLoaded: Int,
    currentSongId: String?,
    onTab: (SearchTab) -> Unit,
    onRadio: (Track) -> Unit,
    onShowOnMap: (Set<String>) -> Unit,
    onFocusCountry: (MapCountry) -> Unit,
    onPlaySong: (Track) -> Unit,
    onStats: (Track) -> Unit,
    catalogLoading: Boolean,
    bottomPadding: Dp,
) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(Modifier.heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 16.dp + bottomPadding)) {
        item(key = "tabs") {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchTab.entries.forEach { t ->
                    val count = when (t) {
                        SearchTab.ARTIST -> if (search.artist != null) 1 else 0
                        SearchTab.SONGS -> search.songs.size + search.catalog.size
                        SearchTab.COUNTRIES -> search.countries.size
                    }
                    FilterChip(selected = tab == t, enabled = count > 0, onClick = { onTab(t) }, label = { Text(t.label) })
                }
            }
        }

        if (search.isEmpty && catalogLoading) {
            item(key = "catalog_loading") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Buscando en toda la música…", style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else if (search.isEmpty) {
            item(key = "empty") {
                Text(
                    "«${query.trim()}» no está hoy en los tops de los $countriesLoaded países del mapa.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        } else when (tab) {
            SearchTab.ARTIST -> search.artist?.let { artist ->
                item(key = "artist") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        CoverImage(artist.thumbnail, Modifier.size(56.dp), CircleShape, sizePx = 168)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(artist.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "En el top de ${artist.hits.size} ${if (artist.hits.size == 1) "país" else "países"} hoy",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        artist.hits.firstOrNull()?.let { best ->
                            FilledTonalButton(onClick = { onRadio(best.bestSong) }) {
                                Icon(Icons.Rounded.Radio, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Radio")
                            }
                        }
                    }
                }
                item(key = "artist_label") {
                    SectionLabel("Dónde se escucha más") { onShowOnMap(artist.hits.map { it.country.code }.toSet()) }
                }
                val maxScore = artist.hits.maxOfOrNull { score(it) } ?: 1
                artist.hits.forEach { hit ->
                    item(key = "ah_${hit.country.code}") {
                        CountryHitRow(
                            hit = hit,
                            detail = (if (hit.count == 1) "1 canción en el top" else "${hit.count} canciones en el top") + ", mejor: ${hit.bestSong.title}",
                            fraction = score(hit).toFloat() / maxScore,
                            onClick = { onFocusCountry(hit.country) },
                        )
                    }
                }
            }

            SearchTab.SONGS -> {
                search.songs.forEach { result ->
                    item(key = "sr_${result.song.videoId}") {
                        val best = result.hits.first()
                        SearchSongRow(
                            song = result.song,
                            detail = "${result.song.artists}, en ${result.hits.size} " +
                                (if (result.hits.size == 1) "país" else "países") + ", mejor #${best.bestRank} en ${best.country.name}",
                            isCurrent = result.song.videoId == currentSongId,
                            onPlay = { onPlaySong(result.song) },
                            onStats = { onStats(result.song) },
                            onShowOnMap = { onShowOnMap(result.hits.map { it.country.code }.toSet()) },
                        )
                    }
                }
                if (search.catalog.isNotEmpty() || catalogLoading) {
                    item(key = "catalog_label") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                        ) {
                            Text("En toda la música", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f).semantics { heading() })
                            if (catalogLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                search.catalog.forEach { song ->
                    item(key = "cat_${song.videoId}") {
                        SearchSongRow(
                            song = song,
                            detail = "${song.artists}, no está en los tops de hoy",
                            isCurrent = song.videoId == currentSongId,
                            onPlay = { onPlaySong(song) },
                            onStats = { onStats(song) },
                            onShowOnMap = null,
                        )
                    }
                }
            }

            SearchTab.COUNTRIES -> search.countries.forEach { chart ->
                item(key = "sc_${chart.country.code}") {
                    val top = chart.songs.firstOrNull()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(onClickLabel = "Ver el top de ${chart.country.name}") { onFocusCountry(chart.country) }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    ) {
                        Text(CountryNames.flag(chart.country.code), fontSize = 22.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(chart.country.name, style = MaterialTheme.typography.titleSmall)
                            if (top != null) {
                                Text(
                                    "N.º 1: ${top.title}, ${top.artists}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "note") {
            Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Search, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Posición en los tops diarios de YouTube Music de $countriesLoaded países. No son reproducciones exactas.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 13.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}
