/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

@file:OptIn(ExperimentalEncodingApi::class)

package com.dezzy.app.ui.screens.social

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.social.P2PSocial
import com.dezzy.app.feature.social.P2PSong
import com.dezzy.app.feature.social.P2PUser
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.player.canvas.CanvasVideo
import com.dezzy.app.ui.screens.ScreenContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.Instant

internal val BannerCyan = Color(0xFF25F4EE)
internal val BannerPink = Color(0xFFFE2C55)

/** El perfil de [pubkey], que se va completando cuando llega de los relés. */
@Composable
internal fun rememberP2PUser(pubkey: String): P2PUser {
    val social: P2PSocial = koinInject()
    val profiles by social.profiles.collectAsState()
    LaunchedEffect(pubkey) { runCatching { social.loadProfiles(listOf(pubkey)) } }
    return profiles[pubkey] ?: P2PUser(pubkey)
}

/** Pide los perfiles de [pubkeys] y devuelve el mapa que se va llenando. */
@Composable
internal fun rememberP2PProfiles(pubkeys: Collection<String>): Map<String, P2PUser> {
    val social: P2PSocial = koinInject()
    val profiles by social.profiles.collectAsState()
    val key = pubkeys.sorted().joinToString(",")
    LaunchedEffect(key) { if (pubkeys.isNotEmpty()) runCatching { social.loadProfiles(pubkeys) } }
    return profiles
}

/** Degradados de portada de Dezzy: no suben nada, se pintan en cada dispositivo. */
internal enum class P2PBannerPreset(val id: String, val label: String) {
    NEON("tiktok", "Neón"),
    ELECTRIC("electric", "Eléctrico"),
    DUSK("dusk", "Atardecer"),
    WINE("wine", "Vino");

    companion object {
        fun byId(id: String?): P2PBannerPreset? = entries.firstOrNull { it.id == id }
    }
}

internal fun presetOf(banner: String?): P2PBannerPreset? =
    if (banner != null && banner.startsWith(P2PSocial.BANNER_PRESET_PREFIX)) {
        P2PBannerPreset.byId(banner.removePrefix(P2PSocial.BANNER_PRESET_PREFIX))
    } else {
        null
    }

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawP2PBannerPreset(preset: P2PBannerPreset) {
    val w = size.width
    val h = size.height
    when (preset) {
        P2PBannerPreset.NEON -> {
            drawRect(Brush.linearGradient(listOf(Color(0xFF0B0B14), Color(0xFF14101F)), Offset.Zero, Offset(w, h)))
            drawRect(
                Brush.radialGradient(listOf(BannerCyan.copy(alpha = 0.55f), Color.Transparent), Offset(w * 0.15f, h * 0.2f), w * 0.6f),
                size = Size(w, h),
            )
            drawRect(
                Brush.radialGradient(listOf(BannerPink.copy(alpha = 0.55f), Color.Transparent), Offset(w * 0.9f, h * 0.9f), w * 0.6f),
                size = Size(w, h),
            )
        }
        P2PBannerPreset.ELECTRIC ->
            drawRect(Brush.linearGradient(listOf(Color(0xFF1E3CFF), Color(0xFF7B2CFF), Color(0xFFD946EF)), Offset.Zero, Offset(w, h)))
        P2PBannerPreset.DUSK -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF2A1B5E), Color(0xFFB4457A), Color(0xFFFF9A57))))
            drawRect(
                Brush.radialGradient(listOf(Color(0xFFFFD27A).copy(alpha = 0.55f), Color.Transparent), Offset(w * 0.8f, h * 0.95f), w * 0.5f),
                size = Size(w, h),
            )
        }
        P2PBannerPreset.WINE ->
            drawRect(Brush.linearGradient(listOf(Color(0xFF3B0A1E), Color(0xFF8E1B3E), Color(0xFF2A0914)), Offset.Zero, Offset(w, h)))
    }
}

/** Portada del perfil: video en bucle, foto subida o degradado. [fade] la funde con el fondo. */
@Composable
internal fun P2PProfileBanner(
    banner: String?,
    modifier: Modifier = Modifier,
    video: String? = null,
    fade: Boolean = true,
) {
    val surface = MaterialTheme.colorScheme.surface
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        val preset = presetOf(banner)
        when {
            video != null -> {
                if (banner != null && preset == null) {
                    AsyncImage(banner, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                CanvasVideo(url = video, loopStartMs = 0, loopLengthMs = 0, playing = true, modifier = Modifier.fillMaxSize())
            }
            preset != null -> Canvas(Modifier.fillMaxSize()) { drawP2PBannerPreset(preset) }
            banner != null -> AsyncImage(banner, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> Canvas(Modifier.fillMaxSize()) { drawP2PBannerPreset(P2PBannerPreset.NEON) }
        }
        if (fade) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to surface),
                ),
            )
        }
    }
}

/** Foto de perfil lista para Coil: las `data:` se decodifican, las URL pasan tal cual. */
private fun avatarModel(picture: String): Any {
    if (picture.startsWith("data:")) {
        val comma = picture.indexOf(',')
        if (comma > 0) {
            runCatching { return Base64.decode(picture.substring(comma + 1)) }
        }
    }
    return picture
}

@Composable
internal fun P2PAvatar(
    user: P2PUser,
    size: Dp = 44.dp,
    ring: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val picture = user.picture
    val model = remember(picture) { picture?.let { avatarModel(it) } }
    val ringBrush = Brush.linearGradient(listOf(BannerCyan, BannerPink))
    val base = modifier
        .size(size)
        .then(if (ring) Modifier.border(2.dp, ringBrush, CircleShape).padding(3.dp) else Modifier)
        .clip(CircleShape)
    Box(base.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        if (model != null) {
            AsyncImage(model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                user.initials,
                fontSize = (size.value * 0.36f).sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
internal fun EncryptedChip(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(6.dp))
            Text("Cifrado de extremo a extremo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
internal fun P2PSongCard(
    song: P2PSong,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(song.thumbnail, Modifier.size(48.dp), RoundedCornerShape(10.dp), contentDescription = null)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(song.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    song.artist,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onClick != null) {
                Icon(Icons.Rounded.PlayArrow, "Reproducir", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
internal fun P2PSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
internal fun P2PEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Text(body, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.size(8.dp))
            action()
        }
    }
}

// ---------------------------------------------------------------- tiempo

private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
private val WEEKDAYS = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")

private fun two(n: Int) = n.toString().padStart(2, '0')

private fun nowSec(): Long = Clock.System.now().epochSeconds

private fun local(seconds: Long) = Instant.fromEpochSeconds(seconds).toLocalDateTime(TimeZone.currentSystemDefault())

/** «ahora», «hace 5 min», «hace 3 h», «hace 2 d» o la fecha corta. [seconds] desde 1970. */
internal fun timeAgo(seconds: Long): String {
    val diff = (nowSec() - seconds).coerceAtLeast(0)
    return when {
        diff < 60 -> "ahora"
        diff < 3600 -> "hace ${diff / 60} min"
        diff < 86400 -> "hace ${diff / 3600} h"
        diff < 7 * 86400 -> "hace ${diff / 86400} d"
        else -> local(seconds).let { "${it.dayOfMonth} ${MONTHS[it.monthNumber - 1]} ${it.year}" }
    }
}

/** Hora del mensaje (HH:mm). */
internal fun messageTime(seconds: Long): String = local(seconds).let { "${two(it.hour)}:${two(it.minute)}" }

/** Hora para la lista de chats: HH:mm hoy, «ayer», día de la semana o fecha corta. */
internal fun chatTime(seconds: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val then = local(seconds)
    val today = Instant.fromEpochSeconds(nowSec()).toLocalDateTime(tz).date
    val days = (today.toEpochDays() - then.date.toEpochDays()).toLong()
    return when {
        days <= 0 -> "${two(then.hour)}:${two(then.minute)}"
        days == 1L -> "ayer"
        days < 7 -> WEEKDAYS[then.date.dayOfWeek.ordinal]
        else -> "${then.dayOfMonth}/${then.monthNumber}/${then.year % 100}"
    }
}

// ---------------------------------------------------------------- canciones

internal fun Track.toP2PSong(): P2PSong? {
    val id = videoId ?: return null
    return P2PSong(id, title, artists, thumbnail, (durationSeconds ?: 0L).toInt())
}

/** La canción que suena ahora, para adjuntarla a un mensaje o publicación. */
internal fun ScreenContext.nowPlayingSong(): P2PSong? = player.state.value.queue.current?.track?.toP2PSong()

internal fun playP2PSong(ctx: ScreenContext, song: P2PSong) {
    val track = Track(
        videoId = song.videoId,
        title = song.title,
        artists = song.artist,
        thumbnail = song.thumbnail,
        durationSeconds = song.durationSec.takeIf { it > 0 }?.toLong(),
    )
    ctx.player.playRadio(track, "radio:${song.title}")
}

internal val P2PBorder: BorderStroke
    @Composable get() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
