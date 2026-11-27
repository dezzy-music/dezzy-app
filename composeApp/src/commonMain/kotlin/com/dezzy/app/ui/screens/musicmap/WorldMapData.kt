/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.musicmap

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.dezzy.app.feature.musicmap.CountryNames

@Immutable
data class CountryMusicProfile(
    val countryCode: String,
    val countryName: String,
    val emoji: String,
    val dominantGenres: List<String>,
    val accentColor: Color,
    val artistHighlight: String? = null,
    /** Coordenadas normalizadas (0..1) para colocar un punto sobre un mapamundi plano. */
    val mapX: Float,
    val mapY: Float,
)

private fun profile(
    code: String,
    genres: List<String>,
    color: Long,
    artist: String?,
    x: Float,
    y: Float,
) = CountryMusicProfile(code, CountryNames.name(code), CountryNames.flag(code), genres, Color(color), artist, x, y)

object WorldMapData {

    val countries: List<CountryMusicProfile> = listOf(
        profile("US", listOf("Pop", "Hip-Hop", "R&B", "Country"), 0xFF4A90E2, "Taylor Swift", 0.155f, 0.33f),
        profile("MX", listOf("Regional mexicano", "Pop latino", "Banda"), 0xFF27AE60, "Bad Bunny", 0.19f, 0.43f),
        profile("BR", listOf("Funk carioca", "Sertanejo", "MPB", "Pagode"), 0xFFF39C12, "Anitta", 0.30f, 0.60f),
        profile("GB", listOf("Pop", "Grime", "Indie rock"), 0xFF8E44AD, "Adele", 0.435f, 0.22f),
        profile("DE", listOf("Electrónica", "Pop", "Schlager"), 0xFF2C3E50, "Rammstein", 0.48f, 0.24f),
        profile("FR", listOf("Pop francés", "Electrónica", "Hip-Hop"), 0xFF2980B9, "David Guetta", 0.455f, 0.26f),
        profile("ES", listOf("Flamenco", "Reguetón", "Pop latino"), 0xFFE74C3C, "Rosalía", 0.43f, 0.29f),
        profile("IT", listOf("Pop italiano", "Ópera", "Electrónica"), 0xFF27AE60, "Måneskin", 0.475f, 0.28f),
        profile("JP", listOf("J-Pop", "Música de anime", "City pop"), 0xFFE74C3C, "YOASOBI", 0.815f, 0.33f),
        profile("KR", listOf("K-Pop", "K-Hip-Hop", "Balada"), 0xFF3498DB, "BTS", 0.795f, 0.35f),
        profile("IN", listOf("Bollywood", "Punjabi pop", "Clásica"), 0xFFFF6B35, "Arijit Singh", 0.66f, 0.38f),
        profile("AU", listOf("Pop", "Rock", "Electrónica"), 0xFFFFBE0B, "Sia", 0.84f, 0.68f),
        profile("CA", listOf("Pop", "Hip-Hop", "Indie"), 0xFFE74C3C, "Drake", 0.175f, 0.25f),
        profile("AR", listOf("Tango", "Rock nacional", "Cumbia"), 0xFF74B9FF, "Bizarrap", 0.265f, 0.74f),
        profile("CO", listOf("Vallenato", "Salsa", "Reguetón"), 0xFFFDCB6E, "J Balvin", 0.245f, 0.53f),
        profile("CL", listOf("Pop", "Rock", "Cumbia"), 0xFFD63031, "Mon Laferte", 0.245f, 0.70f),
        profile("SE", listOf("Pop", "Electrónica", "Metal"), 0xFF0984E3, "ABBA", 0.48f, 0.18f),
        profile("NG", listOf("Afrobeats", "Afropop", "Highlife"), 0xFF00B894, "Burna Boy", 0.47f, 0.48f),
        profile("ZA", listOf("Amapiano", "Kwaito", "Afropop"), 0xFFFDCB6E, "Black Coffee", 0.525f, 0.63f),
        profile("TR", listOf("Pop turco", "Arabesk", "Electrónica"), 0xFFE17055, "Tarkan", 0.555f, 0.30f),
        profile("PL", listOf("Pop", "Hip-Hop", "Electrónica"), 0xFFFF7675, "Kayah", 0.51f, 0.23f),
        profile("PT", listOf("Fado", "Pop", "Electrónica"), 0xFF6C5CE7, "Salvador Sobral", 0.415f, 0.29f),
        profile("ID", listOf("Dangdut", "Pop", "Influencia K-Pop"), 0xFFE17055, "Agnez Mo", 0.775f, 0.52f),
        profile("PH", listOf("OPM", "Pop", "BPop"), 0xFF0984E3, "Ben&Ben", 0.805f, 0.44f),
        profile("TH", listOf("T-Pop", "Luk thung", "Electrónica"), 0xFF6C5CE7, "Billkin", 0.752f, 0.42f),
        profile("RU", listOf("Pop", "Electrónica", "Rock"), 0xFF74B9FF, "Monetochka", 0.59f, 0.20f),
        profile("NL", listOf("Electrónica", "Pop", "Drum and bass"), 0xFFFF7675, "Tiësto", 0.46f, 0.22f),
        profile("SA", listOf("Khaliji", "Pop árabe", "Maqam"), 0xFF00B894, "Mohammed Abdo", 0.59f, 0.37f),
        profile("EG", listOf("Shaabi", "Pop árabe", "Árabe clásica"), 0xFFFDCB6E, "Amr Diab", 0.535f, 0.36f),
        profile("GH", listOf("Highlife", "Hiplife", "Afrobeats"), 0xFF55EFC4, "Sarkodie", 0.455f, 0.48f),
        profile("VE", listOf("Salsa", "Reguetón", "Vallenato"), 0xFF74B9FF, "Nacho", 0.255f, 0.50f),
        profile("PE", listOf("Cumbia", "Chicha", "Salsa"), 0xFFFF7675, "Gian Marco", 0.24f, 0.60f),
        profile("CN", listOf("C-Pop", "Mandopop", "Tradicional"), 0xFFE17055, "Jay Chou", 0.765f, 0.32f),
        profile("MY", listOf("Pop malayo", "R&B", "Electrónica"), 0xFF0984E3, "Yuna", 0.762f, 0.47f),
        profile("VN", listOf("V-Pop", "Bolero", "EDM"), 0xFFE17055, "Sơn Tùng M-TP", 0.758f, 0.44f),
    )

    val countryByCode: Map<String, CountryMusicProfile> = countries.associateBy { it.countryCode }
}

/**
 * País con chart real de YouTube Music que se pinta en el mapa. [minZoom] oculta las píldoras de
 * países pequeños o muy juntos hasta que se acerca el mapa.
 */
internal data class MapCountry(
    val code: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val minZoom: Double = 0.0,
)

internal val MAP_COUNTRIES = listOf(
    MapCountry("US", "Estados Unidos", 39.8, -98.6),
    MapCountry("MX", "México", 23.6, -102.5),
    MapCountry("ES", "España", 40.4, -3.7),
    MapCountry("GB", "Reino Unido", 54.0, -2.0),
    MapCountry("BR", "Brasil", -14.2, -51.9),
    MapCountry("AR", "Argentina", -38.4, -63.6),
    MapCountry("CO", "Colombia", 4.6, -74.1),
    MapCountry("CL", "Chile", -35.7, -71.5),
    MapCountry("FR", "Francia", 46.6, 2.2),
    MapCountry("DE", "Alemania", 51.2, 10.4),
    MapCountry("IT", "Italia", 41.9, 12.6),
    MapCountry("JP", "Japón", 36.2, 138.3),
    MapCountry("KR", "Corea del Sur", 35.9, 127.8),
    MapCountry("IN", "India", 20.6, 78.9),
    MapCountry("CA", "Canadá", 56.1, -106.3),
    MapCountry("AU", "Australia", -25.3, 133.8),
    MapCountry("PE", "Perú", -9.2, -75.0),
    MapCountry("EC", "Ecuador", -1.8, -78.2, minZoom = 4.0),
    MapCountry("BO", "Bolivia", -16.3, -63.6, minZoom = 4.0),
    MapCountry("PY", "Paraguay", -23.4, -58.4, minZoom = 4.0),
    MapCountry("UY", "Uruguay", -32.5, -55.8, minZoom = 4.0),
    MapCountry("GT", "Guatemala", 15.8, -90.2, minZoom = 4.0),
    MapCountry("SV", "El Salvador", 13.8, -88.9, minZoom = 5.0),
    MapCountry("HN", "Honduras", 15.2, -86.2, minZoom = 5.0),
    MapCountry("CR", "Costa Rica", 9.7, -83.8, minZoom = 4.5),
    MapCountry("DO", "República Dominicana", 18.7, -70.2, minZoom = 4.0),
    MapCountry("PT", "Portugal", 39.4, -8.2, minZoom = 4.0),
    MapCountry("NL", "Países Bajos", 52.1, 5.3, minZoom = 4.0),
    MapCountry("SE", "Suecia", 62.0, 15.0),
    MapCountry("PL", "Polonia", 51.9, 19.1, minZoom = 4.0),
    MapCountry("UA", "Ucrania", 48.4, 31.2, minZoom = 4.0),
    MapCountry("RU", "Rusia", 61.5, 95.0),
    MapCountry("TR", "Turquía", 39.0, 35.2),
    MapCountry("EG", "Egipto", 26.8, 30.8),
    MapCountry("SA", "Arabia Saudí", 23.9, 45.1, minZoom = 3.5),
    MapCountry("NG", "Nigeria", 9.1, 8.7),
    MapCountry("ZA", "Sudáfrica", -30.6, 22.9),
    MapCountry("PH", "Filipinas", 12.9, 121.8),
    MapCountry("ID", "Indonesia", -0.8, 113.9),
    MapCountry("NZ", "Nueva Zelanda", -40.9, 174.9),
)

/** El chart mundial: respaldo cuando ningún país devuelve nada. */
internal val MAP_WORLD = MapCountry("ZZ", "Mundial", 15.0, 0.0)

internal val MAP_COUNTRY_BY_CODE: Map<String, MapCountry> = (MAP_COUNTRIES + MAP_WORLD).associateBy { it.code }
