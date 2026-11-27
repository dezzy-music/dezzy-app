/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlin.concurrent.thread

@Composable
actual fun rememberThemeJsonPicker(onPicked: (String) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    return remember {
        {
            thread(isDaemon = true) {
                val dialog = FileDialog(null as Frame?, "Importar tema", FileDialog.LOAD)
                dialog.setFilenameFilter { _, name -> name.lowercase().endsWith(".json") }
                dialog.isVisible = true
                val file = dialog.file
                val dir = dialog.directory
                if (file != null && dir != null) {
                    val text = runCatching { File(dir, file).readText() }.getOrNull()
                    if (text != null) callback(text)
                }
            }
        }
    }
}

@Composable
actual fun rememberThemeJsonSaver(): (fileName: String, content: String) -> Unit = remember {
    { fileName, content ->
        thread(isDaemon = true) {
            val dialog = FileDialog(null as Frame?, "Exportar tema", FileDialog.SAVE)
            dialog.file = fileName
            dialog.isVisible = true
            val file = dialog.file
            val dir = dialog.directory
            if (file != null && dir != null) runCatching { File(dir, file).writeText(content) }
        }
    }
}
