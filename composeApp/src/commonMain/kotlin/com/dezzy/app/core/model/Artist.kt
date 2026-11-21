/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Artist(
    val id: String,
    val name: String,
    val thumbnail: String? = null,
    val listeners: String? = null,
    val description: String? = null,
    val songs: List<Track> = emptyList(),
    val subscribed: Boolean? = null
)
