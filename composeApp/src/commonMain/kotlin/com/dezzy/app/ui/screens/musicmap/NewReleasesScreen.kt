/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.musicmap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.model.MusicSection
import com.dezzy.app.ui.components.ItemCard
import com.dezzy.app.ui.screens.Load
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import com.dezzy.app.ui.screens.carousel
import com.dezzy.app.ui.screens.emptyRow
import com.dezzy.app.ui.screens.errorRow
import com.dezzy.app.ui.screens.loadingRows
import com.dezzy.app.ui.screens.rememberLoad

/** Palabras que delatan un carrusel de estrenos (YouTube Music los nombra en el idioma de la cuenta). */
private val RELEASE_WORDS = listOf("new", "album", "single", "nuev", "sencillo", "lanzamiento", "estreno")

private fun isRelease(section: MusicSection): Boolean {
    val t = section.title.lowercase()
    return section.items.isNotEmpty() && RELEASE_WORDS.any { t.contains(it) }
}

private enum class ReleaseKind(val label: String, val icon: ImageVector) {
    ALBUM("Álbumes", Icons.Rounded.Album),
    SINGLE("Singles", Icons.Rounded.MusicNote),
    EP("EPs", Icons.AutoMirrored.Rounded.QueueMusic),
}

private fun kindOf(section: MusicSection): ReleaseKind {
    val t = section.title.lowercase()
    return when {
        t.contains("single") || t.contains("sencillo") -> ReleaseKind.SINGLE
        Regex("\\bep").containsMatchIn(t) -> ReleaseKind.EP
        else -> ReleaseKind.ALBUM
    }
}

/** Tipo de cada disco según su subtítulo («EP • …», «Sencillo • …», «Álbum • …»); si no lo dice, el de su sección. */
private fun kindOfItem(item: com.dezzy.app.core.model.MusicItem, section: MusicSection): ReleaseKind {
    val first = item.subtitle.substringBefore("•").trim().lowercase()
    return when {
        first == "ep" -> ReleaseKind.EP
        first == "single" || first == "sencillo" -> ReleaseKind.SINGLE
        first == "álbum" || first == "album" -> ReleaseKind.ALBUM
        else -> kindOf(section)
    }
}

/** Tarjeta gris con tres columnas (álbumes, singles, EPs) separadas por líneas. */
@Composable
private fun ReleaseCounts(sections: List<MusicSection>) {
    val counts = ReleaseKind.entries.associateWith { k -> sections.sumOf { sec -> sec.items.count { kindOfItem(it, sec) == k } } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .height(IntrinsicSize.Min),
    ) {
        ReleaseKind.entries.forEachIndexed { i, k ->
            if (i > 0) Box(Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.12f)))
            Column(
                Modifier.weight(1f).padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(k.icon, null, Modifier.size(22.dp), tint = Color.Black) }
                Text("${counts[k] ?: 0}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(k.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Icono en círculo claro, título y píldora con el recuento a la derecha. */
@Composable
private fun ReleaseSectionHeader(section: MusicSection) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
            Icon(kindOf(section).icon, null, Modifier.size(20.dp), tint = Color.Black)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            section.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${section.items.size}",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/** Álbumes y sencillos recién salidos, tal como los muestra la pestaña Explorar de YouTube Music. */
@Composable
fun NewReleasesScreen(ctx: ScreenContext) {
    val load = rememberLoad("new_releases") { ctx.client.getNewReleases() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TopSpacer() }
        item { ScreenTitle("Nuevos lanzamientos", onBack = { ctx.navigator.back() }) }
        when (val l = load) {
            Load.Loading -> loadingRows()
            is Load.Failed -> errorRow("No se pudieron cargar los lanzamientos. Revisa la conexión.")
            is Load.Done -> {
                val all = l.value.filter { it.items.isNotEmpty() }
                val shown = all.filter(::isRelease).ifEmpty { all }
                if (shown.isEmpty()) {
                    emptyRow("Por ahora no hay lanzamientos nuevos.")
                } else {
                    item(key = "counts") { ReleaseCounts(shown) }
                    shown.forEach { section ->
                        item(key = "h:nr:${section.title}") { ReleaseSectionHeader(section) }
                        item(key = "r:nr:${section.title}") {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(section.items, key = { it.type.name + it.id }) { ItemCard(it, onClick = { ctx.open(it) }) }
                            }
                        }
                    }
                }
            }
        }
    }
}
