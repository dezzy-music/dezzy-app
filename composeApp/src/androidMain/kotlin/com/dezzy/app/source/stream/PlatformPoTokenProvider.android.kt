/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.stream

actual class PlatformPoTokenProvider actual constructor() {
    actual suspend fun getPoToken(binding: String): String {
        return PoToken.createColdStartToken(binding)
    }
}
