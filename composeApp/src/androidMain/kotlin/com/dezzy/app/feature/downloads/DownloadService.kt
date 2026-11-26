/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.mp.KoinPlatform

/**
 * Servicio en primer plano (dataSync) con la notificación «Descargando N canciones».
 * Lo arranca/para [onDownloadsActive]; además se para solo si la cola se queda sin trabajo.
 */
class DownloadService : Service() {

    private val manager: DownloadManager by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val first = buildNotification(0, 0f, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, first)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        manager.start()
        if (watcher?.isActive != true) {
            watcher = scope.launch {
                manager.state.collect { s ->
                    val pending = s.items.filter {
                        it.status == DownloadStatus.QUEUED ||
                            it.status == DownloadStatus.RESOLVING ||
                            it.status == DownloadStatus.SAVING
                    }
                    if (s.paused || pending.isEmpty()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@collect
                    }
                    val saving = pending.filter { it.status == DownloadStatus.SAVING }
                    val progress = if (saving.isEmpty()) 0f else saving.map { it.progress }.average().toFloat()
                    val current = (saving.firstOrNull() ?: pending.first()).track.title
                    notify(buildNotification(pending.size, progress, current))
                    delay(800) // ~1 actualización por segundo; collect se salta lo intermedio
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(notification: Notification) {
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Descargas", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun buildNotification(count: Int, progress: Float, current: String?): Notification {
        val title = when (count) {
            0 -> "Descargando"
            1 -> "Descargando 1 canción"
            else -> "Descargando $count canciones"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (!current.isNullOrBlank()) builder.setContentText(current)
        if (progress > 0f) builder.setProgress(100, (progress * 100).toInt().coerceIn(0, 100), false)
        else builder.setProgress(0, 0, true)
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setContentIntent(
                PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        return builder.build()
    }

    private companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 7421
    }
}

/** Arranca (o para) el servicio en primer plano de descargas. */
actual fun onDownloadsActive(active: Boolean) {
    val context = KoinPlatform.getKoin().get<Context>()
    val intent = Intent(context, DownloadService::class.java)
    if (active) {
        // Puede fallar si la app está en segundo plano sin permiso para arrancar servicios: no es grave.
        runCatching { ContextCompat.startForegroundService(context, intent) }
    } else {
        runCatching { context.stopService(intent) }
    }
}
