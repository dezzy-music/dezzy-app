/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.dezzy.app.core.model.MusicItemType
import com.dezzy.app.source.youtube.YouTubeCatalogParsers
import com.dezzy.app.source.youtube.YouTubeEndpoints
import com.dezzy.app.source.youtube.YouTubeMusicClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private val WelcomeBg = Color(0xFF140D0E)
private val WelcomeText = Color(0xFFFFDADC)
private val WelcomeMuted = Color(0xFFD7C1C2)
private val WelcomeAccent = Color(0xFFFFB2B9)
private val SpotifyGreen = Color(0xFF1ED760)

/** Una foto de la cinta: artista en círculo o portada cuadrada. */
private data class WelcomeTile(val key: String, val name: String, val thumb: String?, val round: Boolean)

/**
 * Pantalla de la primera vez que se abre la app: cintas de artistas reales de YouTube Music
 * arriba y, abajo, Spotify y YouTube Music para iniciar sesión de una vez. Cada tarjeta pasa
 * a «Conectado» al volver del login y entonces aparece «Continuar».
 */
@Composable
fun WelcomeScreen(
    client: YouTubeMusicClient,
    spotifyLinked: Boolean,
    youtubeLinked: Boolean,
    onSpotifyLogin: () -> Unit,
    onYoutubeMusicLogin: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
) {
    var tiles by remember { mutableStateOf<List<WelcomeTile>>(emptyList()) }
    var lyricLabel by remember { mutableStateOf<String?>(null) }

    // Al conectar YouTube Music el inicio pasa a ser el de la cuenta y las cintas cambian con él.
    LaunchedEffect(youtubeLinked) {
        val loaded = runCatching { loadWelcomeTiles(client) }.getOrNull() ?: return@LaunchedEffect
        if (loaded.first.size >= 6) tiles = loaded.first
        lyricLabel = loaded.second
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WelcomeBg)
            // Tapa todo lo de detrás: nada de lo de abajo recibe toques.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .verticalScroll(rememberScrollState()),
    ) {
        WelcomeHero(tiles = tiles, lyricLabel = lyricLabel)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-30).dp)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    text = "Toda tu música, en un sitio",
                    color = WelcomeText,
                    fontSize = 30.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "Inicia sesión y trae tu biblioteca de una vez.",
                    color = WelcomeMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            AccountCard(
                gradient = listOf(Color(0xFF0F3D22), Color(0xFF162B1D), Color(0xFF1C1A1A)),
                logo = SpotifyLogo,
                title = "Spotify",
                subtitle = "Playlists, me gusta y artistas",
                linked = spotifyLinked,
                buttonText = "Continuar con Spotify",
                buttonColor = SpotifyGreen,
                buttonContent = Color.Black,
                buttonLogo = SpotifyMonoLogo,
                onClick = onSpotifyLogin,
            )
            AccountCard(
                gradient = listOf(Color(0xFF4A1010), Color(0xFF2E1414), Color(0xFF1C1A1A)),
                logo = YouTubeMusicLogo,
                title = "YouTube Music",
                subtitle = "Tu inicio, mixes y videoclips",
                linked = youtubeLinked,
                buttonText = "Continuar con YouTube Music",
                buttonColor = Color.White,
                buttonContent = Color(0xFF0F0F0F),
                buttonLogo = YouTubeMusicLogo,
                onClick = onYoutubeMusicLogin,
            )

            val anyLinked = spotifyLinked || youtubeLinked
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (anyLinked) "Puedes conectar la otra cuando quieras" else "Puedes hacerlo luego en Ajustes",
                    color = WelcomeMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                if (anyLinked) {
                    Button(
                        onClick = onContinue,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WelcomeAccent,
                            contentColor = Color(0xFF5F1320),
                        ),
                    ) { Text("Continuar") }
                } else {
                    TextButton(onClick = onSkip) {
                        Text("Ahora no", color = WelcomeAccent)
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeHero(tiles: List<WelcomeTile>, lyricLabel: String?) {
    // Sin red: degradados en lugar de fotos.
    val shown = tiles.ifEmpty { List(18) { WelcomeTile("g$it", "", null, it % 4 != 3) } }
    val third = (shown.size + 2) / 3
    val rows = listOf(shown.take(third), shown.drop(third).take(third), shown.drop(third * 2))
        .map { row -> if (row.size >= 5) row else (row + shown).take(6) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(
                Brush.radialGradient(
                    0f to Color(0xFF6E2430),
                    0.5f to Color(0xFF3A1D22),
                    0.95f to WelcomeBg,
                    center = Offset.Zero,
                    radius = 1100f,
                ),
            )
            .clearAndSetSemantics { },
    ) {
        Column(
            modifier = Modifier
                .requiredWidth(560.dp)
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .rotate(-8f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            rows.forEachIndexed { i, row ->
                TileRibbon(row, reverse = i == 1, durationMs = if (i == 1) 46_000 else 36_000)
            }
        }
        // Fundido hacia el fondo para que el título se lea.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.5f to Color.Transparent, 0.94f to WelcomeBg)),
        )
        val bubble = RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp)
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, top = 24.dp)
                .widthIn(max = 210.dp)
                .rotate(-3f)
                .shadow(10.dp, bubble)
                .background(WelcomeAccent.copy(alpha = 0.95f), bubble)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = lyricLabel ?: "Letras sincronizadas",
                color = Color(0xFF4D0A16).copy(alpha = 0.75f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("Cada verso a su tiempo", color = Color(0xFF4D0A16), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 206.dp, end = 14.dp)
                .rotate(3f)
                .shadow(10.dp, RoundedCornerShape(16.dp))
                .background(Color(0xFF231819), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.Favorite, null, tint = WelcomeAccent, modifier = Modifier.size(16.dp))
            Text("Tus me gusta, siempre", color = WelcomeText, fontSize = 12.sp)
        }
    }
}

/** Fila que se desplaza sin fin: se pinta dos veces y avanza media anchura. */
@Composable
private fun TileRibbon(row: List<WelcomeTile>, reverse: Boolean, durationMs: Int) {
    val step = 102.dp // 92 de foto + 10 de hueco
    val halfPx = with(LocalDensity.current) { (step * row.size).toPx() }
    val progress by rememberInfiniteTransition(label = "ribbon").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart),
        label = "ribbonProgress",
    )
    Row(
        modifier = Modifier
            .requiredWidth(step * row.size * 2)
            .graphicsLayer {
                val p = if (reverse) 1f - progress else progress
                translationX = halfPx / 2f - p * halfPx
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        (row + row).forEachIndexed { i, tile ->
            val shape = if (tile.round) CircleShape else RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .shadow(10.dp, shape)
                    .clip(shape)
                    .background(Brush.linearGradient(TileGradients[i % TileGradients.size])),
            ) {
                if (tile.thumb != null) {
                    AsyncImage(
                        model = tile.thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))),
                    )
                    Text(
                        text = tile.name,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}

private val TileGradients = listOf(
    listOf(Color(0xFF6E2430), Color(0xFF3A1D22)),
    listOf(Color(0xFF5B3A1F), Color(0xFF2A1B10)),
    listOf(Color(0xFF3C2A55), Color(0xFF1E1530)),
    listOf(Color(0xFF1F4A3A), Color(0xFF10261E)),
)

@Composable
private fun AccountCard(
    gradient: List<Color>,
    logo: ImageVector,
    title: String,
    subtitle: String,
    linked: Boolean,
    buttonText: String,
    buttonColor: Color,
    buttonContent: Color,
    buttonLogo: ImageVector,
    onClick: () -> Unit,
) {
    val logoPainter = rememberVectorPainter(logo)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(gradient)),
    ) {
        // Logo grande de fondo, muy suave.
        Image(
            painter = logoPainter,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 26.dp, y = (-26).dp)
                .size(130.dp)
                .graphicsLayer { alpha = 0.13f },
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(logoPainter, contentDescription = null, modifier = Modifier.size(40.dp))
                Column {
                    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
                }
            }
            Button(
                onClick = onClick,
                enabled = !linked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = buttonContent,
                    disabledContainerColor = Color.White.copy(alpha = 0.12f),
                    disabledContentColor = Color.White,
                ),
            ) {
                if (linked) {
                    Icon(Icons.Rounded.Check, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Conectado")
                } else {
                    Image(rememberVectorPainter(buttonLogo), contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(buttonText, maxLines = 1)
                }
            }
        }
    }
}

// Logos de 24×24 (los mismos trazos que los de la app actual).
private const val SpotifyWaves =
    "M17.47,17.29a0.84,0.84 0,0 1,-1.15 0.27c-3.14,-1.92 -7.1,-2.36 -11.75,-1.3a0.84,0.84 0,0 1,-0.38 -1.63c5.09,-1.16 9.48,-0.65 13.01,1.51a0.84,0.84 0,0 1,0.27 1.15zM18.91,14.07a1.05,1.05 0,0 1,-1.45 0.35c-3.58,-2.2 -9.04,-2.83 -13.06,-1.55a1.05,1.05 0,1 1,-0.64 -2.01c4.63,-1.48 10.66,-0.78 14.81,1.76a1.05,1.05 0,0 1,0.34 1.45zM19.04,10.62c-4.28,-2.54 -11.35,-2.77 -15.42,-1.53a1.26,1.26 0,0 1,-0.73 -2.4c4.67,-1.42 12.48,-1.15 17.42,1.78a1.26,1.26 0,1 1,-1.27 2.15z"

private fun circle(r: Float) = "M12,12m-$r,0a$r,$r 0,1 1,${r * 2},0a$r,$r 0,1 1,-${r * 2},0"

private fun logo(name: String, vararg paths: Pair<Color, String>): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { (color, data) -> addPath(addPathNodes(data), fill = SolidColor(color)) }
    }.build()

private val SpotifyLogo = logo("spotify", SpotifyGreen to circle(12f), Color.White to SpotifyWaves)
private val SpotifyMonoLogo = logo("spotifyMono", Color.Black to circle(12f), SpotifyGreen to SpotifyWaves)
private val YouTubeMusicLogo = logo(
    "youtubeMusic",
    Color(0xFFFF0000) to circle(12f),
    Color.White to circle(9.5f),
    Color(0xFFFF0000) to circle(7.5f),
    Color.White to "M9.5,7.5v9l7.5,-4.5z",
)

/**
 * Artistas y portadas del inicio de YouTube Music (los de la cuenta o, sin ella, los
 * generales del país). Sin cuenta casi no salen artistas: se buscan los de sus canciones.
 * Devuelve las fotos y una etiqueta «artista · canción» para el bocadillo de letras.
 */
private suspend fun loadWelcomeTiles(client: YouTubeMusicClient): Pair<List<WelcomeTile>, String?>? = coroutineScope {
    val home = client.getHome().getOrNull() ?: return@coroutineScope null
    val items = home.flatMap { it.items }
    val isSong = { t: MusicItemType -> t == MusicItemType.SONG || t == MusicItemType.VIDEO }
    val firstArtist = { subtitle: String -> subtitle.split(" • ", ",", " & ").firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } }
    val label = items.firstOrNull { isSong(it.type) }?.let { song ->
        listOfNotNull(firstArtist(song.subtitle), song.title).joinToString(" · ")
    }

    val artists = items.filter { it.type == MusicItemType.ARTIST }.toMutableList()
    if (artists.size < 8) {
        val names = items.mapNotNull {
            if (isSong(it.type) || it.type == MusicItemType.ALBUM) firstArtist(it.subtitle) else null
        }.distinct().take(12)
        artists += names.map { name ->
            async {
                client.search(name, YouTubeEndpoints.FILTER_ARTISTS).getOrNull()?.artists
                    ?.firstOrNull {
                        YouTubeCatalogParsers.normalize(it.title) == YouTubeCatalogParsers.normalize(name) && it.thumbnail != null
                    }
            }
        }.awaitAll().filterNotNull()
    }

    val seen = HashSet<String>()
    val artistTiles = artists.filter { it.thumbnail != null && seen.add(it.id) }
        .map { WelcomeTile(it.id, it.title, it.thumbnail, round = true) }
    val coverTiles = items.filter { (it.type == MusicItemType.ALBUM || isSong(it.type)) && it.thumbnail != null && seen.add(it.id) }
        .map { WelcomeTile(it.id, it.title, it.thumbnail, round = false) }

    // Artistas en círculo con una portada cuadrada cada cuatro.
    val out = ArrayList<WelcomeTile>(18)
    var a = 0
    var c = 0
    while (out.size < 18 && (a < artistTiles.size || c < coverTiles.size)) {
        val coverTurn = out.size % 4 == 3 || a >= artistTiles.size
        if (coverTurn && c < coverTiles.size) {
            out += coverTiles[c++]
        } else if (a < artistTiles.size) {
            out += artistTiles[a++]
        } else {
            out += coverTiles[c++]
        }
    }
    out to label
}
