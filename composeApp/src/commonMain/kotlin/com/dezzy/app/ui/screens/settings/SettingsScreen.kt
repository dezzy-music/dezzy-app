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
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.AutoAwesome
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
import com.dezzy.app.core.AppVersionName
import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.feature.dj.DjSettings
import com.dezzy.app.feature.dj.DjVoice
import com.dezzy.app.feature.lyrics.LyricsTranslateLangKey
import com.dezzy.app.ui.player.kit.rememberPreference
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
    // Páginas nuevas
    "Exportar o importar listas" to SettingsPage.BACKUP,
    "PoToken" to SettingsPage.PO_TOKEN,
    "Estadísticas para nerds" to SettingsPage.EXPERIMENTS,
    "Reloj en reposo" to SettingsPage.AOD,
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
    SettingsPage.SCROBBLING -> "Scrobbling"
    SettingsPage.DISCORD -> "Discord"
    SettingsPage.LYRICS -> "Letras e IA"
    SettingsPage.LOSSLESS -> "Descargas lossless"
    SettingsPage.EQUALIZER -> "Ecualizador"
    SettingsPage.DESKTOP_LINK -> "Vincular escritorio"
    SettingsPage.SOCIAL -> "Red social"
    SettingsPage.DJ -> "DJ Dezzy"
    SettingsPage.DJ_VOICE -> "Voz del DJ"
    SettingsPage.APP_ICON -> "Icono de la app"
    SettingsPage.WALLPAPER -> "Fondos de pantalla"
    SettingsPage.COLOR_PALETTE -> "Paleta de color"
    SettingsPage.THEME_CREATOR -> "Creador de Temas"
    SettingsPage.BACKUP -> "Copia de seguridad"
    SettingsPage.PO_TOKEN -> "Token de reproducción"
    SettingsPage.EXPERIMENTS -> "Experimentos"
    SettingsPage.AOD -> "Pantalla siempre activa"
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
    val themeMode by ctx.prefs.themeMode.collectAsState("dark")
    val quality by ctx.prefs.audioQuality.collectAsState("auto")
    val normalize by ctx.prefs.normalizeVolume.collectAsState(false)
    val translateLang by rememberPreference(LyricsTranslateLangKey, "")
    val djSettings: DjSettings = koinInject()
    val djProvider by djSettings.provider.collectAsState(DjVoice.FREE)
    val djVoiceName by djSettings.voiceName.collectAsState("")
    val openDefaultLinks = rememberOpenDefaultLinksSettings()
    val spotifyConnected = sessions[SessionProvider.SPOTIFY] != null

    val themeText = when (themeMode) {
        "light" -> "Tema claro"
        "system" -> "Según el sistema"
        else -> "Tema oscuro"
    }
    val qualityText = when (quality) {
        "high" -> "Calidad alta"
        "low" -> "Calidad baja"
        else -> "Calidad automática"
    } + if (normalize) ", normalizar volumen" else ""
    val djText = if (djProvider == DjVoice.FREE) {
        "Voz gratuita"
    } else {
        DjVoice.providerName(djProvider) + if (djVoiceName.isNotBlank()) " · $djVoiceName" else ""
    }

    val group1 = listOf(
        SettingsEntry("Apariencia", themeText, Icons.Rounded.Palette) { open(SettingsPage.APPEARANCE) },
        SettingsEntry("Reproductor y audio", qualityText, Icons.Rounded.PlayCircle) { open(SettingsPage.PLAYER) },
        SettingsEntry(
            "Letras e IA",
            if (translateLang.isNotEmpty()) "Traducción automática activada" else "Traducción automática desactivada",
            Icons.Rounded.Lyrics,
        ) { open(SettingsPage.LYRICS) },
        SettingsEntry("DJ Dezzy", djText, Icons.Rounded.AutoAwesome) { open(SettingsPage.DJ) },
        SettingsEntry("Contenido", "Idioma y región del sistema", Icons.Rounded.Language) { open(SettingsPage.CONTENT) },
        SettingsEntry("Pantalla siempre activa", "Reloj y letras con la pantalla en reposo", Icons.Rounded.Schedule) { open(SettingsPage.AOD) },
    )
    val group2 = listOf(
        SettingsEntry(
            "Cuentas y servicios",
            if (spotifyConnected) "Spotify" else "YouTube, Spotify y más",
            Icons.Rounded.Hub,
        ) { open(SettingsPage.ACCOUNTS) },
        SettingsEntry("Descargas y almacenamiento", "Caché, descargas y espacio usado", Icons.Rounded.Download) { open(SettingsPage.STORAGE) },
        SettingsEntry("Copia de seguridad", "Exportar o restaurar tus datos", Icons.Rounded.Backup) { open(SettingsPage.BACKUP) },
        SettingsEntry("Privacidad", "Historial y capturas de pantalla", Icons.Rounded.Lock) { open(SettingsPage.PRIVACY) },
    )
    val group3 = listOfNotNull(
        openDefaultLinks?.let {
            SettingsEntry("Enlaces predeterminados", "Abrir enlaces de YouTube Music con Dezzy", Icons.Rounded.Link) { it() }
        },
        SettingsEntry("Token de reproducción", "PoToken y datos del cliente web", Icons.Rounded.VpnKey) { open(SettingsPage.PO_TOKEN) },
        SettingsEntry("Experimentos", "Funciones en pruebas y depuración", Icons.Rounded.Science) { open(SettingsPage.EXPERIMENTS) },
        SettingsEntry("Actualizaciones", "Estás al día, versión $AppVersionName", Icons.Rounded.SystemUpdate) { },
        SettingsEntry("Acerca de", "Dezzy $AppVersionName", Icons.Rounded.Info) { open(SettingsPage.ABOUT) },
    )

    SettingsPage(title = "Ajustes", onBack = null, backAbove = { ctx.navigator.back() }) {
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
            .background(Color(0xFFDCDCDC))
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
                color = Color(0xFF1B1B1F),
            )
            Text(
                when {
                    session == null -> "Conecta tu cuenta de YouTube Music"
                    !session.isValid -> "Vuelve a iniciar sesión"
                    else -> "Cuenta de YouTube Music conectada"
                },
                fontSize = 13.sp,
                color = Color(0xFF47464F),
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color(0xFF1B1B1F))
    }
}
