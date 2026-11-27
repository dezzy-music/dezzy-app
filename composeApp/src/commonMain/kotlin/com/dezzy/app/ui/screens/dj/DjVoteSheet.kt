/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.dj

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.components.glass.DezzyOutlinedButton

/** Una propuesta del DJ para votar en una sala (la sala «Escuchar juntos» aporta el recuento). */
class DjVote(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnail: String? = null,
    val votes: Int = 0,
    val votedByMe: Boolean = false,
)

/** Hoja para votar entre las canciones que propone el DJ. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DjVoteSheet(
    djName: String,
    options: List<DjVote>,
    onVote: (DjVote) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("$djName propone", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            options.forEach { option ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverImage(option.thumbnail, Modifier.size(48.dp), RoundedCornerShape(6.dp), sizePx = 144)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(option.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(
                            option.artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DezzyOutlinedButton(onClick = { onVote(option) }, enabled = !option.votedByMe) {
                        Text(if (option.votedByMe) "Votada · ${option.votes}" else "Votar · ${option.votes}")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
