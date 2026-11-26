/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material3.*
import com.dezzy.app.ui.components.glass.DezzyIconButton as IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.dezzy.app.ui.components.glass.DezzyGlassSurfaceButton
import com.dezzy.app.ui.player.kit.BottomSheetState
import com.dezzy.app.ui.player.kit.DevicesButton
import com.dezzy.app.ui.player.kit.LocalPlayerConnection
import com.dezzy.app.ui.player.kit.MediaMetadata
import com.dezzy.app.ui.player.kit.NavController
import com.dezzy.app.ui.player.kit.ShareBottomSheet
import com.dezzy.app.ui.player.kit.isLandscape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarModePlayer(
    state: BottomSheetState,
    navController: NavController,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    onQueueClick: () -> Unit,
    onVisualizerClick: () -> Unit = {},
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    // Compartir directo (tarjeta Canción/Letra/Canvas)
    var showShareSheet by remember { mutableStateOf(false) }
    val shareLyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    if (showShareSheet) {
        ShareBottomSheet(
            title = mediaMetadata?.title ?: "",
            subtitle = mediaMetadata?.artists?.joinToString { it.name } ?: "",
            thumbnailUrl = mediaMetadata?.thumbnailUrl,
            shareLink = "https://music.youtube.com/watch?v=${mediaMetadata?.id ?: ""}",
            lyrics = shareLyricsEntity?.lyrics,
            mediaId = mediaMetadata?.id,
            onDismiss = { showShareSheet = false }
        )
    }

    val landscape = isLandscape()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // High contrast for driving
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { state.collapseSoft() }) {
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = "MODO COCHE",
                    color = Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    style = MaterialTheme.typography.labelLarge
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DevicesButton(tint = Color.White)
                    IconButton(onClick = { playerConnection.enableVideoMode() }) {
                        Icon(
                            imageVector = Icons.Rounded.SlowMotionVideo,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    IconButton(onClick = onQueueClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = "Queue",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            if (landscape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Artwork
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = mediaMetadata?.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxHeight(0.85f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(24.dp))
                        )
                    }

                    // Right: Title & Big Transport Controls
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = mediaMetadata?.title ?: "",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = mediaMetadata?.artists?.joinToString { it.name } ?: "",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DezzyGlassSurfaceButton(
                                onClick = { playerConnection.player.seekToPrevious() },
                                shape = CircleShape,
                                normalContainerColor = Color.White.copy(alpha = 0.1f),
                                normalContentColor = Color.White,
                                prominent = false,
                                modifier = Modifier.size(70.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipPrevious,
                                    contentDescription = "Previous",
                                    modifier = Modifier.size(42.dp)
                                )
                            }

                            DezzyGlassSurfaceButton(
                                onClick = { playerConnection.player.togglePlayPause() },
                                shape = CircleShape,
                                normalContainerColor = MaterialTheme.colorScheme.primary,
                                normalContentColor = MaterialTheme.colorScheme.onPrimary,
                                prominent = true,
                                modifier = Modifier.size(90.dp),
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    modifier = Modifier.size(54.dp)
                                )
                            }

                            DezzyGlassSurfaceButton(
                                onClick = { playerConnection.player.seekToNext() },
                                shape = CircleShape,
                                normalContainerColor = Color.White.copy(alpha = 0.1f),
                                normalContentColor = Color.White,
                                prominent = false,
                                modifier = Modifier.size(70.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = "Next",
                                    modifier = Modifier.size(42.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(0.2f))

                // Big Artwork
                AsyncImage(
                    model = mediaMetadata?.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(32.dp))
                )

                Spacer(modifier = Modifier.weight(0.2f))

                // Text Info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = mediaMetadata?.title ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = mediaMetadata?.artists?.joinToString { it.name } ?: "",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.weight(0.2f))

                // HUGE Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DezzyGlassSurfaceButton(
                        onClick = { playerConnection.player.seekToPrevious() },
                        shape = CircleShape,
                        normalContainerColor = Color.White.copy(alpha = 0.1f),
                        normalContentColor = Color.White,
                        prominent = false,
                        modifier = Modifier.size(80.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    DezzyGlassSurfaceButton(
                        onClick = { playerConnection.player.togglePlayPause() },
                        shape = CircleShape,
                        normalContainerColor = MaterialTheme.colorScheme.primary,
                        normalContentColor = MaterialTheme.colorScheme.onPrimary,
                        prominent = true,
                        modifier = Modifier.size(110.dp),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    DezzyGlassSurfaceButton(
                        onClick = { playerConnection.player.seekToNext() },
                        shape = CircleShape,
                        normalContainerColor = Color.White.copy(alpha = 0.1f),
                        normalContentColor = Color.White,
                        prominent = false,
                        modifier = Modifier.size(80.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(0.2f))
            }
        }
    }
}
