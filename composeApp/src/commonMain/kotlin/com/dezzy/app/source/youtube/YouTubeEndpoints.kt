/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.youtube

object YouTubeEndpoints {
    const val BASE_URL = "https://music.youtube.com/youtubei/v1/"
    const val BROWSE_URL = "${BASE_URL}browse?prettyPrint=false"
    const val SEARCH_URL = "${BASE_URL}search?prettyPrint=false"
    const val NEXT_URL = "${BASE_URL}next?prettyPrint=false"
    const val SUGGESTIONS_URL = "${BASE_URL}music/get_search_suggestions?prettyPrint=false"
    const val ACCOUNT_MENU_URL = "${BASE_URL}account/account_menu?prettyPrint=false"
    const val LIKE_URL = "${BASE_URL}like/like?prettyPrint=false"
    const val REMOVE_LIKE_URL = "${BASE_URL}like/removelike?prettyPrint=false"
    const val PLAYER_URL = "${BASE_URL}player?prettyPrint=false"
    const val YT_PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"

    const val CLIENT_NAME = "WEB_REMIX"
    const val CLIENT_VERSION = "1.20241118.01.00"
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    const val ORIGIN = "https://music.youtube.com"
    const val REFERER = "https://music.youtube.com/"

    // Filtros de búsqueda en YouTube Music (base64 crudo para cuerpo JSON)
    const val FILTER_SONGS = "EgWKAQIIAWoQEAMQBBAJEAoQBRAREBAQFQ=="
    const val FILTER_VIDEOS = "EgWKAQIQAWoQEAMQBBAJEAoQBRAREBAQFQ=="
    const val FILTER_ALBUMS = "EgWKAQIYAWoQEAMQBBAJEAoQBRAREBAQFQ=="
    const val FILTER_ARTISTS = "EgWKAQIgAWoQEAMQBBAJEAoQBRAREBAQFQ=="
    const val FILTER_PLAYLISTS = "EgWKAQIoAWoQEAMQBBAJEAoQBRAREBAQFQ=="

    const val BROWSE_ID_HOME = "FEmusic_home"
    const val BROWSE_ID_LIBRARY_PLAYLISTS = "FEmusic_liked_playlists"
    const val BROWSE_ID_CHARTS = "FEmusic_charts"
    const val CHARTS_PARAMS = "ggMGCgQIgAQ="
}
