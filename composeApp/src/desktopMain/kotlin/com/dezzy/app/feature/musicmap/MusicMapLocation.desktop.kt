/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.musicmap

import org.koin.core.scope.Scope

actual fun createMapLocationSource(scope: Scope): MapLocationSource = object : MapLocationSource {
    override fun hasPermission(): Boolean = false
    override suspend fun current(): Pair<Double, Double>? = null
}
