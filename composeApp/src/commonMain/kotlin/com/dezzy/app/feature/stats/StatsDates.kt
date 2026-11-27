/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.stats

import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Cuentas de calendario sin depender de la API de fechas: los días se numeran desde 1970-01-01
 * (día 0) en hora local, que es lo que agrupan las consultas de Stats.sq con su desplazamiento.
 */
object StatsDates {
    const val DAY_MS = 86_400_000L

    val MONTHS_SHORT = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    val MONTHS_LONG = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )

    /** Desplazamiento de la hora local respecto a UTC, en ms, a [nowMs]. */
    fun offsetMs(nowMs: Long = Clock.System.now().toEpochMilliseconds()): Long =
        TimeZone.currentSystemDefault().offsetAt(Instant.fromEpochMilliseconds(nowMs)).totalSeconds * 1000L

    fun localDay(ms: Long, offsetMs: Long): Long = (ms + offsetMs).floorDiv(DAY_MS)

    /** Inicio (UTC, en ms) del día local numerado [day]. */
    fun dayStartMs(day: Long, offsetMs: Long): Long = day * DAY_MS - offsetMs

    /** 0 = lunes … 6 = domingo. */
    fun dayOfWeek(day: Long): Int = (day + 3).mod(7L).toInt()

    /** (año, mes 1 a 12, día del mes) de un día numerado. */
    fun civil(day: Long): Triple<Int, Int, Int> {
        val z = day + 719_468
        val era = (if (z >= 0) z else z - 146_096) / 146_097
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val y = yoe + era * 400 + if (m <= 2) 1 else 0
        return Triple(y.toInt(), m.toInt(), d.toInt())
    }

    /** Día numerado de (año, mes 1 a 12, día). */
    fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + if (month > 2) -3 else 9).toLong()
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }

    fun currentYear(nowMs: Long = Clock.System.now().toEpochMilliseconds()): Int =
        civil(localDay(nowMs, offsetMs(nowMs))).first
}
