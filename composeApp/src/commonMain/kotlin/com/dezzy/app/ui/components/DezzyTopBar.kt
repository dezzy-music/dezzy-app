/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.core.model.SessionProvider
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.player.kit.ConnectDeviceBottomSheet
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.TopSpacer
import org.koin.compose.koinInject

/**
 * Barra superior común de Tu biblioteca y Salas: «Dezzy» a la izquierda y a la derecha
 * dispositivos (Connect), historial, estadísticas, campana y avatar (→ Ajustes).
 */
@Composable
fun DezzyTopBar(ctx: ScreenContext) {
    val accounts: AccountStore = koinInject()
    val sessions by accounts.sessions.collectAsState()
    val avatarUrl = sessions[SessionProvider.YOUTUBE]?.avatarUrl
    var devices by remember { mutableStateOf(false) }

    TopSpacer()
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            "Dezzy",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { devices = true }) { Icon(Icons.Rounded.Devices, "Dispositivos") }
        IconButton(onClick = { ctx.navigator.push(Screen.History) }) { Icon(Icons.Filled.WatchLater, "Historial") }
        IconButton(onClick = { ctx.navigator.push(Screen.Stats) }) { Icon(Icons.Rounded.BarChart, "Estadísticas") }
        IconButton(onClick = { ctx.navigator.push(Screen.NewReleases) }) { Icon(Icons.Rounded.Notifications, "Novedades") }
        Box(Modifier.padding(start = 4.dp, end = 4.dp)) {
            if (avatarUrl != null) {
                CoverImage(avatarUrl, Modifier.size(26.dp).clickable { ctx.navigator.push(Screen.Settings(com.dezzy.app.ui.navigation.SettingsPage.ACCOUNTS)) }, CircleShape, sizePx = 78, contentDescription = "Cuenta")
            } else {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { ctx.navigator.push(Screen.Settings(com.dezzy.app.ui.navigation.SettingsPage.ACCOUNTS)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("D", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (devices) ConnectDeviceBottomSheet(onDismissRequest = { devices = false })
}
