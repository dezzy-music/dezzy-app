/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberFileSaver(mime: String): (fileName: String, content: String) -> Unit {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<String>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri: Uri? ->
        val content = pending[0]
        if (uri != null && content != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(content.encodeToByteArray()) }
            }
        }
        pending[0] = null
    }
    return remember(launcher) {
        { fileName, content ->
            pending[0] = content
            launcher.launch(fileName)
        }
    }
}

@Composable
actual fun rememberFilePicker(mimes: List<String>, onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.getOrNull()
            if (text != null) callback(text)
        }
    }
    val types = remember(mimes) { mimes.toTypedArray() }
    return remember(launcher, types) { { launcher.launch(types) } }
}
