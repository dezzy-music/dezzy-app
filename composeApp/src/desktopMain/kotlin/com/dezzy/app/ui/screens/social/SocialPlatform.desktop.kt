/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.social

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import java.awt.FileDialog
import java.awt.Frame

@Composable
actual fun rememberMediaPicker(kind: PickKind, onPicked: (source: String) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    return {
        val dialog = FileDialog(null as Frame?, if (kind == PickKind.VIDEO) "Elegir video" else "Elegir foto", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name ->
            val n = name.lowercase()
            if (kind == PickKind.VIDEO) n.endsWith(".mp4") || n.endsWith(".mov") || n.endsWith(".webm") || n.endsWith(".mkv")
            else n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")
        }
        dialog.isVisible = true
        val file = dialog.file
        val dir = dialog.directory
        if (file != null && dir != null) callback(dir + file)
    }
}

@Composable
actual fun rememberPermissionRequester(onResult: (granted: Boolean) -> Unit): (permissions: List<String>) -> Unit {
    val callback by rememberUpdatedState(onResult)
    return { callback(true) }
}

@Composable
actual fun SecureWindow() {
    // El escritorio no tiene un equivalente a FLAG_SECURE.
}
