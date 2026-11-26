/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.core.model.SessionProvider
import com.dezzy.app.core.model.UserSession
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.navigation.SettingsPage
import com.dezzy.app.ui.screens.ScreenContext
import org.koin.compose.koinInject

/** Una fila buscable de Ajustes: o de la página principal o de una subpágina. */
private class SettingsEntry(
    val title: String,
    val subtitle: String?,
    val icon: ImageVector?,
    val badge: Boolean = false,
    val onClick: () -> Unit,
)

/** Índice estático de las filas de las subpáginas: título y página donde viven. */
private val SUBPAGE_INDEX: List<Pair<String, SettingsPage>> = listOf(
    // Apariencia
    "Tema" to SettingsPage.APPEARANCE,
    "Fondo negro puro" to SettingsPage.APPEARANCE,
    "Color dinámico" to SettingsPage.APPEARANCE,
    "Estilo de la interfaz" to SettingsPage.APPEARANCE,
    "Intensidad del cristal" to SettingsPage.APPEARANCE,
    "Estilo del reproductor" to SettingsPage.APPEARANCE,
    "Estilo de la barra de progreso" to SettingsPage.APPEARANCE,
    "Visualizador" to SettingsPage.APPEARANCE,
    // Reproductor y audio
    "Calidad de audio" to SettingsPage.PLAYER,
    "Motor Hi-Res" to SettingsPage.PLAYER,
    "Calidad con datos móviles" to SettingsPage.PLAYER,
    "Normalizar el volumen" to SettingsPage.PLAYER,
    "Fundido entre canciones" to SettingsPage.PLAYER,
    "Saltar silencios" to SettingsPage.PLAYER,
    "Ecualizador" to SettingsPage.PLAYER,
    "Recordar la cola" to SettingsPage.PLAYER,
    "Saltar canciones que fallan" to SettingsPage.PLAYER,
    "Pausar al silenciar" to SettingsPage.PLAYER,
    "Reproducir al conectar Bluetooth" to SettingsPage.PLAYER,
    "Calidad máxima del vídeo" to SettingsPage.PLAYER,
    "Ajuste de la imagen" to SettingsPage.PLAYER,
    "Mantener el modo vídeo al cambiar de canción" to SettingsPage.PLAYER,
    "Ventana flotante (PiP)" to SettingsPage.PLAYER,
    // Privacidad
    "Pausar el historial" to SettingsPage.PRIVACY,
    "Borrar el historial" to SettingsPage.PRIVACY,
    "Escuchas para estadísticas" to SettingsPage.PRIVACY,
    // Descargas y almacenamiento
    "Canciones descargadas" to SettingsPage.STORAGE,
    "Calidad de descarga" to SettingsPage.STORAGE,
    "Caché de imágenes" to SettingsPage.STORAGE,
    "Caché de vídeo" to SettingsPage.STORAGE,
    // Acerca de
    "Versión" to SettingsPage.ABOUT,
    "Licencia" to SettingsPage.ABOUT,
)

private fun pageName(page: SettingsPage): String = when (page) {
    SettingsPage.MAIN -> "Ajustes"
    SettingsPage.APPEARANCE -> "Apariencia"
    SettingsPage.PLAYER -> "Reproductor y audio"
    SettingsPage.PRIVACY -> "Privacidad"
    SettingsPage.STORAGE -> "Descargas y almacenamiento"
    SettingsPage.ABOUT -> "Acerca de Dezzy"
    SettingsPage.ACCOUNTS -> "Cuentas y servicios"
    SettingsPage.CONTENT -> "Contenido"
    SettingsPage.YOUTUBE_LOGIN -> "Iniciar sesión"
    SettingsPage.SPOTIFY -> "Integración de Spotify"
    SettingsPage.SPOTIFY_LOGIN -> "Iniciar sesión en Spotify"
    SettingsPage.SPOTIFY_IMPORT -> "Importar de Spotify"
}

/** Minúsculas y sin tildes, para comparar lo que escribe el usuario. */
private fun normalize(text: String): String = buildString {
    for (c in text.lowercase()) {
        append(
            when (c) {
                'á', 'à', 'ä', 'â' -> 'a'
                'é', 'è', 'ë', 'ê' -> 'e'
                'í', 'ì', 'ï', 'î' -> 'i'
                'ó', 'ò', 'ö', 'ô' -> 'o'
                'ú', 'ù', 'ü', 'û' -> 'u'
                else -> c
            },
        )
    }
}

@Composable
internal fun SettingsScreen(ctx: ScreenContext) {
    var query by rememberSaveable { mutableStateOf("") }
    fun open(page: SettingsPage) = ctx.navigator.push(Screen.Settings(page))
    val accounts: AccountStore = koinInject()
    val sessions by accounts.sessions.collectAsState()
    val youtube = sessions[SessionProvider.YOUTUBE]
    val language by ctx.prefs.contentLanguage.collectAsState("es")
    val country by ctx.prefs.contentCountry.collectAsState("US")

    val group1 = listOf(
        SettingsEntry("Apariencia", "Tema del sistema, color dinámico", Icons.Rounded.Palette) { open(SettingsPage.APPEARANCE) },
        SettingsEntry("Reproductor y audio", "Calidad alta, normalizar volumen", Icons.Rounded.PlayCircle) { open(SettingsPage.PLAYER) },
        SettingsEntry("Letras e IA", "Traducción automática activada", Icons.Rounded.Lyrics) { ctx.pending("Letras e IA", "7") },
        SettingsEntry("Contenido", "${languageName(language)}, ${countryName(country)}", Icons.Rounded.Language) { open(SettingsPage.CONTENT) },
    )
    val group2 = listOf(
        SettingsEntry("Cuentas y servicios", "YouTube, Spotify", Icons.Rounded.Hub) { open(SettingsPage.ACCOUNTS) },
        SettingsEntry("Descargas y almacenamiento", "Caché 1,2 GB, lossless", Icons.Rounded.Download) { open(SettingsPage.STORAGE) },
        SettingsEntry("Privacidad", "Historial activado", Icons.Rounded.Lock) { open(SettingsPage.PRIVACY) },
    )
    val group3 = listOf(
        SettingsEntry("Actualizaciones", "Versión 3.0.8 disponible", Icons.Rounded.SystemUpdate, badge = false) { ctx.pending("Actualizaciones", "11") },
        SettingsEntry("Acerca de Dezzy", "Versión 3.0.7", Icons.Rounded.Info) { open(SettingsPage.ABOUT) },
    )

    SettingsPage(title = "Ajustes", onBack = null) {
        item(key = "search") { SettingsSearchField(query) { query = it } }
        val q = normalize(query.trim())
        if (q.isEmpty()) {
            item(key = "account") {
                SettingsAccountCard(youtube) { open(SettingsPage.ACCOUNTS) }
            }
            item(key = "g1") { SettingsEntryGroup(group1) }
            item(key = "g2") { SettingsEntryGroup(group2) }
            item(key = "g3") { SettingsEntryGroup(group3) }
        } else {
            val mainResults = (group1 + group2 + group3).filter {
                normalize(it.title).contains(q) || normalize(it.subtitle.orEmpty()).contains(q)
            }
            val subResults = SUBPAGE_INDEX
                .filter { (title, _) -> normalize(title).contains(q) }
                .map { (title, page) -> SettingsEntry(title, pageName(page), null) { open(page) } }
            val results = mainResults + subResults
            item(key = "results") {
                if (results.isEmpty()) {
                    Text(
                        "Sin resultados",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                } else {
                    SettingsEntryGroup(results)
                }
            }
        }
    }
}

@Composable
private fun SettingsEntryGroup(entries: List<SettingsEntry>) {
    SettingsGroup {
        entries.forEachIndexed { index, e ->
            if (index > 0) SettingsDivider()
            SettingsRow(title = e.title, subtitle = e.subtitle, icon = e.icon, onClick = e.onClick, badge = e.badge)
        }
    }
}

@Composable
private fun SettingsSearchField(query: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("Buscar en Ajustes", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SettingsAccountCard(session: UserSession?, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (session?.avatarUrl != null) {
            CoverImage(session.avatarUrl, Modifier.size(56.dp), CircleShape, sizePx = 168)
        } else {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.inversePrimary)),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("D", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (session != null) session.name ?: "Cuenta de YouTube Music" else "Inicia sesión",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                when {
                    session == null -> "Conecta tu cuenta de YouTube Music"
                    !session.isValid -> "Vuelve a iniciar sesión"
                    else -> "Cuenta de YouTube Music conectada"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
