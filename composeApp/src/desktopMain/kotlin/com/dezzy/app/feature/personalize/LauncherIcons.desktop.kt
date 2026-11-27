/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.personalize

private object NoLauncherIcons : LauncherIcons {
    override val supported = false
    override fun activeAlias() = "d_0"
    override fun apply(alias: String) = false
}

actual fun launcherIcons(): LauncherIcons = NoLauncherIcons
