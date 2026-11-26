/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.database.MusicRepository
import com.dezzy.app.core.model.MusicItem
import com.dezzy.app.core.model.MusicItemType
import com.dezzy.app.core.model.MusicSection
import com.dezzy.app.core.model.Track
import com.dezzy.app.core.prefs.DezzyPreferences
import com.dezzy.app.feature.player.PlayerController
import com.dezzy.app.source.youtube.YouTubeMusicClient
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.components.ItemCard
import com.dezzy.app.ui.components.SectionHeader
import com.dezzy.app.ui.components.TrackRowPlaceholder
import com.dezzy.app.ui.navigation.Navigator
import com.dezzy.app.ui.navigation.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Lo que toda pantalla necesita para navegar, reproducir y leer la biblioteca. */
class ScreenContext(
    val navigator: Navigator,
    val player: PlayerController,
    val client: YouTubeMusicClient,
    val repository: MusicRepository,
    val prefs: DezzyPreferences,
    val snackbar: SnackbarHostState,
    val scope: CoroutineScope,
    /** videoId de lo que suena ahora (para resaltar la fila). */
    val playingId: String?,
) {
    /** Abre un elemento del catálogo: las canciones suenan como radio, lo demás abre su ficha. */
    fun open(item: MusicItem) {
        when (item.type) {
            MusicItemType.SONG, MusicItemType.VIDEO -> player.playRadio(item.toTrack(), source = "radio:${item.title}")
            MusicItemType.ALBUM -> navigator.push(Screen.Album(item.id))
            MusicItemType.PLAYLIST -> navigator.push(Screen.Playlist(item.id))
            MusicItemType.ARTIST -> navigator.push(Screen.Artist(item.id))
            MusicItemType.OTHER -> Unit
        }
    }

    /** Aviso corto abajo («Añadido a la cola», etc.). */
    fun toast(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    /** Abre la pantalla de una función que aún no está migrada. */
    fun pending(title: String, phase: String) = navigator.push(Screen.Pending(title, phase))
}

fun MusicItem.toTrack() = Track(
    videoId = id,
    title = title,
    artists = subtitle.substringBefore(" • "),
    thumbnail = thumbnail,
)

/** Carga asíncrona sencilla. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Done<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

@Composable
fun <T> rememberLoad(key: Any?, block: suspend () -> Result<T>): Load<T> {
    val result by produceState<Load<T>>(Load.Loading, key) {
        value = Load.Loading
        value = block().fold({ Load.Done(it) }, { Load.Failed(it.message ?: "Error") })
    }
    return result
}

/** Hueco del alto de la barra de estado (la app va de borde a borde). */
@Composable
fun TopSpacer() = Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

/** Título de pantalla de 28 sp con «atrás» opcional. */
@Composable
fun ScreenTitle(text: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (onBack == null) 16.dp else 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") }
        Text(
            text,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

fun LazyListScope.loadingRows(count: Int = 8) = items(count) { TrackRowPlaceholder() }

fun LazyListScope.errorRow(message: String) = item {
    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
}

fun LazyListScope.emptyRow(message: String) = item {
    Text(
        message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
    )
}

/** Carrusel horizontal de tarjetas con su encabezado. */
fun LazyListScope.carousel(section: MusicSection, ctx: ScreenContext, keyPrefix: String = "") {
    item(key = "h:$keyPrefix${section.title}") { SectionHeader(section.title, strapline = section.strapline) }
    item(key = "r:$keyPrefix${section.title}") {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(section.items, key = { it.type.name + it.id }) { ItemCard(it, onClick = { ctx.open(it) }) }
        }
    }
}

/** Fila de un álbum, artista o lista (redonda si es artista). */
@Composable
fun ItemRow(item: MusicItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(item.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            CoverImage(
                item.thumbnail,
                Modifier.size(48.dp),
                if (item.type == MusicItemType.ARTIST) CircleShape else RoundedCornerShape(6.dp),
                sizePx = 144,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Cabecera de ficha: carátula, título, subtítulo y Reproducir / Aleatorio. */
@Composable
fun CollectionHeader(
    title: String,
    subtitle: String?,
    thumbnail: String?,
    round: Boolean,
    onBack: () -> Unit,
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)? = null,
    thumbnailContent: (@Composable () -> Unit)? = null,
) {
    Column {
        TopSpacer()
        ScreenTitle("", onBack = onBack)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (thumbnailContent != null) thumbnailContent()
            else CoverImage(thumbnail, Modifier.size(200.dp), if (round) CircleShape else RoundedCornerShape(16.dp), sizePx = 720)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            if (onPlay != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPlay) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.size(6.dp))
                        Text("Reproducir")
                    }
                    if (onShuffle != null) {
                        FilledTonalButton(onClick = onShuffle) {
                            Icon(Icons.Rounded.Shuffle, null)
                            Spacer(Modifier.size(6.dp))
                            Text("Aleatorio")
                        }
                    }
                }
            }
        }
    }
}
