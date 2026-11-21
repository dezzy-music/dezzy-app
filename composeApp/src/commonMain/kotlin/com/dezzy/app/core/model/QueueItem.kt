/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class QueueItem(
    val queueEntryId: String,
    val track: Track,
    val addedAtEpochMs: Long = 0L
)
