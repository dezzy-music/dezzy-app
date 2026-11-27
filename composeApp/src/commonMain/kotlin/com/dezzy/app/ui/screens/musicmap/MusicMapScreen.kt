/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.musicmap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.musicmap.CountryNames
import com.dezzy.app.feature.musicmap.MusicMapCharts
import com.dezzy.app.feature.musicmap.MusicMapGenres
import com.dezzy.app.feature.musicmap.MusicMapLocation
import com.dezzy.app.feature.musicmap.SongChartStats
import com.dezzy.app.feature.social.P2PMap
import com.dezzy.app.feature.social.P2PMapListener
import com.dezzy.app.feature.social.P2PSocial
import com.dezzy.app.source.youtube.YouTubeEndpoints
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.social.rememberPermissionRequester
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mapa musical: el mapa manda y la hoja acompaña. Mapa a pantalla completa (filtro nocturno sólo en
 * tema oscuro), búsqueda flotante, filtros Países / Personas / Cerca de mí y una hoja inferior con
 * el top del país elegido. Tocar a una persona abre su tarjeta con lo que escucha.
 */

private enum class PointKind { SELF, COUNTRY, PERSON }

/** Qué capa manda en el mapa: los países (con todo) o sólo las personas. */
private enum class MapMode { COUNTRIES, PEOPLE }

private data class MapPoint(
    val key: String,
    val kind: PointKind,
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val artworkUrl: String?,
    val countryCode: String? = null,
    val shortLabel: String = label,
    val userId: String? = null,
    val minZoom: Double = 0.0,
)

/** Una persona escuchando ahora, lista para pintar. */
private data class Person(
    val userId: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val track: Track?,
)

private sealed interface ListRow {
    data class Header(val country: MapCountry) : ListRow
    data class Song(val country: MapCountry, val rank: Int, val song: Track) : ListRow
}

/** Tamaño de la celda de agrupación (grados) según el zoom. 0 = sin agrupar. Sólo se agrupan personas. */
private fun cellSizeForZoom(zoom: Double): Double = when {
    zoom < 3.5 -> 45.0
    zoom < 4.5 -> 25.0
    zoom < 6.0 -> 12.0
    zoom < 7.5 -> 5.0
    zoom < 9.0 -> 2.0
    else -> 0.0
}

/** Distancia aproximada en km entre dos puntos (haversine). */
private fun distanceKm(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    fun rad(d: Double) = d * PI / 180.0
    val dLat = rad(b.first - a.first)
    val dLng = rad(b.second - a.second)
    val h = sin(dLat / 2).pow(2) + cos(rad(a.first)) * cos(rad(b.first)) * sin(dLng / 2).pow(2)
    return 2 * 6371.0 * asin(sqrt(h))
}

private val LIVE_GREEN = Color(0xFF34C759)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicMapScreen(ctx: ScreenContext) {
    val charts = koinInject<MusicMapCharts>()
    val genresService = koinInject<MusicMapGenres>()
    val location = koinInject<MusicMapLocation>()
    val p2pMap = koinInject<P2PMap>()
    val social = koinInject<P2PSocial>()
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f

    val shareLocation by location.shareEnabled.collectAsState(false)
    val profiles by social.profiles.collectAsState()

    var presences by remember { mutableStateOf<List<P2PMapListener>>(emptyList()) }
    var countryCharts by remember { mutableStateOf<List<CountryChart>>(emptyList()) }
    var chartsLoaded by remember { mutableStateOf(false) }
    var ownLatLng by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(MapMode.COUNTRIES) }
    var focusCountry by remember { mutableStateOf<String?>(null) }
    var selectedPersonId by remember { mutableStateOf<String?>(null) }
    val genres = remember { mutableStateMapOf<String, String>() }
    var genresLoading by remember { mutableStateOf(false) }
    var genreFilter by remember { mutableStateOf<String?>(null) }
    var highlight by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchTab by remember { mutableStateOf<SearchTab?>(null) }
    var previousRanks by remember { mutableStateOf<Map<String, Map<String, Int>>>(emptyMap()) }

    val chartStats = koinInject<SongChartStats>()
    val myTop = rememberMostPlayed()
    val historyRankings = rememberHistoryRankings(myTop, chartStats)

    val mapState = remember { TileMapState(20.0, 0.0, 3.0) }
    val scaffoldState = rememberBottomSheetScaffoldState()

    // ── Ubicación y permisos ─────────────────────────────────────────────────
    val requestLocation = rememberPermissionRequester { granted ->
        scope.launch {
            if (granted) {
                location.setShare(true)
                location.refresh()?.let { ownLatLng = it }
            } else {
                location.setShare(false)
                ownLatLng = null
            }
        }
    }

    fun toggleShareLocation() {
        scope.launch {
            if (shareLocation) {
                location.setShare(false)
                ownLatLng = null
            } else if (location.hasPermission()) {
                location.setShare(true)
                location.refresh()?.let { ownLatLng = it }
            } else {
                requestLocation(location.permissions)
            }
        }
    }

    LaunchedEffect(shareLocation) {
        if (shareLocation) {
            ownLatLng = location.stored()
            if (location.hasPermission()) location.refresh()?.let { ownLatLng = it }
        } else {
            ownLatLng = null
        }
    }

    // ── Top de cada país: caché local → YouTube Music, el mapa se pinta según llega ──
    LaunchedEffect(Unit) {
        val codes = MAP_COUNTRIES.map { it.code } + MusicMapCharts.GLOBAL
        fun toCharts(byCode: Map<String, List<Track>>): List<CountryChart> {
            val perCountry = MAP_COUNTRIES.mapNotNull { country ->
                byCode[country.code]?.takeIf { it.isNotEmpty() }?.let { CountryChart(country, it.take(10)) }
            }
            if (perCountry.isNotEmpty()) return perCountry
            return byCode[MusicMapCharts.GLOBAL]?.takeIf { it.isNotEmpty() }
                ?.let { listOf(CountryChart(MAP_WORLD, it.take(20))) }
                .orEmpty()
        }
        val loaded = runCatching {
            charts.load(codes) { partial ->
                val list = toCharts(partial)
                if (list.isNotEmpty()) countryCharts = list
            }
        }.getOrNull().orEmpty()
        toCharts(loaded).takeIf { it.isNotEmpty() }?.let { countryCharts = it }
        chartsLoaded = true
    }

    // Top de hace un día o más, para las flechas de tendencia.
    LaunchedEffect(countryCharts.size) {
        previousRanks = countryCharts.associate { it.country.code to charts.previousRanks(it.country.code) }
    }

    // ── Personas escuchando ahora (Nostr, cada 20 s) ─────────────────────────
    LaunchedEffect(Unit) {
        while (true) {
            presences = runCatching { p2pMap.listeners() }.getOrDefault(presences)
            delay(20_000L)
        }
    }

    val people = remember(presences, profiles, social.me) {
        presences.filter { it.pubkey != social.me }.map { p ->
            val song = p.song
            Person(
                userId = p.pubkey,
                displayName = profiles[p.pubkey]?.name?.takeIf { it.isNotBlank() } ?: "Alguien",
                latitude = p.latitude,
                longitude = p.longitude,
                track = song?.let { Track(it.videoId, it.title, it.artist, thumbnail = it.thumbnail) },
            )
        }
    }

    val ownPoint = remember(ownLatLng) {
        ownLatLng?.let { MapPoint("self", PointKind.SELF, it.first, it.second, "Tú", null) }
    }
    val countryPoints = remember(countryCharts) {
        countryCharts.mapNotNull { chart ->
            if (chart.country == MAP_WORLD) return@mapNotNull null
            val top = chart.songs.firstOrNull() ?: return@mapNotNull null
            MapPoint(
                key = "country_${chart.country.code}",
                kind = PointKind.COUNTRY,
                latitude = chart.country.latitude,
                longitude = chart.country.longitude,
                label = "${chart.country.name} · ${top.title}",
                artworkUrl = top.thumbnail,
                countryCode = chart.country.code,
                shortLabel = chart.country.name,
                minZoom = chart.country.minZoom,
            )
        }
    }
    val peoplePoints = remember(people) {
        people.map {
            MapPoint(
                key = "people_${it.userId}",
                kind = PointKind.PERSON,
                latitude = it.latitude,
                longitude = it.longitude,
                label = it.displayName,
                artworkUrl = it.track?.thumbnail,
                userId = it.userId,
            )
        }
    }
    val visiblePoints = remember(ownPoint, countryPoints, peoplePoints, mode) {
        when (mode) {
            MapMode.COUNTRIES -> countryPoints + peoplePoints + listOfNotNull(ownPoint)
            MapMode.PEOPLE -> peoplePoints + listOfNotNull(ownPoint)
        }
    }

    fun genreOf(song: Track): String? = genres[MusicMapGenres.key(song.firstArtist())]

    val focusedChart = remember(countryCharts, focusCountry) { countryCharts.firstOrNull { it.country.code == focusCountry } }

    LaunchedEffect(focusCountry) { genreFilter = null }

    // Clasifica por género a los artistas del país abierto (queda guardado en el dispositivo).
    LaunchedEffect(focusedChart) {
        val chart = focusedChart ?: return@LaunchedEffect
        if (chart.country == MAP_WORLD) return@LaunchedEffect
        genresLoading = true
        try {
            genresService.classify(chart.songs.map { it.firstArtist() }) { partial -> genres.putAll(partial) }
        } finally {
            genresLoading = false
        }
    }

    val shares by remember(focusedChart) {
        derivedStateOf { focusedChart?.let { chart -> genreShares(chart.songs) { genreOf(it) } }.orEmpty() }
    }

    // ── Búsqueda también en todo el catálogo ────────────────────────────────
    var catalog by remember { mutableStateOf<Pair<String, List<Track>>?>(null) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            catalog = null
            return@LaunchedEffect
        }
        delay(400)
        val items = ctx.client.search(q, YouTubeEndpoints.FILTER_SONGS).getOrNull()?.songs.orEmpty().take(15)
        catalog = q to items
    }
    val catalogLoading = query.trim().length >= 2 && catalog?.first != query.trim()

    val search = remember(query, countryCharts, catalog) {
        if (query.isBlank()) {
            null
        } else {
            val inCharts = searchCharts(query, countryCharts)
            val extra = catalog?.takeIf { it.first == query.trim() }?.second.orEmpty()
            val known = inCharts.songs.mapNotNullTo(HashSet()) { it.song.videoId }
            inCharts.copy(catalog = extra.filter { it.videoId !in known })
        }
    }

    fun playSong(song: Track) {
        ctx.player.playRadio(song, "mapa:${song.title}")
    }

    fun openStats(song: Track) {
        val id = song.videoId ?: return
        ctx.scope.launch {
            ctx.repository.saveTrack(song)
            ctx.navigator.push(Screen.SongStats(id))
        }
    }

    LaunchedEffect(query.isBlank()) {
        if (query.isBlank()) {
            highlight = emptySet()
            searchTab = null
        } else {
            selectedPersonId = null
            runCatching { scaffoldState.bottomSheetState.expand() }
        }
    }

    fun focusOn(country: MapCountry) {
        focusCountry = country.code
        if (country != MAP_WORLD) mapState.moveTo(country.latitude, country.longitude, 5.0)
    }

    fun centerOnMe(zoomTo: Double) {
        val loc = ownLatLng
        when {
            loc != null -> mapState.moveTo(loc.first, loc.second, zoomTo)
            !shareLocation -> toggleShareLocation()
            else -> scope.launch { location.refresh()?.let { ownLatLng = it } }
        }
    }

    // Sin país elegido: el top 5 de cada país. Con país: su top entero, filtrado por el chip de género.
    val rows by remember(countryCharts, focusCountry, genreFilter, shares) {
        derivedStateOf {
            val filter = genreFilter
            buildList {
                countryCharts.filter { focusCountry == null || it.country.code == focusCountry }.forEach { chart ->
                    val ranked = chart.songs.withIndex().toList()
                    val shown = when {
                        focusCountry == null -> ranked.take(5)
                        filter != null -> ranked.filter { (_, s) -> matchesGenre(genreOf(s), filter, shares) }
                        else -> ranked
                    }
                    if (shown.isNotEmpty()) {
                        add(ListRow.Header(chart.country))
                        shown.forEach { (index, song) -> add(ListRow.Song(chart.country, index + 1, song)) }
                    }
                }
            }
        }
    }

    val selectedPerson = remember(people, selectedPersonId) { people.firstOrNull { it.userId == selectedPersonId } }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 176.dp,
        sheetContainerColor = scheme.surface,
        sheetContent = {
            if (selectedPerson != null) {
                PersonSheet(
                    person = selectedPerson,
                    distanceKm = ownLatLng?.let { distanceKm(it, selectedPerson.latitude to selectedPerson.longitude) },
                    isPlaying = selectedPerson.track?.videoId != null && selectedPerson.track.videoId == ctx.playingId,
                    onClose = { selectedPersonId = null },
                    onListen = { selectedPerson.track?.let(::playSong) },
                    onProfile = { ctx.navigator.push(Screen.P2PProfile(selectedPerson.userId)) },
                )
            } else if (search != null) {
                val tab = searchTab?.takeIf {
                    when (it) {
                        SearchTab.ARTIST -> search.artist != null
                        SearchTab.SONGS -> search.hasSongs
                        SearchTab.COUNTRIES -> search.countries.isNotEmpty()
                    }
                } ?: search.firstTab()
                MapSearchResults(
                    query = query,
                    search = search,
                    tab = tab,
                    countriesLoaded = countryCharts.count { it.country != MAP_WORLD },
                    currentSongId = ctx.playingId,
                    onTab = { searchTab = it },
                    onRadio = { playSong(it) },
                    onShowOnMap = { codes ->
                        highlight = codes
                        focusCountry = null
                        mode = MapMode.COUNTRIES
                        mapState.moveTo(20.0, 0.0, 2.6)
                        scope.launch { runCatching { scaffoldState.bottomSheetState.partialExpand() } }
                    },
                    onFocusCountry = { country ->
                        query = ""
                        mode = MapMode.COUNTRIES
                        focusOn(country)
                    },
                    onPlaySong = { playSong(it) },
                    onStats = { openStats(it) },
                    catalogLoading = catalogLoading,
                    bottomPadding = 0.dp,
                )
            } else {
                val focus = countryCharts.firstOrNull { it.country.code == focusCountry }?.country
                val songsShown = rows.filterIsInstance<ListRow.Song>().map { it.song }
                TopSheetHeader(
                    flag = focus?.code?.let(CountryNames::flag),
                    title = when {
                        focus != null -> "Top en ${focus.name}"
                        countryCharts.singleOrNull()?.country == MAP_WORLD -> "Top mundial"
                        else -> "Top por país"
                    },
                    subtitle = when {
                        !chartsLoaded && countryCharts.isEmpty() -> "Cargando canciones…"
                        focus != null && genreFilter != null ->
                            "${cancionesLabel(songsShown.size)} de $genreFilter, " +
                                if (songsShown.size == 1) "toca para escucharla" else "toca una para escucharla"
                        focus != null -> "Top de hoy, ${cancionesLabel(songsShown.size)}"
                        else -> "Toca un país en el mapa para ver su top"
                    },
                    showClear = focus != null,
                    canPlay = songsShown.isNotEmpty(),
                    onClear = { focusCountry = null },
                    onPlayAll = {
                        val title = focus?.let { f -> genreFilter?.let { "$it en ${f.name}" } ?: "Top en ${f.name}" } ?: "Top del mapa musical"
                        ctx.player.playTracks(songsShown, 0, title)
                    },
                )
                if (rows.isEmpty() && focus == null) {
                    Text(
                        text = if (chartsLoaded) "No se pudieron cargar los tops. Revisa la conexión." else "Cargando canciones…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        if (focus != null && focus != MAP_WORLD) {
                            item(key = "genres") {
                                GenreShareSection(
                                    shares = shares,
                                    loading = genresLoading,
                                    selected = genreFilter,
                                    onSelect = { genreFilter = it },
                                    emptyText = "Aún no sabemos los géneros de este top",
                                    loadingText = "Analizando los géneros del top…",
                                )
                            }
                        }
                        items(rows.size, key = { i ->
                            when (val r = rows[i]) {
                                is ListRow.Header -> "h_${r.country.code}"
                                is ListRow.Song -> "s_${r.country.code}_${r.song.videoId}"
                            }
                        }) { i ->
                            when (val r = rows[i]) {
                                is ListRow.Header -> if (focusCountry == null) {
                                    CountryHeader(r.country) { focusOn(r.country) }
                                }
                                is ListRow.Song -> TopSongRow(
                                    rank = r.rank,
                                    song = r.song,
                                    isCurrent = r.song.videoId == ctx.playingId,
                                    previousRanks = previousRanks[r.country.code].orEmpty(),
                                    onClick = { playSong(r.song) },
                                    onStats = { openStats(r.song) },
                                )
                            }
                        }
                        if (focus == null) historyRankingsSection(ctx, myTop, historyRankings)
                    }
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            TileMap(mapState, dark, Modifier.fillMaxSize()) {
                val zoom = mapState.zoom
                val cell = cellSizeForZoom(zoom)

                // Países
                visiblePoints.filter { it.kind == PointKind.COUNTRY }.forEach { p ->
                    val highlighted = p.countryCode in highlight
                    if (!highlighted && p.countryCode != focusCountry && zoom < p.minZoom) return@forEach
                    val selected = p.countryCode == focusCountry || highlighted
                    MapMarker(mapState, p.latitude, p.longitude) {
                        CountryPill(p.shortLabel, p.artworkUrl, selected) {
                            focusCountry = if (focusCountry == p.countryCode) null else p.countryCode
                            selectedPersonId = null
                            scope.launch { runCatching { scaffoldState.bottomSheetState.partialExpand() } }
                        }
                    }
                }

                // Personas (agrupadas según el zoom)
                val personPoints = visiblePoints.filter { it.kind == PointKind.PERSON }
                val clusters: List<Triple<Double, Double, List<MapPoint>>> = if (cell <= 0.0) {
                    personPoints.map { Triple(it.latitude, it.longitude, listOf(it)) }
                } else {
                    val buckets = HashMap<Pair<Int, Int>, MutableList<MapPoint>>()
                    for (p in personPoints) {
                        buckets.getOrPut(floor(p.latitude / cell).toInt() to floor(p.longitude / cell).toInt()) { mutableListOf() }.add(p)
                    }
                    buckets.values.map { pts -> Triple(pts.map { it.latitude }.average(), pts.map { it.longitude }.average(), pts) }
                }
                clusters.forEach { (lat, lng, pts) ->
                    if (pts.size > 1) {
                        MapMarker(mapState, lat, lng) {
                            ClusterDot(pts.size) { mapState.moveTo(lat, lng, zoom + 2.0) }
                        }
                    } else {
                        val p = pts[0]
                        MapMarker(mapState, p.latitude, p.longitude) {
                            PersonDot(p.artworkUrl, p.userId == selectedPersonId, p.label) {
                                selectedPersonId = p.userId
                                scope.launch { runCatching { scaffoldState.bottomSheetState.expand() } }
                            }
                        }
                    }
                }

                // Tu punto, siempre encima
                visiblePoints.firstOrNull { it.kind == PointKind.SELF }?.let { p ->
                    MapMarker(mapState, p.latitude, p.longitude) { SelfDot() }
                }
            }

            // ── Búsqueda flotante, filtros y barra de ubicación ──────────────
            Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp)) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = scheme.surfaceContainerHigh,
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { ctx.navigator.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") }
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Artista, canción o país", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Borrar búsqueda") }
                        } else {
                            Icon(Icons.Rounded.Search, null, tint = scheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(8.dp))
                    MapModeChip("Países", Icons.Rounded.LocationOn, mode == MapMode.COUNTRIES) { mode = MapMode.COUNTRIES }
                    MapModeChip("Personas", Icons.Rounded.Group, mode == MapMode.PEOPLE) {
                        mode = MapMode.PEOPLE
                        focusCountry = null
                    }
                    ElevatedAssistChip(
                        onClick = {
                            mode = MapMode.PEOPLE
                            focusCountry = null
                            centerOnMe(11.0)
                        },
                        label = { Text("Cerca de mí") },
                        leadingIcon = { Icon(Icons.Rounded.NearMe, null, Modifier.size(18.dp)) },
                    )
                    Spacer(Modifier.width(8.dp))
                }

                if (mode == MapMode.PEOPLE) {
                    ShareLocationBar(shareLocation, { toggleShareLocation() }, Modifier.padding(horizontal = 16.dp))
                }
            }

            FloatingActionButton(
                onClick = { centerOnMe(10.0) },
                containerColor = scheme.surfaceContainerHigh,
                contentColor = if (ownLatLng != null) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 192.dp),
            ) {
                Icon(Icons.Rounded.MyLocation, if (ownLatLng != null) "Centrar en mi ubicación" else "Compartir mi ubicación")
            }
        }
    }
}

// ── Hoja: top del país ───────────────────────────────────────────────────────

@Composable
private fun TopSheetHeader(
    flag: String?,
    title: String,
    subtitle: String,
    showClear: Boolean,
    canPlay: Boolean,
    onClear: () -> Unit,
    onPlayAll: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (flag != null) {
                    Text(flag, fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (showClear) {
            IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, "Ver todos los países") }
        }
        FilledTonalIconButton(onClick = onPlayAll, enabled = canPlay) { Icon(Icons.Rounded.PlayArrow, "Reproducir el top") }
    }
}

@Composable
private fun CountryHeader(country: MapCountry, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = "Ver el top de ${country.name}", onClick = onClick)
            .padding(horizontal = 24.dp),
    ) {
        Text(CountryNames.flag(country.code), fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Text(country.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
    }
}

@Composable
internal fun TopSongRow(
    rank: Int,
    song: Track,
    isCurrent: Boolean,
    previousRanks: Map<String, Int>,
    onClick: () -> Unit,
    onStats: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(
            rank.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = if (rank <= 3 || isCurrent) scheme.primary else scheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp),
        )
        CoverImage(song.thumbnail, Modifier.size(48.dp), RoundedCornerShape(8.dp), sizePx = 144)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) scheme.primary else scheme.onSurface,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(song.artists, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TrendBadge(rank = rank, previousRanks = previousRanks, songId = song.videoId)
        IconButton(onClick = onStats) { Icon(Icons.Rounded.BarChart, "Estadísticas de ${song.title}") }
    }
}

// ── Hoja: persona elegida ────────────────────────────────────────────────────

@Composable
private fun PersonSheet(
    person: Person,
    distanceKm: Double?,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onListen: () -> Unit,
    onProfile: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val track = person.track
    Column(Modifier.padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(scheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    person.displayName.trim().firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    person.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                val where = distanceKm?.let { "A unos ${it.roundToInt().coerceAtLeast(1)} km" } ?: "Cerca de ti"
                Text(
                    if (track != null) "$where, escuchando ahora" else where,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Cerrar") }
        }

        if (track != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = scheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverImage(track.thumbnail, Modifier.size(56.dp), RoundedCornerShape(10.dp), sizePx = 168)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artists, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledIconButton(onClick = onListen) {
                        Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pausar" else "Reproducir ${track.title}")
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onListen, enabled = track != null, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Headphones, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Escuchar")
            }
            FilledTonalButton(onClick = onProfile, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Ver perfil") }
        }
    }
}

// ── Controles flotantes ──────────────────────────────────────────────────────

@Composable
private fun MapModeChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    ElevatedFilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(if (selected) Icons.Rounded.Check else icon, null, Modifier.size(FilterChipDefaults.IconSize)) },
    )
}

@Composable
private fun ShareLocationBar(sharing: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainerLowest, shadowElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .toggleable(value = sharing, role = Role.Switch, onValueChange = { onToggle() })
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.LocationOn, null, tint = if (sharing) scheme.primary else scheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (sharing) "Compartes tu zona" else "No compartes tu ubicación", style = MaterialTheme.typography.bodyLarge)
                Text("Zona aproximada, nunca tu punto exacto", style = MaterialTheme.typography.bodyMedium, fontSize = 13.sp, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = sharing, onCheckedChange = null)
        }
    }
}

// ── Marcadores ───────────────────────────────────────────────────────────────

/** Píldora de país: portada redonda y nombre. */
@Composable
private fun CountryPill(name: String, cover: String?, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(if (selected) scheme.primary else scheme.surfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
    ) {
        CoverImage(cover, Modifier.size(28.dp), CircleShape, sizePx = 96)
        Spacer(Modifier.width(8.dp))
        Text(
            name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) scheme.onPrimary else scheme.onSurface,
            maxLines = 1,
        )
    }
}

/** Persona: círculo con su portada, aro blanco y punto verde «en vivo». */
@Composable
private fun PersonDot(cover: String?, selected: Boolean, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(44.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(if (selected) scheme.primary else Color.White)
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(3.dp),
    ) {
        CoverImage(cover, Modifier.fillMaxSize(), CircleShape, sizePx = 120)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(12.dp)
                .clip(CircleShape)
                .background(Color.White)
                .padding(2.dp)
                .clip(CircleShape)
                .background(LIVE_GREEN),
        )
    }
}

/** Grupo de personas: círculo de color primario con el número. */
@Composable
private fun ClusterDot(count: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val d = (36 + minOf(count - 1, 10) * 2).dp
    Box(
        Modifier
            .size(d)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(scheme.primary)
            .border(2.dp, Color.White, CircleShape)
            .clickable(onClickLabel = "$count personas aquí", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else count.toString(), color = scheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** Tu posición: punto de color primario con halo. */
@Composable
private fun SelfDot() {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.size(28.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Box(Modifier.size(13.dp).clip(CircleShape).background(scheme.primary))
        }
    }
}
