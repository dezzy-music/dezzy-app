/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress("DEPRECATION")

package com.dezzy.app.ui.screens.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.feature.social.FollowState
import com.dezzy.app.feature.social.P2PSocial
import com.dezzy.app.feature.social.P2PUser
import com.dezzy.app.ui.components.glass.DezzyFilledTonalButton
import com.dezzy.app.ui.components.glass.DezzyOutlinedButton
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.player.kit.rememberShareText
import com.dezzy.app.ui.screens.ScreenContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Hoja «Añadir amigo»: enlace de invitación, tu ID y búsqueda por nombre, npub o enlace. */
@Composable
internal fun P2PAddFriendSheet(ctx: ScreenContext, onDismiss: () -> Unit) {
    val social: P2PSocial = koinInject()
    val clipboard = LocalClipboardManager.current
    val share = rememberShareText()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<P2PUser>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val link = social.myInviteLink()
    val npub = social.myProfile?.npub

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(450)
        val direct = social.parsePublicKey(q)
        results = if (direct != null) {
            runCatching { social.loadProfiles(listOf(direct), force = true) }
            listOf(social.user(direct))
        } else {
            runCatching { social.searchUsers(q) }.getOrDefault(emptyList())
        }.filter { it.pubkey != social.me }
        searching = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Añadir amigo", fontSize = 20.sp)
            if (link != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DezzyFilledTonalButton(onClick = { share("Sígueme en Dezzy: $link") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Compartir enlace")
                    }
                    DezzyOutlinedButton(
                        onClick = { npub?.let { clipboard.setText(AnnotatedString(it)); ctx.toast("ID copiado") } },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copiar mi ID")
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Nombre, npub o enlace") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                searching -> Text("Buscando…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                query.trim().length >= 2 && results.isEmpty() ->
                    Text("No hay resultados.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            results.forEach { user ->
                ResultRow(ctx, social, user, onDismiss)
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun ResultRow(ctx: ScreenContext, social: P2PSocial, user: P2PUser, onDismiss: () -> Unit) {
    var state by remember(user.pubkey) { mutableStateOf<FollowState?>(null) }
    LaunchedEffect(user.pubkey) { state = runCatching { social.followState(user.pubkey) }.getOrNull() }
    Row(
        Modifier.fillMaxWidth().clickable { onDismiss(); ctx.navigator.push(Screen.P2PProfile(user.pubkey)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        P2PAvatar(user, 44.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(user.displayName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(user.fingerprint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        DezzyOutlinedButton(onClick = { onDismiss(); ctx.navigator.push(Screen.P2PChat(user.pubkey)) }) { Text("Mensaje") }
        Spacer(Modifier.width(8.dp))
        when (state) {
            FollowState.FOLLOWING -> Text("Siguiendo", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FollowState.REQUESTED -> Text("Solicitado", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> DezzyFilledTonalButton(
                onClick = {
                    ctx.scope.launch {
                        val ok = runCatching { social.setFollowing(user.pubkey, true) }.getOrDefault(false)
                        if (ok) state = runCatching { social.followState(user.pubkey) }.getOrNull()
                        else ctx.toast("No se pudo seguir")
                    }
                },
            ) { Text("Seguir") }
        }
    }
}
