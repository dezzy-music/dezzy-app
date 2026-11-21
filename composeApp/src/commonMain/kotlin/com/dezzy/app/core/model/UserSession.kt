/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class SessionProvider {
    YOUTUBE,
    SPOTIFY
}

@Serializable
data class UserSession(
    val provider: SessionProvider,
    val name: String? = null,
    val handle: String? = null,
    val avatarUrl: String? = null,
    val connectedAtEpochMs: Long = 0L,
    val isValid: Boolean = true
)
