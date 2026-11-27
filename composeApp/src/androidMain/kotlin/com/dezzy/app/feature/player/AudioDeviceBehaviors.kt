/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.player

import android.content.Context
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.dezzy.app.core.prefs.DezzyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Comportamientos ligados al audio del teléfono, vivos mientras el servicio de reproducción existe:
 * pausar al silenciar el volumen de medios y reproducir al conectar un dispositivo Bluetooth.
 * Sin permisos nuevos: usa [AudioManager] y un [ContentObserver] del volumen.
 */
class AudioDeviceBehaviors(
    private val context: Context,
    private val player: PlayerController,
    private val prefs: DezzyPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile private var pauseOnMute = false
    @Volatile private var playOnBluetooth = false
    private var pausedByMute = false
    private var knownDevices: Set<Int> = emptySet()

    private val volumeObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = onVolumeChanged()
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            val fresh = addedDevices.filter { it.id !in knownDevices }
            knownDevices = knownDevices + addedDevices.map { it.id }
            if (!playOnBluetooth) return
            if (fresh.any { it.isSink && it.isBluetoothOutput() }) playIfQueued()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            knownDevices = knownDevices - removedDevices.map { it.id }.toSet()
        }
    }

    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch { prefs.value(PauseOnMuteKey, false).collect { pauseOnMute = it } }
        scope.launch { prefs.value(PlayOnBluetoothKey, false).collect { playOnBluetooth = it } }
        // Los dispositivos que ya estaban conectados no cuentan como «recién conectados».
        knownDevices = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.id }.toSet()
        audio.registerAudioDeviceCallback(deviceCallback, handler)
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver)
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { audio.unregisterAudioDeviceCallback(deviceCallback) }
        runCatching { context.contentResolver.unregisterContentObserver(volumeObserver) }
        scope.cancel()
    }

    private fun onVolumeChanged() {
        if (!pauseOnMute) return
        val volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (volume == 0) {
            if (player.state.value.isPlaying) {
                pausedByMute = true
                player.pause()
            }
        } else if (pausedByMute) {
            pausedByMute = false
            playIfQueued()
        }
    }

    private fun playIfQueued() {
        val state = player.state.value
        if (state.queue.current != null && !state.isPlaying) player.play()
    }

    private fun AudioDeviceInfo.isBluetoothOutput(): Boolean = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        -> true
        else -> false
    }
}
