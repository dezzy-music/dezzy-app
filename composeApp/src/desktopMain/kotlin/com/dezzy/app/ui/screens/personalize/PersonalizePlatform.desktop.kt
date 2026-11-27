/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.personalize

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import java.awt.FileDialog
import java.awt.Frame
import kotlin.concurrent.thread

private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif")
private val VIDEO_EXT = setOf("mp4", "webm")

@Composable
actual fun rememberMediaPicker(onPicked: (uri: String, mime: String?) -> Unit): (MediaKind) -> Unit {
    val callback by rememberUpdatedState(onPicked)
    return remember {
        { kind ->
            thread(isDaemon = true) {
                val dialog = FileDialog(null as Frame?, "Elegir archivo", FileDialog.LOAD)
                val allowed = when (kind) {
                    MediaKind.IMAGE -> IMAGE_EXT
                    MediaKind.VIDEO -> VIDEO_EXT
                    MediaKind.IMAGE_OR_VIDEO -> IMAGE_EXT + VIDEO_EXT
                }
                dialog.setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in allowed }
                dialog.isVisible = true
                val file = dialog.file
                val dir = dialog.directory
                if (file != null && dir != null) {
                    val ext = file.substringAfterLast('.', "").lowercase()
                    val mime = when (ext) {
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        "gif" -> "image/gif"
                        "mp4" -> "video/mp4"
                        "webm" -> "video/webm"
                        else -> null
                    }
                    val path = java.io.File(dir, file).absolutePath.replace('\\', '/')
                    callback("file://" + (if (path.startsWith("/")) path else "/$path"), mime)
                }
            }
        }
    }
}

/** En escritorio el vídeo de fondo no se reproduce (no hay reproductor de vídeo propio todavía). */
@Composable
actual fun WallpaperVideo(uri: String, modifier: Modifier) = Unit
