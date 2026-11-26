/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val videoId: String?,
    val title: String,
    val artists: String,
    val album: String? = null,
    val thumbnail: String? = null,
    val durationSeconds: Long? = null,
    val spotifyId: String? = null,
    val musicVideoType: String? = null
)
