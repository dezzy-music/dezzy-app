/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.feature.stats.RankedName
import com.dezzy.app.feature.stats.RankedTrack
import com.dezzy.app.feature.stats.StatsDates
import com.dezzy.app.feature.stats.StatsStore
import com.dezzy.app.feature.stats.YearInMusic
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.components.glass.DezzyButton
import com.dezzy.app.ui.components.glass.DezzyFilterChip
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.player.kit.rememberShareText
import com.dezzy.app.ui.screens.Load
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.TopSpacer
import com.dezzy.app.ui.screens.rememberLoad
import org.koin.compose.koinInject

private val StoryBrushes = listOf(
    listOf(Color(0xFF7B2CBF), Color(0xFF240046)),
    listOf(Color(0xFF0F4C81), Color(0xFF0A1931)),
    listOf(Color(0xFFB5179E), Color(0xFF3A0CA3)),
    listOf(Color(0xFF1B7F5C), Color(0xFF06231A)),
    listOf(Color(0xFFD9480F), Color(0xFF3B0D0D)),
    listOf(Color(0xFF3A0CA3), Color(0xFF03045E)),
    listOf(Color(0xFF9D0208), Color(0xFF370617)),
    listOf(Color(0xFF008F7A), Color(0xFF002B36)),
    listOf(Color(0xFF560BAD), Color(0xFF10002B)),
)

/**
 * «Tu año en música»: resumen anual en páginas que se deslizan, como una historia. Minutos,
 * tu artista y tus canciones, álbum, mes y día más escuchados, racha y tu personalidad de oyente.
 */
@Composable
fun YearInMusicScreen(ctx: ScreenContext) {
    val store: StatsStore = koinInject()
    val offset = remember { StatsDates.offsetMs() }
    val years by produceState<List<Int>?>(null) { value = store.availableYears() }
    var picked by rememberSaveable { mutableStateOf<Int?>(null) }
    val year = picked ?: years?.firstOrNull()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val list = years
        when {
            list == null || (year == null && list.isNotEmpty()) -> Unit
            list.isEmpty() || year == null -> StoryEmpty(onBack = { ctx.navigator.back() })
            else -> {
                val load = rememberLoad(year) { runCatching { store.yearInMusic(year, offset) } }
                when (load) {
                    is Load.Loading -> Unit
                    is Load.Failed -> StoryEmpty(onBack = { ctx.navigator.back() }, text = "No se pudo calcular tu año.")
                    is Load.Done -> {
                        val data = load.value
                        if (data == null) StoryEmpty(onBack = { ctx.navigator.back() }, text = "No hay música guardada en $year.")
                        else Story(ctx, data, years = list, onYear = { picked = it })
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryEmpty(onBack: () -> Unit, text: String = "Escucha música unos días y vuelve: aquí verás tu año.") {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        DezzyButton(onClick = onBack) { Text("Volver") }
    }
}

@Composable
private fun Story(ctx: ScreenContext, data: YearInMusic, years: List<Int>, onYear: (Int) -> Unit) {
    val share = rememberShareText()
    val pages = buildList<@Composable (Modifier) -> Unit> {
        add { IntroPage(data, it) }
        add { MinutesPage(data, it) }
        if (data.topArtists.isNotEmpty()) add { ArtistsPage(ctx, data.topArtists, it) }
        if (data.topTracks.isNotEmpty()) add { SongsPage(ctx, data.topTracks, it) }
        data.topAlbum?.let { album -> add { AlbumPage(ctx, album, it) } }
        if (data.busiestMonth != null) add { MonthsPage(data, it) }
        add { DaysPage(data, it) }
        add { PersonalityPage(data, it) }
        add {
            OutroPage(
                data = data,
                onShare = { share(shareText(data)) },
                onStats = { ctx.navigator.push(Screen.Stats) },
                modifier = it,
            )
        }
    }
    val pager = rememberPagerState(pageCount = { pages.size })
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
            val colors = StoryBrushes[index % StoryBrushes.size]
            pages[index](Modifier.fillMaxSize().background(Brush.verticalGradient(colors)))
        }
        Column(Modifier.fillMaxWidth()) {
            TopSpacer()
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(pages.size) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (i <= pager.currentPage) 0.95f else 0.3f)),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { ctx.navigator.back() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", tint = Color.White)
                }
                if (years.size > 1) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(years.size) { i ->
                            val y = years[i]
                            DezzyFilterChip(selected = y == data.year, onClick = { onYear(y) }, label = { Text("$y") })
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(onClick = { share(shareText(data)) }) {
                    Icon(Icons.Rounded.Share, "Compartir", tint = Color.White)
                }
            }
        }
    }
}

private fun shareText(d: YearInMusic): String {
    val parts = mutableListOf("Mi ${d.year} en Dezzy: ${d.totalMs / 3_600_000L} horas de música")
    d.topArtists.firstOrNull()?.let { parts += "mi artista fue ${it.name}" }
    d.topTracks.firstOrNull()?.let { parts += "mi canción, ${it.track.title}" }
    return parts.joinToString(", ") + "."
}

// Páginas -------------------------------------------------------------------

@Composable
private fun StoryPage(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun Kicker(text: String) =
    Text(text.uppercase(), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)

@Composable
private fun Big(text: String, size: Int = 56) =
    Text(text, color = Color.White, fontSize = size.sp, lineHeight = (size + 6).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

@Composable
private fun Caption(text: String) =
    Text(text, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)

@Composable
private fun IntroPage(d: YearInMusic, modifier: Modifier) = StoryPage(modifier) {
    Kicker("Tu año en música")
    Spacer(Modifier.height(12.dp))
    Big("${d.year}", 88)
    Spacer(Modifier.height(12.dp))
    Caption("Desliza para ver lo que sonó")
}

@Composable
private fun MinutesPage(d: YearInMusic, modifier: Modifier) = StoryPage(modifier) {
    val minutes = d.totalMs / 60_000L
    Kicker("Escuchaste")
    Big("$minutes", 72)
    Caption("minutos de música")
    Spacer(Modifier.height(20.dp))
    Caption("${d.totalPlays} reproducciones de ${d.distinctTracks} canciones y ${d.distinctArtists} artistas")
}

@Composable
private fun ArtistsPage(ctx: ScreenContext, artists: List<RankedName>, modifier: Modifier) = StoryPage(modifier) {
    val top = artists.first()
    Kicker("Tu artista del año")
    Spacer(Modifier.height(16.dp))
    CoverImage(top.thumbnail, Modifier.size(180.dp), CircleShape, sizePx = 540)
    Spacer(Modifier.height(16.dp))
    Big(top.name, 34)
    Caption("${top.plays} reproducciones")
    if (artists.size > 1) {
        Spacer(Modifier.height(20.dp))
        artists.drop(1).forEachIndexed { i, a ->
            Text("${i + 2}. ${a.name}", color = Color.White, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.height(16.dp))
    DezzyButton(onClick = { openArtistByName(ctx, top.name) }) { Text("Ver artista") }
}

@Composable
private fun SongsPage(ctx: ScreenContext, tracks: List<RankedTrack>, modifier: Modifier) = StoryPage(modifier) {
    val top = tracks.first()
    Kicker("Tu canción del año")
    Spacer(Modifier.height(16.dp))
    CoverImage(top.track.thumbnail, Modifier.size(180.dp), RoundedCornerShape(20.dp), sizePx = 540)
    Spacer(Modifier.height(16.dp))
    Big(top.track.title, 30)
    Caption("${top.track.artists}, ${top.plays} veces")
    if (tracks.size > 1) {
        Spacer(Modifier.height(20.dp))
        tracks.drop(1).forEachIndexed { i, t ->
            Text("${i + 2}. ${t.track.title}", color = Color.White, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.height(16.dp))
    DezzyButton(onClick = { ctx.player.playTracks(tracks.map { it.track }, source = "Tu año en música") }) { Text("Reproducir tu top") }
}

@Composable
private fun AlbumPage(ctx: ScreenContext, album: RankedName, modifier: Modifier) = StoryPage(modifier) {
    Kicker("Tu álbum del año")
    Spacer(Modifier.height(16.dp))
    CoverImage(album.thumbnail, Modifier.size(180.dp), RoundedCornerShape(20.dp), sizePx = 540)
    Spacer(Modifier.height(16.dp))
    Big(album.name, 30)
    album.subtitle?.let { Caption(it) }
    Caption("${album.plays} reproducciones")
    Spacer(Modifier.height(16.dp))
    DezzyButton(onClick = { openAlbumByName(ctx, album.name, album.subtitle) }) { Text("Ver álbum") }
}

@Composable
private fun MonthsPage(d: YearInMusic, modifier: Modifier) = StoryPage(modifier) {
    val month = d.busiestMonth ?: 0
    Kicker("Tu mes más musical")
    Big(StatsDates.MONTHS_LONG[month].replaceFirstChar { it.uppercase() }, 48)
    Caption("${d.monthMs[month] / 3_600_000L} h ${(d.monthMs[month] / 60_000L) % 60} min")
    Spacer(Modifier.height(24.dp))
    val max = d.monthMs.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        d.monthMs.forEachIndexed { i, ms ->
            val fraction = (ms.toFloat() / max).coerceIn(0.03f, 1f)
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                if (fraction < 1f) Spacer(Modifier.weight(1f - fraction))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(fraction, fill = true)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(Color.White.copy(alpha = if (i == month) 1f else 0.4f)),
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatsDates.MONTHS_SHORT.forEach {
            Text(it.take(1).uppercase(), Modifier.weight(1f), color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun DaysPage(d: YearInMusic, modifier: Modifier) = StoryPage(modifier) {
    if (d.busiestDayLabel != null) {
        Kicker("Tu día más musical")
        Big(d.busiestDayLabel, 36)
        Caption(formatMinutes(d.busiestDayMs) + " de música")
        Spacer(Modifier.height(28.dp))
    }
    Kicker("Tu mejor racha")
    Big("${d.longestStreakDays}", 64)
    Caption(if (d.longestStreakDays == 1) "día seguido" else "días seguidos")
}

@Composable
private fun PersonalityPage(d: YearInMusic, modifier: Modifier) = StoryPage(modifier) {
    Kicker("Tu personalidad de oyente")
    Spacer(Modifier.height(12.dp))
    Big(d.personality, 44)
    d.peakHour?.let {
        Spacer(Modifier.height(12.dp))
        Caption("Tu hora favorita: las ${it.toString().padStart(2, '0')}:00")
    }
    if (d.favoritesAdded > 0) {
        Spacer(Modifier.height(20.dp))
        Caption("Y guardaste ${d.favoritesAdded} canciones en Me gusta")
    }
}

@Composable
private fun OutroPage(data: YearInMusic, onShare: () -> Unit, onStats: () -> Unit, modifier: Modifier) = StoryPage(modifier) {
    Kicker("Eso fue tu ${data.year}")
    Spacer(Modifier.height(12.dp))
    Big("Gracias por escuchar", 36)
    Spacer(Modifier.height(24.dp))
    DezzyButton(onClick = onShare) { Text("Compartir") }
    Spacer(Modifier.height(8.dp))
    DezzyButton(onClick = onStats) { Text("Ver más estadísticas") }
}
