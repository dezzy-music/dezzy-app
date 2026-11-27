/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.personalize

/**
 * Cambio del icono del launcher. En Android son `<activity-alias>` del manifest (uno por forma y paleta,
 * llamados `com.dezzy.app.AppIcon_<forma>_<paleta>`, p. ej. `AppIcon_d_0`, `AppIcon_star_8`); sólo uno
 * está activo. En escritorio no existe esa idea y [supported] es false.
 */
interface LauncherIcons {
    val supported: Boolean

    /** Alias activo ahora mismo en el sistema («d_0» si nunca se cambió). */
    fun activeAlias(): String

    /** Activa [alias] («d_3», «star_8»…) y apaga los demás. true si salió bien. */
    fun apply(alias: String): Boolean
}

expect fun launcherIcons(): LauncherIcons
