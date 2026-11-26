/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.dezzy.app.ui.screens.social

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.feature.social.NearbyStatus
import com.dezzy.app.feature.social.P2PNearby
import com.dezzy.app.feature.social.P2PNearbyPerson
import com.dezzy.app.feature.social.P2PSocial
import com.dezzy.app.ui.components.glass.DezzyButton
import com.dezzy.app.ui.components.glass.DezzyOutlinedButton
import com.dezzy.app.ui.components.glass.DezzySwitch
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.screens.ScreenContext
import org.koin.compose.koinInject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Pestaña «Cerca»: radar Bluetooth con la gente de Dezzy a unos metros. */
@Composable
internal fun NearbyTab(ctx: ScreenContext) {
    val nearby: P2PNearby = koinInject()
    val social: P2PSocial = koinInject()
    val status by nearby.status.collectAsState()
    val people by nearby.people.collectAsState()
    val visible by social.nearbyVisible.collectAsState()
    var selected by remember { mutableStateOf<P2PNearbyPerson?>(null) }
    var denied by remember { mutableStateOf(false) }
    val profiles = rememberP2PProfiles(people.map { it.pubkey })

    val request = rememberPermissionRequester { granted ->
        denied = !granted
        if (granted) nearby.start()
    }
    LaunchedEffect(Unit) {
        if (nearby.hasPermissions()) nearby.start() else request(nearby.requiredPermissions())
    }
    DisposableEffect(Unit) { onDispose { nearby.stop() } }

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
        item {
            Radar(people, Modifier.fillMaxWidth().padding(24.dp).aspectRatio(1f), scanning = status == NearbyStatus.SCANNING)
        }
        item {
            when (status) {
                NearbyStatus.UNSUPPORTED -> P2PEmptyState(
                    Icons.Rounded.Radar,
                    "No disponible aquí",
                    "El radar usa Bluetooth, así que sólo funciona en el móvil.",
                )
                NearbyStatus.BLUETOOTH_OFF -> P2PEmptyState(
                    Icons.Rounded.BluetoothDisabled,
                    "Bluetooth apagado",
                    "Enciéndelo para ver a quien tengas cerca con Dezzy.",
                )
                NearbyStatus.NO_PERMISSION -> P2PEmptyState(
                    Icons.Rounded.Radar,
                    "Falta un permiso",
                    "Dezzy necesita Bluetooth para encontrar gente cerca. No usa tu ubicación ni internet.",
                    action = { DezzyButton(onClick = { request(nearby.requiredPermissions()) }) { Text("Dar permiso") } },
                )
                else -> if (denied && !nearby.hasPermissions()) {
                    P2PEmptyState(
                        Icons.Rounded.Radar,
                        "Falta un permiso",
                        "Sin el permiso de Bluetooth no se puede buscar gente cerca.",
                        action = { DezzyButton(onClick = { request(nearby.requiredPermissions()) }) { Text("Dar permiso") } },
                    )
                } else if (people.isEmpty()) {
                    Text(
                        "Buscando gente cerca… Pídele a un amigo que abra la pestaña Cerca en su Dezzy.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
        }
        if (people.isNotEmpty()) {
            item { P2PSectionTitle("Cerca de ti") }
            items(people, key = { it.pubkey }) { person ->
                NearbyRow(person, profiles[person.pubkey] ?: com.dezzy.app.feature.social.P2PUser(person.pubkey)) { selected = person }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Visibility, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text("Visible en el radar", fontSize = 16.sp)
                    Text(
                        "Los demás pueden verte cuando abren esta pestaña.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DezzySwitch(checked = visible, onCheckedChange = {
                    social.setNearbyVisible(it)
                    nearby.stop()
                    nearby.start()
                })
            }
        }
    }
    selected?.let { person ->
        NearbyPersonSheet(ctx, profiles[person.pubkey] ?: com.dezzy.app.feature.social.P2PUser(person.pubkey)) { selected = null }
    }
}

@Composable
private fun Radar(people: List<P2PNearbyPerson>, modifier: Modifier, scanning: Boolean) {
    val transition = rememberInfiniteTransition(label = "radar")
    val pulse by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )
    val primary = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2
        listOf(0.33f, 0.66f, 1f).forEach { f -> drawCircle(line, r * f, c, style = Stroke(1.dp.toPx())) }
        if (scanning) {
            drawCircle(primary.copy(alpha = 0.35f * (1f - pulse)), r * pulse, c)
            val p2 = (pulse + 0.5f) % 1f
            drawCircle(primary.copy(alpha = 0.25f * (1f - p2)), r * p2, c)
        }
        drawCircle(primary, 8.dp.toPx(), c)
        people.forEach { p ->
            // Más señal = más cerca del centro. El ángulo sale de la clave para que cada persona tenga su sitio.
            val strength = ((p.rssi + 95) / 55f).coerceIn(0f, 1f)
            val dist = r * (0.9f - 0.65f * strength)
            val angle = (p.pubkey.take(6).toLongOrNull(16) ?: 0L) % 360 * (PI / 180.0)
            val pos = Offset(c.x + (cos(angle) * dist).toFloat(), c.y + (sin(angle) * dist).toFloat())
            drawCircle(Color(0xFFFE2C55), 7.dp.toPx(), pos)
            drawCircle(Color.White, 7.dp.toPx(), pos, style = Stroke(1.5.dp.toPx()))
        }
    }
}

@Composable
private fun NearbyRow(person: P2PNearbyPerson, user: com.dezzy.app.feature.social.P2PUser, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        P2PAvatar(user, 44.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(user.displayName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    person.rssi > -60 -> "Muy cerca"
                    person.rssi > -75 -> "Cerca"
                    else -> "A unos metros"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NearbyPersonSheet(ctx: ScreenContext, user: com.dezzy.app.feature.social.P2PUser, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            P2PAvatar(user, 88.dp, ring = true)
            Text(user.displayName, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text(user.fingerprint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (user.about.isNotBlank()) Text(user.about, fontSize = 14.sp)
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DezzyOutlinedButton(onClick = { onDismiss(); ctx.navigator.push(Screen.P2PProfile(user.pubkey)) }) { Text("Ver perfil") }
                DezzyButton(onClick = { onDismiss(); ctx.navigator.push(Screen.P2PChat(user.pubkey)) }) { Text("Mensaje") }
            }
        }
    }
}
