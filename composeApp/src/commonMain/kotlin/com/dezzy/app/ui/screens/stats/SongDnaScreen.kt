/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

/*
 * SongDNA (estilo Spotify): combina DOS artistas (el principal de la canción y su artista más
 * afín) y, en base a ambos, recomienda NUEVOS artistas y canciones. Datos reales de YouTube Music:
 * artistas relacionados y canciones de cada semilla, mezclados y priorizando los que aparecen en
 * ambos (la verdadera «combinación»).
 *
 * Boceto A «La hélice que une a dos artistas»: cabecera en primaryContainer con la doble hélice,
 * Reproducir mezcla / Guardar, pestañas Artistas (puentes numerados) y Canciones (barra de color
 * según el lado).
 */

package com.dezzy.app.ui.screens.stats

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.dezzy.app.core.model.MusicItemType
import com.dezzy.app.core.model.Track
import com.dezzy.app.source.youtube.YouTubeMusicClient
import com.dezzy.app.ui.components.glass.DezzyButton as Button
import com.dezzy.app.ui.components.glass.DezzyOutlinedButton as OutlinedButton
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.player.kit.rememberShareText
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.TopSpacer
import com.dezzy.app.ui.screens.toTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/** De qué semilla viene una recomendación. */
private enum class DnaSide { SEED, PARTNER, BOTH }

private data class DnaPerson(val id: String, val name: String, val thumbnail: String?)

private data class DnaArtist(val person: DnaPerson, val side: DnaSide)

/** [bridge] = número del artista puente (1..n) al que pertenece la canción, si lo hay. */
private data class DnaSong(val track: Track, val side: DnaSide, val bridge: Int?)

private data class SongDnaResult(
    val seed: DnaPerson?,
    val partner: DnaPerson?,
    val partnerCount: Int,
    val artists: List<DnaArtist>,
    val songs: List<DnaSong>,
) {
    companion object {
        val Empty = SongDnaResult(null, null, 0, emptyList(), emptyList())
    }
}

/** Ficha de ADN de la canción [videoId]: parte de su artista principal. */
@Composable
fun SongDnaScreen(ctx: ScreenContext, videoId: String) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf(SongDnaResult.Empty) }
    var artistId by remember(videoId) { mutableStateOf<String?>(null) }
    // «Cambiar»: recorre los artistas afines como compañero de mezcla.
    var partnerIndex by rememberSaveable(videoId) { mutableIntStateOf(0) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val share = rememberShareText()

    LaunchedEffect(videoId, partnerIndex) {
        loading = true
        error = null
        if (artistId == null) artistId = resolveArtistId(ctx, videoId)
        val id = artistId
        if (id == null) {
            error = "Sin artista para analizar"
            loading = false
            return@LaunchedEffect
        }
        data = withContext(Dispatchers.Default) {
            runCatching { buildSongDna(ctx.client, id, partnerIndex) }.getOrNull() ?: SongDnaResult.Empty
        }
        if (data.seed == null && data.artists.isEmpty()) {
            error = "No se pudo generar el SongDNA de este artista"
        }
        loading = false
    }

    val seedName = data.seed?.name ?: "Tu artista"
    val partnerName = data.partner?.name ?: "Afín"
    val mixTitle = "$seedName × $partnerName"

    val playMix: () -> Unit = {
        val tracks = data.songs.map { it.track }
        if (tracks.isNotEmpty()) ctx.player.playTracks(tracks, source = mixTitle)
    }
    val saveMix: () -> Unit = {
        val tracks = data.songs.map { it.track }
        if (tracks.isNotEmpty()) {
            ctx.scope.launch {
                val playlist = ctx.repository.createPlaylist(mixTitle)
                tracks.forEach { ctx.repository.addTrackToPlaylist(playlist.id, it) }
                ctx.toast("Guardada como «$mixTitle»")
            }
        }
    }
    val shareMix: () -> Unit = {
        val link = artistId?.let { " https://music.youtube.com/channel/$it" }.orEmpty()
        share("SongDNA de $mixTitle en Dezzy:$link")
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "top") {
                TopSpacer()
                DnaTopBar(
                    onBack = { ctx.navigator.back() },
                    onShare = shareMix,
                    shareEnabled = !loading && error == null,
                )
            }

            if (loading) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(top = 96.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                return@LazyColumn
            }
            error?.let { msg ->
                item(key = "error") {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
                return@LazyColumn
            }

            item(key = "helix") {
                HelixCard(
                    seed = data.seed,
                    partner = data.partner,
                    title = mixTitle,
                    subtitle = "${data.artists.size} artistas puente, ${data.songs.size} canciones en común",
                    canChangePartner = data.partnerCount > 1,
                    onChangePartner = { partnerIndex = (partnerIndex + 1) % data.partnerCount.coerceAtLeast(1) },
                    onPlayMix = playMix,
                    onSave = saveMix,
                    hasSongs = data.songs.isNotEmpty(),
                )
            }

            item(key = "tabs") {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    listOf("Artistas", "Canciones").forEachIndexed { index, label ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(label, maxLines = 1) },
                        )
                    }
                }
            }

            if (selectedTab == 0) {
                item(key = "artists") {
                    SectionLabel("Nuevos artistas para descubrir")
                    if (data.artists.isEmpty()) {
                        EmptyLine("No encontramos artistas puente entre los dos.")
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(data.artists, key = { _, a -> a.person.id }) { index, item ->
                                BridgeArtist(
                                    number = index + 1,
                                    item = item,
                                    seedName = seedName,
                                    partnerName = partnerName,
                                    onClick = { ctx.navigator.push(Screen.Artist(item.person.id)) },
                                )
                            }
                        }
                    }
                }
            } else {
                item(key = "songs-head") {
                    SectionLabel("Canciones que conectan a ambos")
                    SideLegend(seedName = seedName, partnerName = partnerName)
                }
                if (data.songs.isEmpty()) {
                    item(key = "songs-empty") { EmptyLine("Aún no hay canciones en común.") }
                }
                itemsIndexed(data.songs, key = { _, s -> s.track.videoId.orEmpty() }) { _, item ->
                    DnaSongRow(
                        item = item,
                        onClick = { ctx.player.playRadio(item.track, source = "radio:${item.track.title}") },
                        onPlayNext = {
                            ctx.player.playNext(item.track)
                            ctx.toast("Suena a continuación")
                        },
                        onAddToQueue = {
                            ctx.player.addToQueue(item.track)
                            ctx.toast("Añadida a la cola")
                        },
                    )
                }
            }
        }
    }
}

/** La pantalla recibe una canción; el ADN parte de su artista principal. */
private suspend fun resolveArtistId(ctx: ScreenContext, videoId: String): String? {
    val track = ctx.repository.getTrack(videoId)
        ?: ctx.player.state.value.queue.items.firstOrNull { it.track.videoId == videoId }?.track
        ?: return null
    return ctx.client.findArtistId(track.artists).getOrNull()
}

// ── Combinación real de dos artistas ─────────────────────────────────────────

private fun norm(s: String) = s.trim().lowercase()

private suspend fun buildSongDna(client: YouTubeMusicClient, seedId: String, partnerIndex: Int): SongDnaResult {
    val seedPage = client.getArtist(seedId).getOrNull() ?: return SongDnaResult.Empty
    val seedArtists = relatedArtists(seedPage.shelves)
    val seedSongs = pageSongs(seedPage.songs, seedPage.shelves)

    // Candidatos a «compañero de mezcla»: los artistas más afines al principal.
    val candidates = seedArtists.filter { it.id != seedId }.distinctBy { it.id }.take(6)
    val partner = candidates.getOrNull(partnerIndex.coerceAtLeast(0) % candidates.size.coerceAtLeast(1))
    val partnerPage = partner?.let { client.getArtist(it.id).getOrNull() }
    val partnerArtists = partnerPage?.let { relatedArtists(it.shelves) }.orEmpty()
    val partnerSongs = partnerPage?.let { pageSongs(it.songs, it.shelves) }.orEmpty()

    val excluded = setOfNotNull(seedId, partner?.id)

    // Artistas recomendados: primero los que aparecen en AMBAS listas (la verdadera
    // intersección de la combinación), luego el resto, sin duplicados.
    val seedIds = seedArtists.map { it.id }.toSet()
    val partnerIds = partnerArtists.map { it.id }.toSet()
    val both = (seedIds intersect partnerIds)
    val ordered = (seedArtists + partnerArtists)
        .filter { it.id !in excluded }
        .distinctBy { it.id }
        .sortedByDescending { it.id in both }
        .take(12)
        .map { artist ->
            val side = when {
                artist.id in both -> DnaSide.BOTH
                artist.id in seedIds -> DnaSide.SEED
                else -> DnaSide.PARTNER
            }
            DnaArtist(artist, side)
        }
    val bridgeIndex = ordered.withIndex().associate { (i, a) -> norm(a.person.name) to i + 1 }

    // Canciones: intercalamos las de ambos para que la mezcla conecte los dos.
    val seedName = seedPage.name
    val seedSongIds = seedSongs.mapNotNull { it.videoId }.toSet()
    val partnerSongIds = partnerSongs.mapNotNull { it.videoId }.toSet()
    val songs = interleave(seedSongs, partnerSongs)
        .distinctBy { it.videoId }
        .take(15)
        .map { song ->
            val names = song.artists.split(",", "&", " • ").map(::norm).filter { it.isNotEmpty() }
            val fromSeed = song.videoId in seedSongIds || norm(seedName) in names
            val fromPartner = song.videoId in partnerSongIds || (partner != null && norm(partner.name) in names)
            val side = when {
                fromSeed && fromPartner -> DnaSide.BOTH
                fromPartner -> DnaSide.PARTNER
                else -> DnaSide.SEED
            }
            DnaSong(song, side, names.firstNotNullOfOrNull { bridgeIndex[it] })
        }

    return SongDnaResult(
        seed = DnaPerson(seedPage.id, seedPage.name, seedPage.thumbnail),
        partner = partner,
        partnerCount = candidates.size,
        artists = ordered,
        songs = songs,
    )
}

private fun relatedArtists(shelves: List<com.dezzy.app.core.model.MusicSection>): List<DnaPerson> =
    shelves.flatMap { it.items }
        .filter { it.type == MusicItemType.ARTIST }
        .map { DnaPerson(it.id, it.title, it.thumbnail) }

private fun pageSongs(top: List<Track>, shelves: List<com.dezzy.app.core.model.MusicSection>): List<Track> =
    (top + shelves.flatMap { it.items }.filter { it.type == MusicItemType.SONG || it.type == MusicItemType.VIDEO }.map { it.toTrack() })
        .filter { it.videoId != null }

private fun <T> interleave(a: List<T>, b: List<T>): List<T> {
    val out = ArrayList<T>(a.size + b.size)
    val max = maxOf(a.size, b.size)
    for (i in 0 until max) {
        if (i < a.size) out.add(a[i])
        if (i < b.size) out.add(b[i])
    }
    return out
}

// ── UI ───────────────────────────────────────────────────────────────────────

@Composable
private fun DnaTopBar(
    onBack: () -> Unit,
    onShare: () -> Unit,
    shareEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
        }
        Text(
            text = "Song DNA",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(start = 4.dp)
                .semantics { heading() },
        )
        Spacer(Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
            Text(
                text = "Beta",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onShare, enabled = shareEnabled) {
            Icon(Icons.Rounded.Share, contentDescription = "Compartir")
        }
    }
}

@Composable
private fun HelixCard(
    seed: DnaPerson?,
    partner: DnaPerson?,
    title: String,
    subtitle: String,
    canChangePartner: Boolean,
    onChangePartner: () -> Unit,
    onPlayMix: () -> Unit,
    onSave: () -> Unit,
    hasSongs: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.Top) {
                SeedAvatar(
                    url = seed?.thumbnail,
                    name = seed?.name ?: "—",
                    caption = "Tu artista",
                    onCaptionClick = null,
                )
                HelixCanvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                )
                SeedAvatar(
                    url = partner?.thumbnail,
                    name = partner?.name ?: "—",
                    caption = if (canChangePartner) "Cambiar" else "Afín",
                    onCaptionClick = if (canChangePartner) onChangePartner else null,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlayMix, enabled = hasSongs) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reproducir mezcla")
                }
                OutlinedButton(
                    onClick = onSave,
                    enabled = hasSongs,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar")
                }
            }
        }
    }
}

@Composable
private fun SeedAvatar(
    url: String?,
    name: String,
    caption: String,
    onCaptionClick: (() -> Unit)?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(88.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (url != null) {
                AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
            } else {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val captionModifier = if (onCaptionClick != null) {
            Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClickLabel = "Cambiar artista afín", onClick = onCaptionClick)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        } else {
            Modifier.padding(top = 2.dp)
        }
        Box(captionModifier, contentAlignment = Alignment.Center) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (onCaptionClick != null) FontWeight.Bold else FontWeight.Normal,
                color = if (onCaptionClick != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                },
            )
        }
    }
}

/** Doble hélice: dos hebras senoidales en contrafase unidas por peldaños. Gira despacio. */
@Composable
private fun HelixCanvas(modifier: Modifier = Modifier) {
    val strandA = MaterialTheme.colorScheme.primary
    val strandB = MaterialTheme.colorScheme.tertiary
    val transition = rememberInfiniteTransition(label = "helix")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "helixPhase",
    )
    Canvas(modifier.clearAndSetSemantics { contentDescription = "Doble hélice que une a los dos artistas" }) {
        val midY = size.height / 2f
        val amp = size.height * 0.42f
        val waves = 2f
        val steps = 48
        val stroke = 3.dp.toPx()
        fun y(x: Float, offset: Float) =
            midY + amp * sin((x / size.width) * waves * 2 * PI.toFloat() + phase + offset)

        // Peldaños
        val rungs = 8
        for (i in 0 until rungs) {
            val x = size.width * (i + 0.5f) / rungs
            drawLine(
                color = strandA.copy(alpha = 0.3f),
                start = Offset(x, y(x, 0f)),
                end = Offset(x, y(x, PI.toFloat())),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        // Hebras
        listOf(0f to strandA, PI.toFloat() to strandB).forEach { (offset, color) ->
            val path = Path()
            for (s in 0..steps) {
                val x = size.width * s / steps
                if (s == 0) path.moveTo(x, y(x, offset)) else path.lineTo(x, y(x, offset))
            }
            drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun BridgeArtist(
    number: Int,
    item: DnaArtist,
    seedName: String,
    partnerName: String,
    onClick: () -> Unit,
) {
    val sideLabel = when (item.side) {
        DnaSide.BOTH -> "Afín a ambos"
        DnaSide.SEED -> "Lado $seedName"
        DnaSide.PARTNER -> "Lado $partnerName"
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Puente $number, ${item.person.name}, $sideLabel"
            }
            .padding(vertical = 8.dp),
    ) {
        Box {
            AsyncImage(
                model = item.person.thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            Box(
                modifier = Modifier
                    .offset(x = (-2).dp, y = (-2).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.person.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = sideLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun sideBrush(side: DnaSide): Brush {
    val a = MaterialTheme.colorScheme.primary
    val b = MaterialTheme.colorScheme.tertiary
    return when (side) {
        DnaSide.SEED -> Brush.verticalGradient(listOf(a, a))
        DnaSide.PARTNER -> Brush.verticalGradient(listOf(b, b))
        DnaSide.BOTH -> Brush.verticalGradient(0f to a, 0.5f to a, 0.5f to b, 1f to b)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SideLegend(seedName: String, partnerName: String) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    ) {
        listOf(
            DnaSide.SEED to "Lado $seedName",
            DnaSide.PARTNER to "Lado $partnerName",
            DnaSide.BOTH to "Ambos",
        ).forEach { (side, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(sideBrush(side)),
                )
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DnaSongRow(
    item: DnaSong,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
) {
    val song = item.track
    var menuOpen by remember { mutableStateOf(false) }
    val tags = buildList {
        song.album?.takeIf { it.isNotBlank() && it != song.title }?.let(::add)
        item.bridge?.let { add("Puente $it") }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(sideBrush(item.side)),
        )
        Spacer(Modifier.width(10.dp))
        AsyncImage(
            model = song.thumbnail,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artists,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    tags.forEach { tag ->
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Más opciones",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Reproducir a continuación") }, onClick = { menuOpen = false; onPlayNext() })
                DropdownMenuItem(text = { Text("Añadir a la cola") }, onClick = { menuOpen = false; onAddToQueue() })
            }
        }
    }
}
