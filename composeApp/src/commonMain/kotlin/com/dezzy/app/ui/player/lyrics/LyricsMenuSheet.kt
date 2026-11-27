/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.feature.lyrics.LyricsManager
import com.dezzy.app.feature.lyrics.LyricsState
import com.dezzy.app.feature.lyrics.TRANSLATE_LANGUAGES
import io.ktor.http.encodeURLParameter

/** Letras editadas a mano en esta sesión (clave: título|artista → texto). */
object LyricsEdits {
    val byKey = mutableStateMapOf<String, String>()
    fun key(title: String, artist: String) = "$title|$artist"
}

/** Menú ⋯ de la pantalla de letras: rejilla de acciones como en la app actual. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsMenuSheet(title: String, artist: String, manager: LyricsManager, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uri = LocalUriHandler.current
    val state by manager.state.collectAsState()
    var languages by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(
                title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                artist, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            if (!languages) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MenuTile("Editar", Icons.Rounded.Edit, Modifier.weight(1f)) { editing = true }
                    MenuTile("Volver a buscar", Icons.Rounded.Refresh, Modifier.weight(1f)) {
                        manager.retry(); onDismiss()
                    }
                    MenuTile("Traducir", Icons.Rounded.Translate, Modifier.weight(1f)) { languages = true }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MenuTile("Traducir por IA", Icons.Rounded.AutoAwesome, Modifier.weight(1f)) {
                        manager.setTranslateLanguage("es"); onDismiss()
                    }
                    MenuTile("Buscar", Icons.Rounded.Search, Modifier.weight(1f)) {
                        runCatching {
                            uri.openUri("https://www.google.com/search?q=" + "letra $title $artist".encodeURLParameter())
                        }
                        onDismiss()
                    }
                    Spacer(Modifier.weight(1f))
                }
            } else {
                Text("Traducir a", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                    items(TRANSLATE_LANGUAGES) { (code, name) ->
                        Text(
                            name,
                            Modifier.fillMaxWidth()
                                .clickable { manager.setTranslateLanguage(code); onDismiss() }
                                .padding(vertical = 12.dp),
                        )
                    }
                    item {
                        Text(
                            "Quitar traducción",
                            Modifier.fillMaxWidth()
                                .clickable { manager.setTranslateLanguage(null); onDismiss() }
                                .padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (editing) {
        val found = (state as? LyricsState.Found)?.result
        val key = LyricsEdits.key(title, artist)
        val initial = LyricsEdits.byKey[key]
            ?: found?.synced?.takeIf { it.isNotEmpty() }?.joinToString("\n") { it.text }
            ?: found?.plain.orEmpty()
        var text by remember { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Editar letra") },
            text = { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().height(320.dp)) },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isBlank()) LyricsEdits.byKey.remove(key) else LyricsEdits.byKey[key] = text
                    editing = false
                    onDismiss()
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun MenuTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(92.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, maxLines = 2, lineHeight = 14.sp,
        )
    }
}
