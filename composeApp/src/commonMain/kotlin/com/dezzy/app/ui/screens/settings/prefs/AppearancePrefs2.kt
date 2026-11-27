/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

// Preferencias de Ajustes › Apariencia que aún no tenían clave propia.

// ── Tema ─────────────────────────────────────────────────────────────────────────────────────
/** Elegir una paleta aleatoria cada vez que arranca la app. */
val ThemeRandomAtStartKey = booleanPreferencesKey("theme_random_at_start")

// ── Fondo de la app ──────────────────────────────────────────────────────────────────────────
/** Enlace del pin de Pinterest que se usará de fondo. */
val PinterestPinKey = stringPreferencesKey("pinterestPin")
val WallpaperImageEnabledKey = booleanPreferencesKey("wallpaper_image_enabled")
val WallpaperVideoEnabledKey = booleanPreferencesKey("wallpaper_video_enabled")
/** Desenfoque del fondo de la app, en dp (0 = nítido). */
val WallpaperBlurKey = floatPreferencesKey("wallpaper_blur")
val WallpaperIdleFadeKey = booleanPreferencesKey("wallpaper_idle_fade")

// ── Reproductor ──────────────────────────────────────────────────────────────────────────────
val KeepScreenOnPlayerKey = booleanPreferencesKey("player_keep_screen_on")
val MiniPlayerDynamicColorKey = booleanPreferencesKey("mini_player_dynamic_color")
val HidePlayerThumbnailKey = booleanPreferencesKey("player_hide_thumbnail")
val SpinningThumbnailKey = booleanPreferencesKey("player_spinning_thumbnail")
val ShowCommentsButtonKey = booleanPreferencesKey("player_show_comments_button")
val CropThumbnails11Key = booleanPreferencesKey("player_crop_thumbnails_1_1")
/** Radio de las esquinas del reproductor en dp (0–32). */
val PlayerCornerRadiusKey = floatPreferencesKey("player_corner_radius")
/** "DEFAULT", "DYNAMIC" o "WHITE". */
val PlayerButtonColorsKey = stringPreferencesKey("player_button_colors")
val PlayerSwipeToChangeKey = booleanPreferencesKey("player_swipe_to_change")
/** Sensibilidad del deslizamiento del mini reproductor, en %. */
val MiniSwipeSensitivityKey = floatPreferencesKey("mini_swipe_sensitivity")

// ── Letras ───────────────────────────────────────────────────────────────────────────────────
val LyricsV2Key = booleanPreferencesKey("lyrics_v2")
/** "HEATMAP", "FADE" o "SCALE". */
val LyricsV2AnimKey = stringPreferencesKey("lyrics_v2_anim")
/** "APPLE", "FADE", "SLIDE" o "NONE". */
val LyricsAnimStyleKey = stringPreferencesKey("lyrics_anim_style")
val LyricsTapToSeekKey = booleanPreferencesKey("lyrics_tap_to_seek")
val LyricsAutoScrollKey = booleanPreferencesKey("lyrics_auto_scroll")
val LyricsAppleBlurKey = booleanPreferencesKey("lyrics_apple_blur")
val LyricsStandardBlurKey = booleanPreferencesKey("lyrics_standard_blur")
val LyricsHideStatusBarKey = booleanPreferencesKey("lyrics_hide_status_bar")
/** Interlineado de la letra: 1,0 a 2,0. */
val LyricsLineSpacingKey = floatPreferencesKey("lyrics_line_spacing")

// ── Varios ───────────────────────────────────────────────────────────────────────────────────
/** "HOME", "SEARCH", "LIBRARY" o "ROOMS". */
val DefaultTabKey = stringPreferencesKey("default_tab")
/** "PLAYLISTS", "ALBUMS", "ARTISTS", "SONGS" o "GENRES". */
val DefaultLibraryTabKey = stringPreferencesKey("default_library_tab")
val FloatingBubbleKey = booleanPreferencesKey("floating_bubble")
val HomeCategoryChipsKey = booleanPreferencesKey("home_category_chips")
val LibraryTagsKey = booleanPreferencesKey("library_tags")
val SwipeSongActionsKey = booleanPreferencesKey("swipe_song_actions")
/** "SMALL" o "LARGE". */
val GridCellSizeKey = stringPreferencesKey("grid_cell_size")

// ── Auto listas de reproducción ──────────────────────────────────────────────────────────────
val ShowLikedAutoPlaylistKey = booleanPreferencesKey("auto_playlist_liked")
val ShowDownloadedAutoPlaylistKey = booleanPreferencesKey("auto_playlist_downloaded")
val ShowTopAutoPlaylistKey = booleanPreferencesKey("auto_playlist_top")
val ShowCachedAutoPlaylistKey = booleanPreferencesKey("auto_playlist_cached")

// ── Perfil del artista ───────────────────────────────────────────────────────────────────────
val ArtistVideoThumbnailsKey = booleanPreferencesKey("artist_video_thumbnails")
val ArtistVideoBackgroundKey = booleanPreferencesKey("artist_video_background")
