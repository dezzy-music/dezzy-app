/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.social

import androidx.compose.runtime.Composable

/** Qué tipo de archivo multimedia se elige. */
enum class PickKind { IMAGE, VIDEO }

/**
 * Selector de foto o video del sistema. Devuelve una función que lo abre; [onPicked] recibe el
 * origen como texto (una URI en Android, una ruta en el escritorio), tal como lo esperan
 * `P2PSocial.compressPhoto(source)`, `avatarDataUri(source)` y `prepareBannerVideo(source)`.
 */
@Composable
expect fun rememberMediaPicker(kind: PickKind, onPicked: (source: String) -> Unit): () -> Unit

/**
 * Pide permisos de ejecución (Android). Devuelve una función que recibe los permisos a pedir;
 * [onResult] dice si se concedieron todos. En el escritorio no hace nada y responde `true`.
 */
@Composable
expect fun rememberPermissionRequester(onResult: (granted: Boolean) -> Unit): (permissions: List<String>) -> Unit

/** Mientras está en pantalla, bloquea capturas y grabaciones de pantalla (FLAG_SECURE en Android). */
@Composable
expect fun SecureWindow()
