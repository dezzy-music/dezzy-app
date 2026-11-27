/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberOpenDefaultLinksSettings(): (() -> Unit)? {
    val context = LocalContext.current
    return remember(context) {
        {
            val pkg = Uri.parse("package:${context.packageName}")
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkg)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            }
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
