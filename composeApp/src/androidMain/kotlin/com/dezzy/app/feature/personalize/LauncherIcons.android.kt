/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.personalize

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import org.koin.mp.KoinPlatform

/** Los alias están declarados en el manifest con el nombre completo `com.dezzy.app.AppIcon_<forma>_<paleta>`. */
private class AndroidLauncherIcons(private val context: Context) : LauncherIcons {
    override val supported = true

    private fun component(shape: AppIconShape, palette: Int) =
        ComponentName(context.packageName, "com.dezzy.app.AppIcon_${shape.id}_$palette")

    override fun activeAlias(): String {
        val pm = context.packageManager
        for (shape in AppIconShape.entries) for (i in AppIcon.PALETTES.indices) {
            val state = runCatching { pm.getComponentEnabledSetting(component(shape, i)) }.getOrNull() ?: continue
            val enabled = when (state) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> shape == AppIconShape.D_PLAY && i == 0
                else -> false
            }
            if (enabled) return "${shape.id}_$i"
        }
        return "d_0"
    }

    /** Primero activa el nuevo alias y luego apaga los demás, para que nunca quede la app sin icono. */
    override fun apply(alias: String): Boolean = runCatching {
        val pm = context.packageManager
        val all = AppIconShape.entries.flatMap { s -> AppIcon.PALETTES.indices.map { s to it } }
        val (ts, ti) = all.first { "${it.first.id}_${it.second}" == alias }
        pm.setComponentEnabledSetting(
            component(ts, ti),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        for ((s, i) in all) {
            if (s == ts && i == ti) continue
            pm.setComponentEnabledSetting(
                component(s, i),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }.isSuccess
}

private val instance: LauncherIcons by lazy { AndroidLauncherIcons(KoinPlatform.getKoin().get<Context>()) }

actual fun launcherIcons(): LauncherIcons = instance
