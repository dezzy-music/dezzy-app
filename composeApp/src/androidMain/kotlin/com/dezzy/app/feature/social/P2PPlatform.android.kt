/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.dezzy.app.feature.social.crypto.hexToBytes
import com.dezzy.app.feature.social.crypto.toHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.koin.core.scope.Scope
import java.io.ByteArrayOutputStream
import java.util.Base64

actual fun createP2PImageTools(scope: Scope): P2PImageTools = AndroidP2PImageTools(scope.get<Context>().applicationContext)

actual fun createP2PNotifier(scope: Scope): P2PNotifier = AndroidP2PNotifier(scope.get<Context>().applicationContext)

actual fun createP2PBle(scope: Scope): P2PBle = AndroidP2PBle(scope.get<Context>().applicationContext)

actual fun p2pDeviceName(): String = Build.MODEL.orEmpty().ifBlank { "Móvil" }.take(60)

// --- Imagen y video ----------------------------------------------------------------------------

private class AndroidP2PImageTools(private val context: Context) : P2PImageTools {

    private fun decodeUri(source: String, target: Int): Bitmap? {
        val uri = Uri.parse(source)
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, target) }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun decodeBytes(bytes: ByteArray, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, target) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun sampleFor(w: Int, h: Int, target: Int): Int {
        var sample = 1
        while (w / (sample * 2) >= target && h / (sample * 2) >= target) sample *= 2
        return sample
    }

    private fun avatar(bitmap: Bitmap, size: Int): String {
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, size, size, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray())
    }

    private fun photo(bitmap: Bitmap, maxSide: Int): ByteArray {
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
    }

    override suspend fun avatarDataUri(source: String, size: Int): String? = withContext(Dispatchers.IO) {
        runCatching { decodeUri(source, size * 2)?.let { avatar(it, size) } }.getOrNull()
    }

    override suspend fun avatarDataUri(bytes: ByteArray, size: Int): String? = withContext(Dispatchers.IO) {
        runCatching { decodeBytes(bytes, size * 2)?.let { avatar(it, size) } }.getOrNull()
    }

    override suspend fun compressPhoto(source: String, maxSide: Int): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { decodeUri(source, maxSide)?.let { photo(it, maxSide) } }.getOrNull()
    }

    override suspend fun compressPhoto(bytes: ByteArray, maxSide: Int): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { decodeBytes(bytes, maxSide)?.let { photo(it, maxSide) } }.getOrNull()
    }

    override suspend fun prepareBannerVideo(source: String): Result<P2PBannerVideo> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(source)
            val resolver = context.contentResolver
            val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            if (size > BANNER_VIDEO_MAX_BYTES) error("El video pesa demasiado. Elige uno de menos de 30 MB.")
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                if (ms > BANNER_VIDEO_MAX_MS) error("El video dura más de 15 segundos. Recórtalo en la galería y vuelve a elegirlo.")
                val frame = retriever.getFrameAtTime(0) ?: error("No se pudo leer el video.")
                val scale = minOf(1f, 1280f / maxOf(frame.width, frame.height))
                val poster = if (scale < 1f) {
                    Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true)
                } else {
                    frame
                }
                val jpeg = ByteArrayOutputStream().use {
                    poster.compress(Bitmap.CompressFormat.JPEG, 85, it)
                    it.toByteArray()
                }
                val mime = resolver.getType(uri)?.takeIf { it == "video/mp4" || it == "video/webm" } ?: "video/mp4"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("No se pudo leer el video.")
                P2PBannerVideo(bytes, mime, jpeg)
            } finally {
                retriever.release()
            }
        }
    }

    private companion object {
        const val BANNER_VIDEO_MAX_BYTES = 30L * 1024 * 1024
        const val BANNER_VIDEO_MAX_MS = 15_500L
    }
}

// --- Notificaciones -----------------------------------------------------------------------------

private class AndroidP2PNotifier(private val context: Context) : P2PNotifier {

    private class Line(val text: String, val time: Long)

    /** Últimas líneas sin leer por conversación (sólo en memoria, para el estilo mensajería). */
    private val pending = HashMap<String, MutableList<Line>>()

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_MESSAGES) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_MESSAGES, "Mensajes", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Mensajes cifrados de tus amigos"
                },
            )
        }
        if (nm.getNotificationChannel(CHANNEL_SOCIAL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SOCIAL, "Amigos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Solicitudes para seguirte"
                },
            )
        }
    }

    private fun notificationId(peer: String, kind: Int): Int = 0x50320000 xor (peer.hashCode() * 31 + kind)

    private fun openIntent(action: String, peer: String): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        intent.action = action
        intent.putExtra(P2PNotifications.EXTRA_PEER, peer)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        return PendingIntent.getActivity(
            context,
            notificationId(peer, action.hashCode()),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun notifyMessage(peer: String, name: String, text: String, songTitle: String?, createdAtSec: Long) {
        if (!canNotify()) return
        ensureChannels()
        val body = when {
            text.isNotBlank() && songTitle != null -> "$text · $songTitle"
            text.isNotBlank() -> text
            songTitle != null -> "Te compartió «$songTitle»"
            else -> "Mensaje nuevo"
        }
        val snapshot = synchronized(pending) {
            val lines = pending.getOrPut(peer) { mutableListOf() }
            lines += Line(body, createdAtSec * 1000L)
            lines.sortBy { it.time }
            while (lines.size > MAX_LINES) lines.removeAt(0)
            lines.toList()
        }
        val sender = Person.Builder().setName(name).setKey(peer).build()
        val me = Person.Builder().setName("Tú").build()
        val style = NotificationCompat.MessagingStyle(me)
        snapshot.forEach { style.addMessage(it.text, it.time, sender) }
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setStyle(style)
            .setContentTitle(name)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_MESSAGES)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .apply { openIntent(P2PNotifications.ACTION_OPEN_CHAT, peer)?.let { setContentIntent(it) } }
            .build()
        post(notificationId(peer, 1), notification)
    }

    override fun notifyFollowRequest(peer: String, name: String) {
        if (!canNotify()) return
        ensureChannels()
        val notification = NotificationCompat.Builder(context, CHANNEL_SOCIAL)
            .setSmallIcon(android.R.drawable.ic_menu_add)
            .setContentTitle("$name quiere seguirte")
            .setContentText("Toca para aceptar o rechazar la solicitud.")
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .apply { openIntent(P2PNotifications.ACTION_OPEN_SOCIAL, peer)?.let { setContentIntent(it) } }
            .build()
        post(notificationId(peer, 2), notification)
    }

    override fun clearChat(peer: String) {
        synchronized(pending) { pending.remove(peer) }
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId(peer, 1)) }
    }

    override fun clearAll() {
        synchronized(pending) { pending.clear() }
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            nm.activeNotifications
                .filter { it.notification.group == GROUP_MESSAGES || it.notification.channelId == CHANNEL_SOCIAL }
                .forEach { nm.cancel(it.id) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun post(id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Sin permiso para notificar.
        }
    }

    private companion object {
        const val CHANNEL_MESSAGES = "p2p_messages"
        const val CHANNEL_SOCIAL = "p2p_social"
        const val GROUP_MESSAGES = "com.dezzy.app.P2P_MESSAGES"
        const val MAX_LINES = 6
    }
}

// --- Bluetooth LE -------------------------------------------------------------------------------

private class AndroidP2PBle(private val context: Context) : P2PBle {

    private val _status = MutableStateFlow(NearbyStatus.IDLE)
    override val status: StateFlow<NearbyStatus> = _status.asStateFlow()

    private var advertising = false
    private var scanning = false
    private var myKey: String? = null
    private var onFound: ((String, Int) -> Unit)? = null

    override fun requiredPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    override fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            advertising = false
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = onResult(result)

        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { onResult(it) }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
        }
    }

    private fun onResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val a = record.getServiceData(UUID_A) ?: return
        val b = record.getServiceData(UUID_B) ?: return
        if (a.size != 16 || b.size != 16) return
        val pubkey = (a + b).toHex()
        if (pubkey == myKey) return
        onFound?.invoke(pubkey, result.rssi)
    }

    @SuppressLint("MissingPermission")
    override fun start(myPubkey: String, advertise: Boolean, onFound: (pubkey: String, rssi: Int) -> Unit) {
        this.myKey = myPubkey
        this.onFound = onFound
        if (!hasPermissions()) {
            _status.value = NearbyStatus.NO_PERMISSION
            return
        }
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null) {
            _status.value = NearbyStatus.UNSUPPORTED
            return
        }
        if (!adapter.isEnabled) {
            _status.value = NearbyStatus.BLUETOOTH_OFF
            return
        }
        runCatching {
            if (!advertising && advertise) {
                val bytes = myPubkey.hexToBytes()
                adapter.bluetoothLeAdvertiser?.let { advertiser ->
                    val settings = AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                        .setConnectable(false)
                        .build()
                    val data = AdvertiseData.Builder()
                        .setIncludeDeviceName(false)
                        .addServiceData(UUID_A, bytes.copyOfRange(0, 16))
                        .build()
                    val response = AdvertiseData.Builder()
                        .setIncludeDeviceName(false)
                        .addServiceData(UUID_B, bytes.copyOfRange(16, 32))
                        .build()
                    advertiser.startAdvertising(settings, data, response, advertiseCallback)
                    advertising = true
                }
            }
            if (!scanning) {
                val filters = listOf(ScanFilter.Builder().setServiceData(UUID_A, ByteArray(0), ByteArray(0)).build())
                val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
                adapter.bluetoothLeScanner?.startScan(filters, settings, scanCallback)
                scanning = true
            }
            _status.value = NearbyStatus.SCANNING
        }.onFailure {
            _status.value = NearbyStatus.NO_PERMISSION
        }
    }

    @SuppressLint("MissingPermission")
    override fun stop() {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        runCatching { if (scanning) adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        runCatching { if (advertising) adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) }
        scanning = false
        advertising = false
        onFound = null
        if (_status.value == NearbyStatus.SCANNING) _status.value = NearbyStatus.IDLE
    }

    private companion object {
        /** UUID de 16 bits propios de Dezzy (primera y segunda mitad de la clave). */
        val UUID_A: ParcelUuid = ParcelUuid.fromString("0000d22e-0000-1000-8000-00805f9b34fb")
        val UUID_B: ParcelUuid = ParcelUuid.fromString("0000d22f-0000-1000-8000-00805f9b34fb")
    }
}
