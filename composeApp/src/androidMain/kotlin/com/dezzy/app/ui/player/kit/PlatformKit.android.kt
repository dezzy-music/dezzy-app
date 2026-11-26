/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.kit

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.BatteryManager
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform

actual suspend fun loadArtPalette(url: String?, maxColors: Int): ArtPalette? {
    if (url.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val context = KoinPlatform.getKoin().get<Context>()
            val request = ImageRequest.Builder(context).data(url).size(256).allowHardware(false).build()
            val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@runCatching null
            val p = Palette.from(result.image.toBitmap()).maximumColorCount(maxColors).generate()
            fun Palette.Swatch?.map() = this?.let { Swatch(it.rgb, it.population) }
            ArtPalette(
                vibrantSwatch = p.vibrantSwatch.map(),
                darkVibrantSwatch = p.darkVibrantSwatch.map(),
                lightVibrantSwatch = p.lightVibrantSwatch.map(),
                mutedSwatch = p.mutedSwatch.map(),
                darkMutedSwatch = p.darkMutedSwatch.map(),
                lightMutedSwatch = p.lightMutedSwatch.map(),
                dominantSwatch = p.dominantSwatch.map(),
                swatches = p.swatches.map { Swatch(it.rgb, it.population) },
            )
        }.getOrNull()
    }
}

internal actual fun createSpectrumSource(audioSessionId: Int, onFft: (ByteArray) -> Unit): SpectrumSource? {
    val visualizer = Visualizer(audioSessionId)
    return try {
        visualizer.enabled = false
        visualizer.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
        visualizer.setDataCaptureListener(
            object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit
                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft != null) onFft(fft)
                }
            },
            Visualizer.getMaxCaptureRate(),
            false,
            true,
        )
        visualizer.enabled = true
        object : SpectrumSource {
            override fun release() {
                runCatching { visualizer.enabled = false }
                visualizer.release()
            }
        }
    } catch (e: Exception) {
        visualizer.release()
        null
    }
}

@Composable
actual fun rememberRecordAudioPermission(): RecordAudioPermission {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    return RecordAudioPermission(granted) { launcher.launch(Manifest.permission.RECORD_AUDIO) }
}

@Composable
actual fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    var saving by remember { mutableStateOf((context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                saving = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return saving
}

@Composable
actual fun rememberBatteryLevel(): Int? {
    val context = LocalContext.current
    var level by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val l = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val s = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                level = if (l >= 0 && s > 0) l * 100 / s else null
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return level
}

@Composable
actual fun rememberShareText(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

@Composable
actual fun rememberPhoneDeviceName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching { android.provider.Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: android.os.Build.MODEL.takeIf { it.isNotBlank() }
            ?: "Este teléfono"
    }
}
