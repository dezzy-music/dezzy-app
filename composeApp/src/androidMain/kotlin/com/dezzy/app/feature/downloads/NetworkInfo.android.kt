/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.downloads

import android.content.Context
import android.net.ConnectivityManager
import org.koin.mp.KoinPlatform

actual fun isMeteredNetwork(): Boolean = runCatching {
    val context = KoinPlatform.getKoin().get<Context>()
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    cm.isActiveNetworkMetered
}.getOrDefault(false)
