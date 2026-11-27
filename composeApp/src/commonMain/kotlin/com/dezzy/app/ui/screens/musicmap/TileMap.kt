/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.musicmap

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * Mapa de teselas (estilo «slippy map») hecho en Compose: proyección Web Mercator, teselas de
 * OpenStreetMap cargadas con Coil, arrastrar y pellizcar para mover y hacer zoom. Sustituye a
 * osmdroid, que sólo existe en Android.
 */
@Stable
internal class TileMapState(lat: Double, lng: Double, zoom: Double) {
    var zoom by mutableDoubleStateOf(zoom)
        private set
    var centerX by mutableDoubleStateOf(mercatorX(lng))
        private set
    var centerY by mutableDoubleStateOf(mercatorY(lat))
        private set
    var width by mutableIntStateOf(0)
        internal set
    var height by mutableIntStateOf(0)
        internal set
    var density: Float = 1f

    /** Ancho del mundo entero en píxeles al zoom actual. */
    val worldPx: Double get() = TILE_DP * density * 2.0.pow(this.zoom)

    fun toScreen(lat: Double, lng: Double): Offset {
        val w = worldPx
        return Offset(
            ((mercatorX(lng) - centerX) * w + width / 2.0).toFloat(),
            ((mercatorY(lat) - centerY) * w + height / 2.0).toFloat(),
        )
    }

    /** Centra el mapa en un punto (y opcionalmente cambia el zoom). */
    fun moveTo(lat: Double, lng: Double, zoom: Double? = null) {
        if (zoom != null) this.zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        centerX = mercatorX(lng)
        centerY = mercatorY(lat)
    }

    fun transform(centroid: Offset, pan: Offset, zoomFactor: Float) {
        val old = worldPx
        val wx = centerX + (centroid.x - width / 2.0) / old
        val wy = centerY + (centroid.y - height / 2.0) / old
        if (zoomFactor != 1f) {
            zoom = (zoom + ln(zoomFactor.toDouble()) / ln(2.0)).coerceIn(MIN_ZOOM, MAX_ZOOM)
        }
        val now = worldPx
        centerX = (wx - (centroid.x + pan.x - width / 2.0) / now).coerceIn(0.0, 1.0)
        centerY = (wy - (centroid.y + pan.y - height / 2.0) / now).coerceIn(0.0, 1.0)
    }

    companion object {
        const val TILE_DP = 256.0
        const val MIN_ZOOM = 2.5
        const val MAX_ZOOM = 17.0

        fun mercatorX(lng: Double): Double = (lng + 180.0) / 360.0

        fun mercatorY(lat: Double): Double {
            val l = lat.coerceIn(-85.05, 85.05) * PI / 180.0
            return 0.5 - ln(tan(PI / 4 + l / 2)) / (2 * PI)
        }
    }
}

private val NIGHT = floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)

private class MarkerSlot(val x: Float, val y: Float) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@MarkerSlot
}

/** Un elemento del mapa anclado a una coordenada; su centro cae sobre el punto. */
@Composable
internal fun MapMarker(state: TileMapState, lat: Double, lng: Double, content: @Composable () -> Unit) {
    val p = state.toScreen(lat, lng)
    Box(Modifier.then(MarkerSlot(p.x, p.y))) { content() }
}

@Composable
internal fun TileMap(
    state: TileMapState,
    dark: Boolean,
    modifier: Modifier = Modifier,
    markers: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    state.density = density.density
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .clipToBounds()
            .background(if (dark) Color(0xFF1B1F24) else Color(0xFFAAD3DF))
            .onSizeChanged {
                state.width = it.width
                state.height = it.height
            }
            .pointerInput(state) {
                detectTransformGestures { centroid, pan, zoom, _ -> state.transform(centroid, pan, zoom) }
            },
    ) {
        if (state.width > 0 && state.height > 0) {
            val z = floor(state.zoom).toInt().coerceIn(0, 19)
            val n = 1 shl z
            val world = state.worldPx
            val tilePx = world / n
            val leftW = state.centerX - state.width / 2.0 / world
            val rightW = state.centerX + state.width / 2.0 / world
            val topW = state.centerY - state.height / 2.0 / world
            val bottomW = state.centerY + state.height / 2.0 / world
            val x0 = floor(leftW * n).toInt().coerceAtLeast(0)
            val x1 = floor(rightW * n).toInt().coerceAtMost(n - 1)
            val y0 = floor(topW * n).toInt().coerceAtLeast(0)
            val y1 = floor(bottomW * n).toInt().coerceAtMost(n - 1)
            val sizeDp = with(density) { (tilePx.roundToInt() + 1).toDp() }
            val filter = if (dark) ColorFilter.colorMatrix(ColorMatrix(NIGHT)) else null
            for (ty in y0..y1) {
                for (tx in x0..x1) {
                    val sx = ((tx.toDouble() / n - state.centerX) * world + state.width / 2.0).roundToInt()
                    val sy = ((ty.toDouble() / n - state.centerY) * world + state.height / 2.0).roundToInt()
                    key(z, tx, ty) {
                        AsyncImage(
                            model = "https://tile.openstreetmap.org/$z/$tx/$ty.png",
                            contentDescription = null,
                            colorFilter = filter,
                            modifier = Modifier.offset { IntOffset(sx, sy) }.size(sizeDp),
                        )
                    }
                }
            }
        }
        Layout(content = markers, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val placed = measurables.map { it to it.measure(loose) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                for ((m, p) in placed) {
                    val slot = m.parentData as? MarkerSlot ?: continue
                    p.place((slot.x - p.width / 2f).roundToInt(), (slot.y - p.height / 2f).roundToInt())
                }
            }
        }
        Text(
            "© OpenStreetMap",
            fontSize = 10.sp,
            color = if (dark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 2.dp),
        )
    }
}
