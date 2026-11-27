/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.runtime.Composable

/** Devuelve una función que abre el selector de archivos .json y entrega su texto a [onPicked]. */
@Composable
expect fun rememberThemeJsonPicker(onPicked: (String) -> Unit): () -> Unit

/** Devuelve una función que pide dónde guardar y escribe [content] en un archivo con ese nombre. */
@Composable
expect fun rememberThemeJsonSaver(): (fileName: String, content: String) -> Unit
