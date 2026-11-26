/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.connect

import org.koin.core.scope.Scope

actual fun createConnectPlatform(scope: Scope): ConnectPlatform = object : ConnectPlatform {
    override val kind = DeviceKind.pc
    override fun deviceName(): String =
        runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()
            ?.takeIf { it.isNotBlank() }?.take(60)
            ?: System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }?.take(60)
            ?: "Ordenador"
}
