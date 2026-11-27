/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.personalize

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.personalize.SongMod
import com.dezzy.app.feature.personalize.SongMods
import com.dezzy.app.feature.personalize.modKey
import com.dezzy.app.feature.personalize.wallpaperFiles
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.components.glass.DezzyOutlinedButton as OutlinedButton
import com.dezzy.app.ui.components.glass.DezzyTextButton as TextButton
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Editor de MOD de una canción: título personalizado + carátula, con opción de revertir al original.
 * No destructivo: el mod se guarda aparte ([SongMods]) y el original no se toca.
 *
 * [track] debe ser la canción ORIGINAL (sin el mod aplicado). Tras guardar o revertir se llama a
 * [onChanged]; los reproductores y listas muestran los cambios pasando sus pistas por
 * `track.withMods(mods)` (con `mods` = `SongMods.all`).
 */
@Composable
fun SongModDialog(
    track: Track,
    onDismiss: () -> Unit,
    onChanged: () -> Unit = {},
) {
    val mods: SongMods = koinInject()
    val files = remember { wallpaperFiles() }
    val scope = rememberCoroutineScope()

    var title by remember(track.modKey()) { mutableStateOf(track.title) }
    var cover by remember(track.modKey()) { mutableStateOf<String?>(null) } // null = la del original
    var isModded by remember(track.modKey()) { mutableStateOf(false) }
    LaunchedEffect(track.modKey()) {
        val m = mods.get(track)
        title = m?.title ?: track.title
        cover = m?.cover
        isModded = m != null
    }

    val pickCover = rememberMediaPicker { uri, mime ->
        scope.launch {
            val imported = files.importPicked(uri, extensionForMime(mime, isVideo = false))
            if (imported != null) cover = imported
        }
    }
    val changeCover = { pickCover(MediaKind.IMAGE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Palette, contentDescription = null) },
        title = { Text("Personalizar canción") },
        text = {
            Column {
                // Carátula editable
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = changeCover),
                    ) {
                        CoverImage(cover ?: track.thumbnail, Modifier.fillMaxSize(), RoundedCornerShape(8.dp), sizePx = 216)
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.BottomEnd,
                        ) {
                            Icon(
                                Icons.Rounded.Image,
                                contentDescription = "Cambiar carátula",
                                tint = Color.White,
                                modifier = Modifier.padding(4.dp).size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Carátula",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        OutlinedButton(onClick = changeCover) { Text("Cambiar") }
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (isModded) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            scope.launch {
                                mods.reset(track)
                                onChanged()
                                onDismiss()
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Revertir al original")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        val newTitle = title.trim().takeIf { it.isNotEmpty() && it != track.title }
                        mods.save(track, SongMod(title = newTitle, cover = cover))
                        onChanged()
                        onDismiss()
                    }
                },
            ) { Text("Guardar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
