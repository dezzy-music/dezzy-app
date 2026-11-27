/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.personalize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dezzy.app.feature.personalize.PinterestResult
import com.dezzy.app.feature.personalize.PinterestWallpaper
import com.dezzy.app.ui.components.glass.DezzyButton as Button
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * «Desde un pin de Pinterest»: pegas pin.it/… o pinterest.com/pin/… y Dezzy baja la imagen
 * o el vídeo del pin y lo pone de fondo. La descarga y todas las comprobaciones están en
 * [PinterestWallpaper]; aquí sólo va el campo, el estado de carga y los mensajes.
 *
 * Si el pin es vertical, va al fondo vertical. Si es horizontal, no encaja con la app en vertical:
 * se guarda sólo como fondo horizontal (al girar el móvil), se deja libre el vertical y se avisa.
 */
@Composable
fun PinterestWallpaperField(
    onImage: (uri: String, landscape: Boolean) -> Unit,
    onVideo: (uri: String, landscape: Boolean) -> Unit,
) {
    val pinterest: PinterestWallpaper = koinInject()
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var done by remember { mutableStateOf("") }
    var warn by remember { mutableStateOf("") }
    val isPin = remember(url) { pinterest.isPinterestUrl(url) }

    fun use() {
        if (busy || url.isBlank()) return
        focus.clearFocus()
        busy = true
        error = ""
        done = ""
        warn = ""
        scope.launch {
            when (val r = pinterest.fetch(url)) {
                is PinterestResult.Ok -> {
                    if (r.isVideo) onVideo(r.uri, r.isLandscape) else onImage(r.uri, r.isLandscape)
                    val what = if (r.isVideo) "el vídeo" else "la imagen"
                    if (r.isLandscape) {
                        warn = "Este pin es horizontal y no es compatible con la app en vertical. " +
                            "Lo he puesto sólo como fondo horizontal (se ve al girar el móvil); el fondo vertical queda libre."
                    } else {
                        done = "Listo: $what del pin es tu fondo en vertical."
                    }
                    url = ""
                }
                is PinterestResult.Error -> error = r.message
            }
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Desde un pin de Pinterest", style = MaterialTheme.typography.titleMedium)
        Text(
            "Pega el enlace de un pin con imagen o vídeo. Si es vertical, será tu fondo; si es horizontal, sólo se usará al girar el móvil.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    error = ""
                    done = ""
                    warn = ""
                },
                enabled = !busy,
                singleLine = true,
                placeholder = { Text("pin.it/… o pinterest.com/pin/…") },
                leadingIcon = if (isPin) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Enlace de Pinterest",
                            tint = Color(0xFFE60023),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else {
                    null
                },
                isError = error.isNotEmpty(),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { use() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { use() },
                enabled = !busy && url.isNotBlank(),
                modifier = Modifier.semantics { if (busy) contentDescription = "Bajando del pin" },
            ) {
                if (busy) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isPin) "Bajando del pin…" else "Bajando…")
                } else {
                    Text("Usar")
                }
            }
        }
        if (error.isNotEmpty()) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (warn.isNotEmpty()) {
            Text(
                warn,
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (done.isNotEmpty()) {
            Text(
                done,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
