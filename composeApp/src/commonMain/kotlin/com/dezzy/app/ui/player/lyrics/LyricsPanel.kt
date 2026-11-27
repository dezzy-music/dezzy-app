/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.lyrics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import com.dezzy.app.feature.lyrics.LyricsManager
import com.dezzy.app.feature.lyrics.LyricsState
import com.dezzy.app.feature.lyrics.LyricsTranslateLangKey
import com.dezzy.app.feature.lyrics.SyncedLine
import com.dezzy.app.feature.lyrics.TRANSLATE_LANGUAGES
import com.dezzy.app.feature.lyrics.TranslationState
import com.dezzy.app.feature.lyrics.activeLineIndex
import com.dezzy.app.ui.player.kit.DisableBlurKey
import com.dezzy.app.ui.player.kit.PlayerConnection
import com.dezzy.app.ui.player.kit.rememberEnumPreference
import com.dezzy.app.ui.player.kit.rememberPreference
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.time.Clock

/** Letra embebida (sin barra ni chips), para los estilos que la pintan dentro de su propio diseño. */
@Composable
fun LyricsPanel(
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
    textSizeOverride: Float? = null,
) {
    LyricsContent(
        playerConnection = playerConnection,
        modifier = modifier,
        contentColor = LocalContentColor.current,
        textSizeOverride = textSizeOverride,
        showChips = false,
    )
}

private enum class LineKind { NOW, PAST, NEAR, FAR }

private class WordSpan(val text: String, val startMs: Long, val endMs: Long)

private const val SYNC_LEAD_MS = 200L
private val ChipOnText = Color(0xFF2A120A)

private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

/**
 * Palabras de la línea que suena: las de la letra si trae tiempos por palabra; si no, el avance se
 * reparte por la longitud de cada palabra dentro de la duración de la línea.
 */
private fun lineWords(line: SyncedLine, endMs: Long): List<WordSpan> {
    val given = line.words
    // Las palabras ya traen su espacio final; sólo valen si cubren la línea entera.
    if (!given.isNullOrEmpty() && given.joinToString("") { it.text }.trim() == line.text.trim()) {
        return given.map { WordSpan(it.text, it.startMs, it.endMs) }
    }
    val parts = line.text.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return emptyList()
    // Las líneas largas con un instrumental detrás no deben tardar en llenarse.
    val dur = (endMs - line.startMs).coerceAtMost(parts.size * 600L + 1000L).coerceAtLeast(600L)
    val total = parts.sumOf { it.length + 1 }
    var acc = 0
    return parts.mapIndexed { i, w ->
        val a = line.startMs + dur * acc / total
        acc += w.length + 1
        val b = line.startMs + dur * acc / total
        WordSpan(if (i < parts.lastIndex) "$w " else w, a, b)
    }
}

/**
 * Cuerpo de las letras: chips opcionales, lista con la línea activa centrada, resaltado por palabra,
 * traducción debajo de cada línea y selección de líneas para compartir.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LyricsContent(
    playerConnection: PlayerConnection,
    modifier: Modifier,
    contentColor: Color,
    textSizeOverride: Float?,
    showChips: Boolean,
    onShare: (List<String>) -> Unit = {},
) {
    val manager: LyricsManager = koinInject()
    LaunchedEffect(manager) { manager.start() }
    val state by manager.state.collectAsState()
    val translation by manager.translation.collectAsState()

    val align by rememberEnumPreference(LyricsAlignKey, LyricsAlign.CENTER)
    val anim by rememberEnumPreference(LyricsAnimKey, LyricsAnim.FOCUS)
    val scale by rememberPreference(LyricsTextScaleKey, 1f)
    val offsetMs by rememberPreference(LyricsSyncOffsetKey, 0)
    val disableBlur by rememberPreference(DisableBlurKey, false)
    val lang by rememberPreference(LyricsTranslateLangKey, "")

    val controller = playerConnection.controller
    val positionState: State<Long> = remember(controller) {
        controller.state.map { it.positionMs }.distinctUntilChanged()
    }.collectAsState(controller.state.value.positionMs)

    val found = state as? LyricsState.Found
    val result = found?.result
    val nowMeta by playerConnection.mediaMetadata.collectAsState()
    val edited = nowMeta?.let { m -> LyricsEdits.byKey[LyricsEdits.key(m.title, m.artists.joinToString { it.name })] }
    val synced: List<SyncedLine>? = if (edited != null) null else result?.synced?.takeIf { it.isNotEmpty() }
    val plainLines: List<String> = remember(result, synced, edited) {
        edited?.split(Regex("\r?\n"))
            ?: if (synced == null) result?.plain?.split(Regex("\r?\n")).orEmpty() else emptyList()
    }
    val trLines = (translation as? TranslationState.Ready)?.lines
    fun trFor(i: Int, text: String): String? =
        trLines?.getOrNull(i)?.takeIf { it.isNotBlank() && !it.equals(text, ignoreCase = true) }

    val clock: () -> Long = { positionState.value + offsetMs + SYNC_LEAD_MS }
    val active by remember(synced, offsetMs) {
        derivedStateOf { if (synced == null) -1 else activeLineIndex(synced, clock()) }
    }

    // Selección de líneas para compartir.
    val selected = remember { mutableStateListOf<Int>() }
    val selecting = LyricsSelectionState.isSelectingForShare
    LaunchedEffect(found?.videoId) {
        selected.clear()
        LyricsSelectionState.isSelectingForShare = false
    }
    DisposableEffect(Unit) { onDispose { LyricsSelectionState.isSelectingForShare = false } }

    // La línea activa se centra, salvo si el usuario acaba de tocar o desplazar la lista (3 s).
    val listState = rememberLazyListState()
    var lastUser by remember { mutableLongStateOf(0L) }
    LaunchedEffect(active, anim) {
        if (active < 0 || nowMs() - lastUser < 3000L) return@LaunchedEffect
        val target = -(listState.layoutInfo.viewportSize.height * 0.4f).toInt()
        if (anim == LyricsAnim.NONE) listState.scrollToItem(active, target)
        else listState.animateScrollToItem(active, target)
    }

    val base = textSizeOverride ?: (22f * scale)
    val color = contentColor

    Column(modifier) {
        if (showChips) {
            var menu by remember { mutableStateOf(false) }
            val langName = TRANSLATE_LANGUAGES.firstOrNull { it.first == lang }?.second
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LyricsChip(
                    if (synced != null) "Sincronizada" else "Sin sincronizar",
                    Icons.Rounded.GraphicEq, color, on = synced != null,
                )
                Box {
                    LyricsChip(
                        when (translation) {
                            is TranslationState.Loading -> "Traduciendo…"
                            is TranslationState.Failed -> "No se pudo traducir"
                            else -> langName ?: "Traducir"
                        },
                        Icons.Rounded.Translate, color, on = lang.isNotEmpty(), enabled = result != null,
                        onClick = { menu = true },
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        Text(
                            "Traducir a",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        TRANSLATE_LANGUAGES.forEach { (code, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                leadingIcon = if (lang == code) {
                                    { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }
                                } else null,
                                onClick = { menu = false; manager.setTranslateLanguage(code) },
                            )
                        }
                        if (lang.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Quitar traducción", color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; manager.setTranslateLanguage(null) },
                            )
                        }
                    }
                }
                LyricsChip(
                    if (selecting) "Compartir (${selected.size})" else "Compartir letra",
                    Icons.Rounded.IosShare, color,
                    enabled = synced != null && (!selecting || selected.isNotEmpty()),
                    onClick = {
                        val s = synced
                        if (s != null) {
                            val lines = if (selecting) {
                                selected.sorted().mapNotNull { s.getOrNull(it)?.text?.takeIf { t -> t.isNotBlank() } }
                            } else {
                                s.drop(active.coerceAtLeast(0)).take(4).map { it.text }.filter { it.isNotBlank() }
                            }
                            if (lines.isNotEmpty()) onShare(lines)
                        }
                    },
                )
                if (selecting) {
                    LyricsChip("Cancelar", Icons.Rounded.Check, color, onClick = {
                        selected.clear()
                        LyricsSelectionState.isSelectingForShare = false
                    })
                }
            }
        }

        // Aviso cuando la traducción no cambia nada: la letra ya está en ese idioma.
        val sameLanguage = (translation as? TranslationState.Ready)?.lines?.let { l -> l.isNotEmpty() && l.all { it == null } } == true
        if (translation is TranslationState.Loading) {
            Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.92f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp,
                    )
                    Text(
                        "Traduciendo letras…",
                        color = Color.Black,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        if (sameLanguage) {
            Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
                Text(
                    "La letra ya está en ese idioma",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFE5484D))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.pressed }) lastUser = nowMs()
                        }
                    }
                },
        ) {
            when (state) {
                is LyricsState.Loading -> item(key = "msg") { LyricsMessage("Buscando la letra…", color) }
                is LyricsState.NotFound -> item(key = "msg") {
                    Column {
                        LyricsMessage("No hay letra para esta canción.", color)
                        TextButton(onClick = { manager.retry() }) { Text("Reintentar", color = color) }
                    }
                }
                else -> Unit
            }
            if (synced != null) {
                itemsIndexed(synced, key = { i, _ -> "s$i" }) { i, line ->
                    val a = active
                    val kind = when {
                        i == a -> LineKind.NOW
                        i < a -> LineKind.PAST
                        abs(i - a) == 1 -> LineKind.NEAR
                        else -> LineKind.FAR
                    }
                    val words = if (kind == LineKind.NOW && line.text.isNotBlank()) {
                        remember(line) { lineWords(line, synced.getOrNull(i + 1)?.startMs ?: (line.startMs + 6000L)) }
                    } else null
                    LyricLine(
                        text = line.text,
                        translation = if (line.text.isNotBlank()) trFor(i, line.text) else null,
                        kind = kind, plain = false, anim = anim, disableBlur = disableBlur,
                        base = base, color = color, align = align.textAlign,
                        selected = selecting && i in selected,
                        words = words, clock = clock,
                        onClick = {
                            if (selecting) {
                                if (i in selected) selected.remove(i) else selected.add(i)
                            } else {
                                playerConnection.player.seekTo(line.startMs)
                            }
                        },
                        onLongClick = if (showChips) {
                            {
                                if (line.text.isNotBlank()) {
                                    LyricsSelectionState.isSelectingForShare = true
                                    LyricsSelectionState.pendingShareSong = playerConnection.mediaMetadata.value?.track
                                    if (i !in selected) selected.add(i)
                                }
                            }
                        } else null,
                    )
                }
            }
            if (synced == null && result != null) {
                itemsIndexed(plainLines, key = { i, _ -> "p$i" }) { i, text ->
                    LyricLine(
                        text = text, translation = if (text.isNotBlank()) trFor(i, text) else null,
                        kind = LineKind.FAR, plain = true, anim = anim, disableBlur = disableBlur,
                        base = base, color = color, align = align.textAlign,
                        selected = false, words = null, clock = clock, onClick = null, onLongClick = null,
                    )
                }
            }
            if (result != null) {
                item(key = "source") {
                    Text(
                        "Letra: ${result.source}",
                        fontSize = 12.sp,
                        color = color.copy(alpha = 0.45f),
                        textAlign = align.textAlign,
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    )
                }
                // Hueco final para que las últimas líneas también puedan quedar centradas.
                item(key = "end") { Box(Modifier.height(240.dp)) }
            }
        }
    }
}

@Composable
private fun LyricsMessage(text: String, color: Color) {
    Text(text, fontSize = 18.sp, color = color.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun LyricsChip(
    text: String,
    icon: ImageVector,
    color: Color,
    on: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val fg = if (on) ChipOnText else color
    Row(
        Modifier
            .alpha(if (enabled) 1f else 0.45f)
            .height(30.dp)
            .clip(CircleShape)
            .background(if (on) color else color.copy(alpha = 0.12f))
            .then(if (onClick != null && enabled) Modifier.combinedClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = fg)
        Text(text, fontSize = 13.sp, color = fg, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LyricLine(
    text: String,
    translation: String?,
    kind: LineKind,
    plain: Boolean,
    anim: LyricsAnim,
    disableBlur: Boolean,
    base: Float,
    color: Color,
    align: TextAlign,
    selected: Boolean,
    words: List<WordSpan>?,
    clock: () -> Long,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
) {
    val isGap = !plain && text.isBlank()
    val animate = anim != LyricsAnim.NONE
    val dim = if (kind == LineKind.NEAR) 0.5f else 0.32f
    val targetAlpha = when {
        plain -> 0.8f
        kind == LineKind.NOW && !isGap -> 1f
        anim == LyricsAnim.FADE -> dim * 0.6f
        anim == LyricsAnim.FOCUS && kind == LineKind.PAST -> 0.22f
        anim == LyricsAnim.SLIDE && kind == LineKind.PAST -> 0.5f
        else -> dim
    }
    val targetSize = when {
        isGap -> base * 0.8f
        plain -> base * 0.9f
        kind == LineKind.NOW -> base * 1.3f
        anim == LyricsAnim.FOCUS && kind == LineKind.PAST -> base * 0.85f
        else -> base
    }
    val a by animateFloatAsState(targetAlpha, tween(if (animate) 300 else 0), label = "lyAlpha")
    val size by animateFloatAsState(targetSize, tween(if (animate) 250 else 0), label = "lySize")
    val shift by animateDpAsState(
        if (kind == LineKind.NOW && anim == LyricsAnim.SLIDE && !isGap && !plain) 10.dp else 0.dp,
        label = "lyShift",
    )
    val blurred = !plain && anim == LyricsAnim.FOCUS && kind == LineKind.PAST && !disableBlur
    val glow = !plain && anim == LyricsAnim.FOCUS && kind == LineKind.NOW && !isGap
    val interaction = remember { MutableInteractionSource() }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = if (plain) 6.dp else 16.dp)
            .offset(x = shift)
            .then(if (blurred) Modifier.blur(1.dp) else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .then(if (selected) Modifier.background(color.copy(alpha = 0.15f)) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else Modifier,
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        val font = MaterialTheme.typography.bodyLarge
        val style = if (glow) font.copy(shadow = Shadow(Color(0xFFFFB77A).copy(alpha = 0.3f), blurRadius = 24f)) else font
        if (words != null && words.isNotEmpty()) {
            val pos = clock()
            val annotated = buildAnnotatedString {
                words.forEach { w ->
                    val p = if (w.endMs <= w.startMs) {
                        if (pos >= w.startMs) 1f else 0f
                    } else {
                        ((pos - w.startMs).toFloat() / (w.endMs - w.startMs)).coerceIn(0f, 1f)
                    }
                    // Palabra que suena en cian; las ya cantadas, plenas; las que faltan, tenues.
                    val wc = when {
                        p <= 0f -> color.copy(alpha = 0.38f)
                        else -> color
                    }
                    withStyle(SpanStyle(color = wc)) { append(w.text) }
                }
            }
            Text(
                annotated, color = color, fontSize = size.sp, lineHeight = (size * 1.25f).sp,
                fontWeight = FontWeight.Bold, textAlign = align, style = style, modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                if (isGap) "♪" else text.ifEmpty { " " },
                color = color.copy(alpha = a), fontSize = size.sp, lineHeight = (size * 1.25f).sp,
                fontWeight = FontWeight.Bold, textAlign = align, style = style, modifier = Modifier.fillMaxWidth(),
            )
        }
        if (translation != null) {
            Text(
                translation,
                color = color.copy(alpha = if (kind == LineKind.NOW) 0.85f else 0.55f),
                fontSize = (base * 0.65f).sp, lineHeight = (base * 0.65f * 1.35f).sp,
                fontWeight = FontWeight.Medium, textAlign = align,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

/** Color de la palabra que se está cantando (resaltado por palabra). */
private val LyricsWordHighlight = Color(0xFF4DD9F0)
