/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class MusicItemType {
    SONG,
    VIDEO,
    ALBUM,
    PLAYLIST,
    ARTIST
}

@Serializable
data class MusicItem(
    val id: String,
    val type: MusicItemType,
    val title: String,
    val subtitle: String,
    val thumbnail: String? = null
)

@Serializable
data class MusicSection(
    val title: String,
    val strapline: String? = null,
    val items: List<MusicItem> = emptyList()
)

@Serializable
data class SearchResult(
    val songs: List<Track> = emptyList(),
    val albums: List<MusicItem> = emptyList(),
    val artists: List<MusicItem> = emptyList(),
    val playlists: List<MusicItem> = emptyList()
)
