/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.dezzy.app.core.model.Track
import com.dezzy.app.ui.player.kit.loadArtPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Fondo de la tarjeta: color sólido o degradado vertical color → negro. */
private data class BgChoice(val color: Color, val gradient: Boolean)

private val SheetBg = Color(0xFF1E1E1E)
private val InvalidFileChars = Regex("[\\\\/:*?\"<>|]")

/**
 * Hoja inferior para compartir letra: tarjeta con la carátula, el título, el artista y las [lines]
 * elegidas, sobre un color de la carátula (sólido o degradado). Se comparte o se guarda como PNG.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsShareSheet(track: Track, lines: List<String>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // Colores de la carátula (+ neutros de respaldo), cada uno en sólido y en degradado color→negro.
    var swatches by remember(track.thumbnail) { mutableStateOf(listOf<Color>()) }
    var selected by remember(track.thumbnail) { mutableStateOf<BgChoice?>(null) }
    LaunchedEffect(track.thumbnail) {
        val palette = loadArtPalette(track.thumbnail, 12)
        swatches = buildList {
            palette?.darkVibrantSwatch?.let { add(Color(it.rgb or 0xFF000000.toInt())) }
            palette?.dominantSwatch?.let { add(Color(it.rgb or 0xFF000000.toInt())) }
            palette?.vibrantSwatch?.let { add(Color(it.rgb or 0xFF000000.toInt())) }
            palette?.darkMutedSwatch?.let { add(Color(it.rgb or 0xFF000000.toInt())) }
            palette?.mutedSwatch?.let { add(Color(it.rgb or 0xFF000000.toInt())) }
            palette?.lightVibrantSwatch?.let { add(Color(it.rgb or 0xFF000000.toInt())) }
            add(Color(0xFF2B2B2B)); add(Color(0xFF121212)); add(Color(0xFFF2F2F2))
        }.distinct()
    }
    val choices = remember(swatches) { swatches.flatMap { listOf(BgChoice(it, false), BgChoice(it, true)) } }
    val choice = selected ?: choices.firstOrNull() ?: BgChoice(Color(0xFF2B2B2B), false)
    val textColor = if (choice.gradient || choice.color.luminance() <= 0.5f) Color.White else Color.Black
    val secondaryColor = textColor.copy(alpha = 0.7f)

    val fileName = remember(track) {
        "${track.title} - letra.png".replace(InvalidFileChars, "").ifBlank { "letra.png" }
    }
    val shareText = remember(track) { "${track.title} · ${track.artists}" }

    /** Captura la tarjeta tal como se ve y se la pasa a [action] ya codificada en PNG. */
    fun capture(action: suspend (ByteArray) -> Unit) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                val bitmap = layer.toImageBitmap()
                val png = withContext(Dispatchers.Default) { encodePng(bitmap) }
                if (png.isEmpty()) message = "No se pudo crear la imagen" else action(png)
            } catch (e: Exception) {
                message = "No se pudo crear la imagen"
            } finally {
                busy = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetBg,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Tarjeta de LETRA: carátula pequeña + título/artista + letra elegida + marca.
            val cardBg = if (choice.gradient) {
                Modifier.background(Brush.verticalGradient(listOf(choice.color, Color.Black)))
            } else {
                Modifier.background(choice.color)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 20.dp)
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(16.dp))
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                    .then(cardBg),
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = track.thumbnail,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = track.title,
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = track.artists,
                                color = secondaryColor,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        lines.forEach { line ->
                            Text(
                                text = line,
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                lineHeight = 27.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(
                            text = "Dezzy",
                            color = textColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            // Selector de color: sólido y degradado color→negro por cada color de la carátula.
            if (choices.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    choices.forEach { c ->
                        val isSel = c == choice
                        val swatchBg = if (c.gradient) {
                            Modifier.background(Brush.verticalGradient(listOf(c.color, Color.Black)))
                        } else {
                            Modifier.background(c.color)
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .then(swatchBg)
                                .border(
                                    width = if (isSel) 3.dp else 1.dp,
                                    color = if (isSel) Color.White else Color.White.copy(alpha = 0.3f),
                                    shape = CircleShape,
                                )
                                .clickable { selected = c },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Toca un color para personalizar la tarjeta",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        capture { png ->
                            shareImage(png, fileName, shareText)
                            onDismiss()
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                ) {
                    Text("Compartir", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        capture { png ->
                            message = if (saveImage(png, fileName)) "Imagen guardada en la galería" else "No se pudo guardar la imagen"
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Text("Guardar imagen", fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = message ?: " ",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
