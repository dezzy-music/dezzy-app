/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.bubble

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil3.compose.AsyncImage
import com.dezzy.app.feature.player.PlayerController
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.roundToInt

/**
 * Burbuja flotante (Ajustes › Interfaz › Burbuja Flotante): al salir de la app con una canción
 * cargada aparece la carátula en un círculo arrastrable sobre las demás apps. Un toque despliega
 * los controles (anterior, reproducir/pausar, siguiente, abrir la app y cerrar); al soltarla se
 * pega al borde más cercano.
 */
class FloatingBubbleService : Service(), LifecycleOwner, SavedStateRegistryOwner, KoinComponent {
    private val player: PlayerController by inject()
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var view: ComposeView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedState.performAttach()
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = resources.displayMetrics
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.widthPixels - (80 * metrics.density).roundToInt()
            y = metrics.heightPixels / 3
        }
        val compose = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingBubbleService)
            setViewTreeSavedStateRegistryOwner(this@FloatingBubbleService)
            setContent {
                BubbleContent(
                    player = player,
                    onDrag = { dx, dy -> moveBy(dx, dy) },
                    onDragEnd = { snapToEdge() },
                    onOpenApp = { openApp() },
                    onClose = { stopSelf() },
                )
            }
        }
        view = compose
        runCatching { windowManager.addView(compose, params) }.onFailure { stopSelf(); return }
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    private fun moveBy(dx: Float, dy: Float) {
        val v = view ?: return
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        runCatching { windowManager.updateViewLayout(v, params) }
    }

    private fun snapToEdge() {
        val v = view ?: return
        val width = resources.displayMetrics.widthPixels
        val center = params.x + v.width / 2
        params.x = if (center < width / 2) 0 else width - v.width
        params.y = params.y.coerceIn(0, resources.displayMetrics.heightPixels - v.height)
        runCatching { windowManager.updateViewLayout(v, params) }
    }

    private fun openApp() {
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(it)
        }
        stopSelf()
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            runCatching { context.startService(Intent(context, FloatingBubbleService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, FloatingBubbleService::class.java)) }
        }
    }
}

@Composable
private fun BubbleContent(
    player: PlayerController,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
) {
    val state by player.state.collectAsState()
    val track = state.queue.current?.track
    var expanded by remember { mutableStateOf(false) }
    val spin = rememberInfiniteTransition(label = "bubbleSpin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "angle")

    Row(
        Modifier
            .padding(6.dp)
            .shadow(10.dp, RoundedCornerShape(40.dp))
            .clip(RoundedCornerShape(40.dp))
            .background(Color(0xEE141414))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier
                .size(60.dp)
                .pointerInputDrag(onDrag, onDragEnd) { expanded = !expanded },
            contentAlignment = Alignment.Center,
        ) {
            if (track?.thumbnail != null) {
                AsyncImage(
                    model = track.thumbnail,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(60.dp)
                        .rotate(if (state.isPlaying) angle else 0f)
                        .clip(CircleShape)
                        .border(2.dp, Color.White, CircleShape),
                )
            } else {
                Icon(Icons.Rounded.MusicNote, null, tint = Color.White)
            }
        }
        AnimatedVisibility(expanded, enter = expandHorizontally(), exit = shrinkHorizontally()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BubbleButton(Icons.Rounded.SkipPrevious, "Anterior") { player.previous() }
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = { player.togglePlay() }) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (state.isPlaying) "Pausar" else "Reproducir",
                            tint = Color.Black,
                        )
                    }
                }
                BubbleButton(Icons.Rounded.SkipNext, "Siguiente") { player.next() }
                BubbleButton(Icons.Rounded.OpenInNew, "Abrir Dezzy", onOpenApp)
                BubbleButton(Icons.Rounded.Close, "Cerrar burbuja", onClose)
            }
        }
    }
}

@Composable
private fun BubbleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(icon, label, tint = Color.White)
    }
}

/** Arrastrar mueve la ventana; un toque sin arrastre llama a [onTap]. */
private fun Modifier.pointerInputDrag(
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit,
): Modifier = this
    .pointerInput(Unit) {
        detectDragGestures(onDragEnd = onDragEnd, onDragCancel = onDragEnd) { change, amount ->
            change.consume()
            onDrag(amount.x, amount.y)
        }
    }
    .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
