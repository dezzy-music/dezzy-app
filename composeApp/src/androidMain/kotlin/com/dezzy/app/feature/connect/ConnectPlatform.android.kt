/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.connect

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import org.koin.core.scope.Scope

actual fun createConnectPlatform(scope: Scope): ConnectPlatform = AndroidConnectPlatform(scope.get<Context>().applicationContext)

private class AndroidConnectPlatform(private val context: Context) : ConnectPlatform {
    override val kind = DeviceKind.phone
    private var lock: WifiManager.MulticastLock? = null

    override fun deviceName(): String =
        runCatching { Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()
            ?.takeIf { it.isNotBlank() }?.take(60)
            ?: Build.MODEL.orEmpty().ifBlank { "Móvil" }.take(60)

    @SuppressLint("WifiManagerPotentialLeak")
    override fun setMulticast(enabled: Boolean) {
        runCatching {
            if (enabled) {
                if (lock == null) {
                    // Sin esto muchos móviles tiran los paquetes de difusión que llegan.
                    val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    lock = wifi?.createMulticastLock("dezzy-connect")?.apply {
                        setReferenceCounted(false)
                        acquire()
                    }
                }
            } else {
                lock?.takeIf { it.isHeld }?.release()
                lock = null
            }
        }
    }
}
