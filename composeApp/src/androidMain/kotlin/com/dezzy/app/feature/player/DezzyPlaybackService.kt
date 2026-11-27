/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.dezzy.app.core.prefs.DezzyPreferences
import org.koin.android.ext.android.inject

/**
 * Servicio de reproducción: publica [DezzyPlayer] en una sesión multimedia para que haya
 * notificación, controles en la pantalla de bloqueo y botones de auriculares. Media3 lo pone
 * en primer plano mientras suena.
 */
@OptIn(UnstableApi::class)
class DezzyPlaybackService : MediaSessionService() {

    private val player: DezzyPlayer by inject()
    private val prefs: DezzyPreferences by inject()
    private var session: MediaSession? = null
    private var deviceBehaviors: AudioDeviceBehaviors? = null

    override fun onCreate() {
        super.onCreate()
        deviceBehaviors = AudioDeviceBehaviors(this, player, prefs).also { it.start() }
        val builder = MediaSession.Builder(this, player.sessionPlayer)
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setSessionActivity(
                PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        session = builder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Se quitó la app de recientes: si no suena nada, el servicio se va. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (player.stopOnTaskClear) player.pause()
        if (p == null || player.stopOnTaskClear || !p.playWhenReady || p.mediaItemCount == 0) {
            player.releaseService()
            stopSelf()
        }
    }

    override fun onDestroy() {
        deviceBehaviors?.stop()
        deviceBehaviors = null
        player.onServiceDestroyed()
        session?.release()
        session = null
        super.onDestroy()
    }
}
