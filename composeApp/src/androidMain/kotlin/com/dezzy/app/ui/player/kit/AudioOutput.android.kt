/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.kit

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter2
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private data class Outputs(val external: String?, val bluetooth: List<String>)

private fun readOutputs(audio: AudioManager): Outputs {
    val devices = runCatching { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrDefault(emptyList())
    val bt = devices.filter {
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= 31 && (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
    }
    val btNames = bt.mapNotNull { it.productName?.toString()?.takeIf { n -> n.isNotBlank() } }.distinct()
    val wired = devices.firstOrNull {
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE
    }
    val external = btNames.firstOrNull() ?: bt.firstOrNull()?.let { "Bluetooth" } ?: wired?.let {
        it.productName?.toString()?.takeIf { n -> n.isNotBlank() } ?: "Auriculares"
    }
    return Outputs(external, btNames)
}

private fun Context.startSafe(intent: Intent): Boolean =
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

@Composable
actual fun rememberAudioOutputControls(): AudioOutputControls {
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var outputs by remember { mutableStateOf(readOutputs(audio)) }
    DisposableEffect(audio) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                outputs = readOutputs(audio)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                outputs = readOutputs(audio)
            }
        }
        audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { audio.unregisterAudioDeviceCallback(callback) }
    }
    return remember(context, outputs) {
        AudioOutputControls(
            outputName = outputs.external,
            bluetooth = outputs.bluetooth,
            openSwitcher = {
                val shown = Build.VERSION.SDK_INT >= 34 &&
                    runCatching { MediaRouter2.getInstance(context).showSystemOutputSwitcher() }.getOrDefault(false)
                if (!shown) {
                    val panel = Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
                    if (!context.startSafe(panel)) context.startSafe(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
            },
            openBluetoothSettings = { context.startSafe(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
            supported = true,
        )
    }
}
