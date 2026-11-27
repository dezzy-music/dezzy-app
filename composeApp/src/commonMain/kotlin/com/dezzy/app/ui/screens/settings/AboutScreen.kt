/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

// «Acerca de» brutalista, boceto B «Tablero de bloques»: rejilla de 6 columnas,
// bordes de 3 dp, sombras duras desplazadas y sólo iconos vectoriales de trazo recto.

package com.dezzy.app.ui.screens.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import com.dezzy.app.core.AppVersionName
import com.dezzy.app.ui.screens.ScreenContext

// ── Paleta y tipos del tablero ──────────────────────────────────────────────

private val Ink = Color(0xFF0D0D0D)
private val Acid = Color(0xFFE4FF3A)
private val Sky = Color(0xFF9FD3FF)
private val Blush = Color(0xFFE9C9CD)
private val Paper = Color(0xFFF3EFE6)

private val BlockGap = 10.dp
private val EdgeWidth = 3.dp
private val ShadowOffset = 4.dp

private val DisplayFont = FontFamily.Default
private val MonoFont = FontFamily.Monospace

private fun mono(size: TextUnit, weight: FontWeight = FontWeight.Bold) = TextStyle(
    fontFamily = MonoFont,
    fontWeight = weight,
    fontSize = size,
    letterSpacing = 0.08.em,
)

private class Contributor(val avatarUrl: String, val name: String, val role: String, val profileUrl: String)

/** Contribuidores reconocidos en «Acerca de» (además del autor). */
private val DezzyContributors = listOf(
    Contributor(
        avatarUrl = "https://github.com/dani36652.png",
        name = "dani36652",
        role = "Contribuidor",
        profileUrl = "https://github.com/dani36652",
    ),
)

private const val UrlGitHub = "https://github.com/dezzy-music"
private const val AvatarLead = "https://github.com/dezzy-music.png"
private const val UrlInstagram = "https://www.instagram.com/dezzy_9n9_/?utm_source=ig_web_button_share_sheet"
private const val UrlFacebook = "https://www.facebook.com/share/18nog3EfkG/"
private const val UrlWebsite = "https://dezzy.space"

// ── Pantalla ────────────────────────────────────────────────────────────────

@Composable
internal fun AboutScreen(ctx: ScreenContext) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val edge = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface

    val versionLabel = "Dezzy $AppVersionName"
    val copyVersion = {
        clipboard.setText(AnnotatedString(versionLabel))
        ctx.toast("Versión copiada")
    }
    val (checkingUpdates, checkUpdates) = rememberUpdateCheck(ctx)

    SettingsPage(title = "Acerca de Dezzy", onBack = { ctx.navigator.back() }) {
        item(key = "about:board") {
            CompositionLocalProvider(LocalBoardEdge provides edge) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(BlockGap),
                ) {
                    // ── Cabecera: sello · copiar versión ──────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(BlockGap),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("ACERCA DE", style = mono(13.sp), color = edge)
                            Text("05 / AJUSTES", style = mono(10.sp, FontWeight.Normal), color = edge.copy(alpha = 0.6f))
                        }
                        BrutalBlock(
                            fill = surface,
                            content = edge,
                            padding = PaddingValues(10.dp),
                            onClick = copyVersion,
                        ) {
                            BoardIcon(Icons.Rounded.ContentCopy, "Copiar versión", 22.dp)
                        }
                    }

                    // ── Bloque título ─────────────────────────────────────────
                    BrutalBlock(
                        modifier = Modifier.fillMaxWidth(),
                        fill = MaterialTheme.colorScheme.primary,
                        content = MaterialTheme.colorScheme.onPrimary,
                        padding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 48.dp),
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    "DEZZY",
                                    fontFamily = DisplayFont,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 64.sp,
                                    lineHeight = 60.sp,
                                    letterSpacing = (-2).sp,
                                )
                                Text(
                                    "*",
                                    fontFamily = DisplayFont,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 48.sp,
                                    lineHeight = 48.sp,
                                    color = Acid,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("* OBSESSION WAS ALWAYS THE KEY.", style = mono(11.sp))
                        }
                        // Pegatina girada en la esquina
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 6.dp, y = 34.dp)
                                .rotate(-6f)
                                .background(Acid)
                                .border(EdgeWidth, Ink)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text("© 2026 DEZZY", style = mono(10.sp), color = Ink)
                        }
                    }

                    // ── Versión · build ───────────────────────────────────────
                    BoardRow {
                        BrutalBlock(
                            modifier = span(3),
                            fill = Acid,
                            content = Ink,
                            onClick = checkUpdates,
                            onLongClick = copyVersion,
                        ) {
                            Column {
                                BlockLabel("Versión")
                                BigValue(AppVersionName)
                            }
                        }
                        BrutalBlock(
                            modifier = span(3),
                            fill = Ink,
                            content = Color.White,
                        ) {
                            Column {
                                BlockLabel("Licencia")
                                BigValue("PolyForm Strict")
                                Spacer(Modifier.height(8.dp))
                                Chip(text = "1.0.0", fill = Ink, content = Color.White, edgeColor = Color.White)
                            }
                        }
                    }

                    // ── Autor · origen ────────────────────────────────────────
                    BoardRow {
                        BrutalBlock(
                            modifier = span(4),
                            fill = Blush,
                            content = Ink,
                            onClick = { uriHandler.openUri(UrlGitHub) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Face("JH", fill = Ink, content = Blush, avatarUrl = AvatarLead)
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Jhonny Hernández",
                                        fontFamily = DisplayFont,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 17.sp,
                                        lineHeight = 19.sp,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text("LEAD DEVELOPER", style = mono(10.sp))
                                }
                            }
                        }
                        BrutalBlock(
                            modifier = span(2),
                            fill = Sky,
                            content = Ink,
                        ) {
                            Column {
                                BlockLabel("DESDE")
                                Text(
                                    "20/30",
                                    fontFamily = DisplayFont,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 22.sp,
                                )
                                Text("NY", style = mono(11.sp))
                            }
                        }
                    }

                    // ── Contribuidores ────────────────────────────────────────
                    DezzyContributors.forEachIndexed { index, contributor ->
                        BrutalBlock(
                            modifier = Modifier.fillMaxWidth(),
                            fill = if (index % 2 == 0) Acid else Sky,
                            content = Ink,
                            onClick = { uriHandler.openUri(contributor.profileUrl) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Face(contributor.name.take(1).uppercase(), fill = Ink, content = Acid, avatarUrl = contributor.avatarUrl)
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        contributor.name,
                                        fontFamily = DisplayFont,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text("${contributor.role.uppercase()} · NUEVO", style = mono(10.sp))
                                }
                                BoardIcon(Icons.Rounded.ArrowOutward, null, 24.dp)
                            }
                        }
                    }

                    // ── Redes ─────────────────────────────────────────────────
                    BoardRow {
                        LinkSquare("GH", "GITHUB", surface, edge, span(2)) { uriHandler.openUri(UrlGitHub) }
                        LinkSquare("IG", "INSTAGRAM", Ink, Color.White, span(2)) { uriHandler.openUri(UrlInstagram) }
                        LinkSquare("FB", "FACEBOOK", Sky, Ink, span(2)) { uriHandler.openUri(UrlFacebook) }
                    }

                    // ── Web ───────────────────────────────────────────────────
                    BrutalBlock(
                        modifier = Modifier.fillMaxWidth(),
                        fill = surface,
                        content = edge,
                        onClick = { uriHandler.openUri(UrlWebsite) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BoardIcon(Icons.Rounded.Code, null, 24.dp)
                            Spacer(Modifier.size(12.dp))
                            Text(
                                "dezzy.space",
                                modifier = Modifier.weight(1f),
                                fontFamily = DisplayFont,
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                            )
                            BoardIcon(Icons.Rounded.ArrowOutward, null, 22.dp)
                        }
                    }

                    // ── Cita · cruz ───────────────────────────────────────────
                    BoardRow {
                        BrutalBlock(
                            modifier = span(4),
                            fill = Ink,
                            content = Color.White,
                        ) {
                            Column {
                                Text(
                                    "JESUS CHRIST",
                                    fontFamily = DisplayFont,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    lineHeight = 22.sp,
                                )
                                Text("loves you", style = mono(12.sp, FontWeight.Normal), color = Acid)
                            }
                        }
                        BrutalBlock(
                            modifier = span(2),
                            fill = Acid,
                            content = Ink,
                            contentAlignment = Alignment.Center,
                        ) {
                            BoardIcon(Icons.Rounded.Add, null, 40.dp)
                        }
                    }

                    // ── Licencia ──────────────────────────────────────────────
                    BrutalBlock(
                        modifier = Modifier.fillMaxWidth(),
                        fill = Paper,
                        content = Ink,
                        onClick = { uriHandler.openUri(UrlGitHub) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BoardIcon(Icons.Rounded.Description, null, 26.dp)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                BlockLabel("Licencia")
                                Text(
                                    "PolyForm Strict 1.0.0",
                                    fontFamily = DisplayFont,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                )
                                Text("© 2026 Dezzy", style = mono(10.sp, FontWeight.Normal))
                            }
                        }
                    }

                    Text(
                        "HECHO CON OBSESIÓN · DEZZY · NEW YORK · ESTD 2030",
                        style = mono(9.sp, FontWeight.Normal),
                        color = edge.copy(alpha = 0.55f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        item(key = "about:updates") {
            SettingsGroup {
                SettingsRow(
                    title = "Buscar actualizaciones",
                    subtitle = if (checkingUpdates) "Buscando…" else "Versión instalada: ${com.dezzy.app.core.AppVersionName}",
                    icon = Icons.Rounded.ArrowOutward,
                    onClick = checkUpdates,
                )
            }
        }
    }
}

// ── Piezas del tablero ──────────────────────────────────────────────────────

/** Color de bordes y sombras: onSurface, así el tablero se adapta al modo oscuro. */
private val LocalBoardEdge = staticCompositionLocalOf { Ink }

/** Fila de la rejilla de 6 columnas: todos los bloques comparten altura. */
@Composable
private fun BoardRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(BlockGap),
        content = content,
    )
}

/** Ocupa [columns] de las 6 columnas de la fila y estira el bloque a su altura. */
private fun RowScope.span(columns: Int): Modifier =
    Modifier.weight(columns.toFloat()).fillMaxHeight()

/**
 * Bloque brutalista: relleno plano, borde de 3 dp y sombra dura desplazada 4 dp. Al pulsarlo
 * el bloque «baja» sobre su sombra.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrutalBlock(
    modifier: Modifier = Modifier,
    fill: Color,
    content: Color,
    padding: PaddingValues = PaddingValues(14.dp),
    contentAlignment: Alignment = Alignment.TopStart,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    body: @Composable BoxScope.() -> Unit,
) {
    val edge = LocalBoardEdge.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shift by animateDpAsState(
        targetValue = if (pressed && onClick != null) ShadowOffset else 0.dp,
        animationSpec = tween(70),
        label = "brutalPress",
    )
    Box(
        modifier = modifier
            .padding(end = ShadowOffset, bottom = ShadowOffset)
            .drawBehind {
                val s = ShadowOffset.toPx()
                drawRect(edge, topLeft = Offset(s, s), size = size)
            }
            .offset(x = shift, y = shift)
            .background(fill)
            .border(EdgeWidth, edge)
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(padding),
        contentAlignment = contentAlignment,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            body()
        }
    }
}

@Composable
private fun BoardIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String?, size: Dp) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = LocalContentColor.current,
        modifier = Modifier.size(size),
    )
}

@Composable
private fun BlockLabel(text: String) {
    Text(text, style = mono(10.sp), modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun BigValue(text: String) {
    Text(
        text,
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Black,
        fontSize = 26.sp,
        lineHeight = 28.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Etiqueta rectangular con borde (sin esquinas redondeadas). */
@Composable
private fun Chip(text: String, fill: Color, content: Color, edgeColor: Color = Ink) {
    Row(
        modifier = Modifier
            .background(fill)
            .border(2.dp, edgeColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = mono(9.sp), color = content, maxLines = 1)
    }
}

/**
 * «Cara» cuadrada con la foto de GitHub del creador. Las iniciales quedan debajo: se ven
 * mientras carga la foto o si no hay conexión.
 */
@Composable
private fun Face(initials: String, fill: Color, content: Color, avatarUrl: String? = null) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(fill)
            .border(EdgeWidth, Ink),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            color = content,
        )
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = initials,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(EdgeWidth),
            )
        }
    }
}

@Composable
private fun LinkSquare(
    short: String,
    label: String,
    fill: Color,
    content: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    BrutalBlock(
        modifier = modifier.heightIn(min = 92.dp),
        fill = fill,
        content = content,
        padding = PaddingValues(10.dp),
        onClick = onClick,
    ) {
        BoardIcon(Icons.Rounded.ArrowOutward, null, 18.dp)
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(
                short,
                fontFamily = DisplayFont,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                lineHeight = 26.sp,
            )
            Text(label, style = mono(8.sp), maxLines = 1, overflow = TextOverflow.Clip)
        }
    }
}

/** Devuelve (buscando, acción): comprueba la última release y abre su página si es más nueva. */
@Composable
private fun rememberUpdateCheck(ctx: ScreenContext): Pair<Boolean, () -> Unit> {
    val http: io.ktor.client.HttpClient = org.koin.compose.koinInject()
    val uri = LocalUriHandler.current
    var checking by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    return checking to {
        if (!checking) {
            checking = true
            ctx.scope.launch {
                runCatching { com.dezzy.app.core.fetchLatestRelease(http) }
                    .onSuccess { latest ->
                        if (com.dezzy.app.core.isNewerVersion(latest.version, AppVersionName)) {
                            ctx.toast("Hay una versión nueva: ${latest.version}")
                            runCatching { uri.openUri(latest.url) }
                        } else {
                            ctx.toast("Ya tienes la última versión")
                        }
                    }
                    .onFailure { ctx.toast("No se pudo comprobar si hay actualizaciones") }
                checking = false
            }
        }
    }
}
