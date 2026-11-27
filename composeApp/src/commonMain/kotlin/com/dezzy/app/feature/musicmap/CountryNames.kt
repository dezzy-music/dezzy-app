/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.musicmap

/** Nombres de país en español y banderas, escritos a mano (sin `java.util.Locale`, que no existe en común). */
object CountryNames {
    /** Código del chart mundial. */
    const val GLOBAL = "ZZ"

    private val names: Map<String, String> = mapOf(
        "AR" to "Argentina", "AU" to "Australia", "AT" to "Austria", "BE" to "Bélgica", "BO" to "Bolivia",
        "BR" to "Brasil", "BG" to "Bulgaria", "CA" to "Canadá", "CL" to "Chile", "CO" to "Colombia",
        "CR" to "Costa Rica", "HR" to "Croacia", "CZ" to "Chequia", "DK" to "Dinamarca", "DO" to "República Dominicana",
        "EC" to "Ecuador", "EG" to "Egipto", "SV" to "El Salvador", "EE" to "Estonia", "FI" to "Finlandia",
        "FR" to "Francia", "DE" to "Alemania", "GR" to "Grecia", "GT" to "Guatemala", "HN" to "Honduras",
        "HK" to "Hong Kong", "HU" to "Hungría", "IS" to "Islandia", "IN" to "India", "ID" to "Indonesia",
        "IE" to "Irlanda", "IL" to "Israel", "IT" to "Italia", "JP" to "Japón", "KE" to "Kenia",
        "KR" to "Corea del Sur", "LV" to "Letonia", "LT" to "Lituania", "LU" to "Luxemburgo", "MY" to "Malasia",
        "MX" to "México", "MA" to "Marruecos", "NL" to "Países Bajos", "NZ" to "Nueva Zelanda", "NI" to "Nicaragua",
        "NG" to "Nigeria", "NO" to "Noruega", "PA" to "Panamá", "PY" to "Paraguay", "PE" to "Perú",
        "PH" to "Filipinas", "PL" to "Polonia", "PT" to "Portugal", "PR" to "Puerto Rico", "RO" to "Rumanía",
        "RU" to "Rusia", "SA" to "Arabia Saudita", "RS" to "Serbia", "SG" to "Singapur", "SK" to "Eslovaquia",
        "SI" to "Eslovenia", "ZA" to "Sudáfrica", "ES" to "España", "SE" to "Suecia", "CH" to "Suiza",
        "TW" to "Taiwán", "TH" to "Tailandia", "TR" to "Turquía", "UA" to "Ucrania", "AE" to "Emiratos Árabes Unidos",
        "GB" to "Reino Unido", "US" to "Estados Unidos", "UY" to "Uruguay", "VE" to "Venezuela", "VN" to "Vietnam",
        "TZ" to "Tanzania", "UG" to "Uganda", "ZW" to "Zimbabue", "GH" to "Ghana", "SN" to "Senegal",
        "CI" to "Costa de Marfil", "TN" to "Túnez", "DZ" to "Argelia", "JO" to "Jordania", "KW" to "Kuwait",
        "LB" to "Líbano", "QA" to "Catar", "PK" to "Pakistán", "BD" to "Bangladés", "LK" to "Sri Lanka",
        "NP" to "Nepal", "KZ" to "Kazajistán", "BY" to "Bielorrusia", "AZ" to "Azerbaiyán", "GE" to "Georgia",
        "AM" to "Armenia", "CY" to "Chipre", "MT" to "Malta", "BA" to "Bosnia y Herzegovina", "MK" to "Macedonia del Norte",
        "AL" to "Albania", "ME" to "Montenegro", "MD" to "Moldavia", "CM" to "Camerún", "AO" to "Angola",
        "MZ" to "Mozambique", "ET" to "Etiopía", "JM" to "Jamaica", "TT" to "Trinidad y Tobago", "CU" to "Cuba",
        "HT" to "Haití", "BS" to "Bahamas", "BB" to "Barbados", "MM" to "Birmania", "KH" to "Camboya",
        "LA" to "Laos", "MN" to "Mongolia", "UZ" to "Uzbekistán", "IQ" to "Irak", "OM" to "Omán",
        "BH" to "Baréin", "LY" to "Libia", "SD" to "Sudán", "ZM" to "Zambia", "BW" to "Botsuana",
        "NA" to "Namibia", "RW" to "Ruanda", "MU" to "Mauricio", "MG" to "Madagascar", "FJ" to "Fiyi",
    )

    /** Nombre del país en español («MX» → «México»); «ZZ» es «Mundial». */
    fun name(code: String): String {
        val c = code.uppercase()
        return if (c == GLOBAL) "Mundial" else names[c] ?: c
    }

    /** Bandera como emoji; «ZZ» es un globo. */
    fun flag(code: String): String {
        val c = code.uppercase()
        if (c == GLOBAL) return "🌐"
        if (c.length != 2 || c.any { it !in 'A'..'Z' }) return "🌐"
        val base = 0x1F1E6
        val sb = StringBuilder()
        for (ch in c) appendCodePoint(sb, base + (ch - 'A'))
        return sb.toString()
    }

    private fun appendCodePoint(sb: StringBuilder, cp: Int) {
        val v = cp - 0x10000
        sb.append((0xD800 + (v shr 10)).toChar())
        sb.append((0xDC00 + (v and 0x3FF)).toChar())
    }

    /** Países con chart conocido (sin el mundial). */
    val charted: List<String> get() = names.keys.toList()

    /** Minúsculas sin acentos ni signos: «Beyoncé» → «beyonce». Sólo letras latinas comunes. */
    fun fold(text: String): String {
        val sb = StringBuilder(text.length)
        var lastSpace = true
        for (raw in text.lowercase()) {
            val c = when (raw) {
                'á', 'à', 'ä', 'â', 'ã', 'å' -> 'a'
                'é', 'è', 'ë', 'ê' -> 'e'
                'í', 'ì', 'ï', 'î' -> 'i'
                'ó', 'ò', 'ö', 'ô', 'õ', 'ø' -> 'o'
                'ú', 'ù', 'ü', 'û' -> 'u'
                'ñ' -> 'n'
                'ç' -> 'c'
                else -> raw
            }
            if (c in 'a'..'z' || c in '0'..'9') {
                sb.append(c)
                lastSpace = false
            } else if (!lastSpace) {
                sb.append(' ')
                lastSpace = true
            }
        }
        return sb.toString().trim()
    }
}
