/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.musicmap

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.model.MusicSection
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
                    shown.forEach { carousel(it, ctx, keyPrefix = "nr:") }
                }
            }
        }
    }
}
