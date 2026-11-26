/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.downloads

import androidx.compose.runtime.Composable

/** Devuelve una función que abre el selector de carpeta; al elegir llama a [onPicked] con la carpeta. Desktop: no hace nada. */
@Composable
expect fun rememberExportFolderPicker(onPicked: (folder: String) -> Unit): () -> Unit
