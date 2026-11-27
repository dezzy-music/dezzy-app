/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.ai.DezzyIaClient
import com.dezzy.app.feature.ai.IaActionType
import com.dezzy.app.feature.ai.IaTurn
import com.dezzy.app.feature.dj.DjSettings
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.components.glass.DezzyFilledIconButton
import com.dezzy.app.ui.components.glass.DezzyIconButton
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.navigation.SettingsPage
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import com.dezzy.app.ui.screens.personalize.DezzyLogoMark
import io.ktor.client.HttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private class IaChatMessage(
    val id: Long,
    val text: String,
    val isUser: Boolean,
    val recommendations: List<Track> = emptyList(),
)

private const val WELCOME = "¡Hola! Soy Dezzy, tu asistente musical con IA. ¿De qué humor estás hoy o qué te gustaría escuchar?"

/**
 * Chat con Dezzy IA: entiende peticiones de música, responde y puede poner canciones, añadirlas
 * a la cola o sugerirlas. Usa la clave de Gemini que el usuario guardó en los ajustes del DJ (BYOK).
 */
@Composable
fun DezzyIAScreen(ctx: ScreenContext) {
    val http: HttpClient = koinInject()
    val settings: DjSettings = koinInject()
    val client = remember(http) { DezzyIaClient(http) }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    var inputText by rememberSaveable { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }
    var nextId by remember { mutableStateOf(1L) }
    val messages = remember { mutableStateListOf(IaChatMessage(0L, WELCOME, isUser = false)) }

    fun add(text: String, isUser: Boolean, recommendations: List<Track> = emptyList()) {
        messages.add(IaChatMessage(nextId++, text, isUser, recommendations))
    }

    /** Contexto musical en tiempo real: lo que suena, lo más escuchado y los me gusta. */
    suspend fun buildUserContext(): String {
        val current = ctx.player.state.value.queue.current?.track
        val topSongs = runCatching { ctx.repository.getMostPlayed(0L, 10).first() }.getOrDefault(emptyList())
        val liked = runCatching { ctx.repository.getFavorites().first() }.getOrDefault(emptyList())
        val topArtists = topSongs
            .flatMap { t -> t.artists.split(",", "&").map { it.trim() }.filter { it.isNotBlank() } }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(10).map { it.key }
        return buildString {
            append("Contexto del usuario musical en TIEMPO REAL:\n")
            if (current != null) append("- MÚSICA ACTUALMENTE EN REPRODUCCIÓN: \"${current.title}\" de ${current.artists}.\n\n")
            if (topArtists.isNotEmpty()) append("- Artistas más escuchados: ${topArtists.joinToString()}.\n")
            if (topSongs.isNotEmpty()) append("- Canciones más escuchadas: ${topSongs.joinToString { "${it.title} de ${it.artists}" }}.\n")
            if (liked.isNotEmpty()) append("- Le gustan: ${liked.take(10).joinToString { "${it.title} de ${it.artists}" }}.\n")
        }
    }

    fun sendMessage() {
        val text = inputText.trim()
        if (text.isEmpty() || isTyping) return
        add(text, isUser = true)
        inputText = ""
        keyboard?.hide()
        ctx.scope.launch {
            listState.animateScrollToItem(messages.size - 1)
            val key = settings.geminiKey()
            if (key.isBlank()) {
                add("Para charlar conmigo necesito tu clave de Gemini (es gratis y es tuya). Ponla en Ajustes, en el apartado del DJ.", isUser = false)
                ctx.navigator.push(Screen.Settings(SettingsPage.DJ))
                return@launch
            }
            isTyping = true
            val history = messages.takeLast(10).map { IaTurn(it.isUser, it.text) }
            val result = client.chat(key, DezzyIaClient.systemPrompt(buildUserContext()), history)
            isTyping = false
            result.onSuccess { raw ->
                val reply = DezzyIaClient.parseReply(raw)
                val action = reply.action
                var tracks = emptyList<Track>()
                if (action != null) {
                    val songs = ctx.client.search(action.query).getOrNull()?.songs.orEmpty().filter { it.videoId != null }
                    when (action.type) {
                        IaActionType.PLAY_MUSIC -> if (songs.isNotEmpty()) ctx.player.playTracks(songs, 0, "ia:${action.query}")
                        IaActionType.ADD_TO_QUEUE -> songs.firstOrNull()?.let {
                            ctx.player.addToQueue(it)
                            ctx.toast("Añadida a la cola: ${it.title}")
                        }
                        IaActionType.RECOMMEND -> tracks = songs.take(8)
                    }
                    if (songs.isEmpty()) ctx.toast("No encontré nada para «${action.query}»")
                }
                add(reply.text.ifBlank { "Hecho." }, isUser = false, recommendations = tracks)
            }.onFailure { error ->
                add("Error de conexión con la IA: ${error.message ?: "Desconocido"}", isUser = false)
            }
            delay(100)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopSpacer()
        ScreenTitle("Dezzy IA", onBack = { ctx.navigator.back() })
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.id }) { message -> ChatBubble(message, ctx) }
            if (isTyping) item(key = "typing") { TypingIndicator() }
        }

        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Pregúntame algo...") },
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                )
                DezzyFilledIconButton(
                    onClick = { sendMessage() },
                    enabled = inputText.isNotBlank() && !isTyping,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, "Enviar mensaje", Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun AssistantAvatar() {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
        contentAlignment = Alignment.Center,
    ) {
        DezzyLogoMark(modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun ChatBubble(message: IaChatMessage, ctx: ScreenContext) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val bubbleShape = if (message.isUser) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!message.isUser) {
            AssistantAvatar()
            Spacer(Modifier.width(8.dp))
        }
        Column(
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Surface(
                shape = bubbleShape,
                color = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = if (message.isUser) 0.dp else 1.dp,
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(12.dp),
                    color = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!message.isUser) {
                DezzyIconButton(onClick = { clipboard.setText(AnnotatedString(message.text)) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        "Copiar al portapapeles",
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }

    if (!message.isUser && message.recommendations.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(start = 40.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(message.recommendations, key = { it.videoId ?: it.title }) { track -> SongRecommendationCard(track, ctx) }
        }
    }
}

@Composable
private fun SongRecommendationCard(track: Track, ctx: ScreenContext) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.width(160.dp).clickable { ctx.player.playRadio(track, source = "radio:${track.title}") },
    ) {
        Column(Modifier.fillMaxWidth()) {
            CoverImage(track.thumbnail, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(0.dp), sizePx = 480)
            Column(Modifier.padding(8.dp)) {
                Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    track.artists,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
        AssistantAvatar()
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp,
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { index ->
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(index * 200L)
                        while (true) {
                            visible = true
                            delay(600)
                            visible = false
                            delay(600)
                        }
                    }
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (visible) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            ),
                    )
                }
            }
        }
    }
}
