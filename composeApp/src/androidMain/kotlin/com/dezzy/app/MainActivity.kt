/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.dezzy.app.feature.player.PlayerController
import com.dezzy.app.feature.video.VideoPipState
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val player: PlayerController by inject()

    /** Hora a la que se cerró la ventana PiP (para distinguir «cerrar» de «volver a la app»). */
    private var leftPipAtMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
        askNotificationPermission()
        handleSocialIntent(intent)
        lifecycleScope.launch {
            VideoPipState.eligible.collect { setVideoPipEligible(it) }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleSocialIntent(intent)
    }

    /** Toque en una notificación de la red social: abre el chat o la pestaña Juntos. */
    private fun handleSocialIntent(intent: android.content.Intent?) {
        when (intent?.action) {
            com.dezzy.app.feature.social.P2PNotifications.ACTION_OPEN_CHAT ->
                com.dezzy.app.feature.social.P2PDeepLinks.pending.value =
                    intent.getStringExtra(com.dezzy.app.feature.social.P2PNotifications.EXTRA_PEER).orEmpty()
            com.dezzy.app.feature.social.P2PNotifications.ACTION_OPEN_SOCIAL ->
                com.dezzy.app.feature.social.P2PDeepLinks.pending.value = ""
        }
    }

    /** Android 13+: sin este permiso la notificación del reproductor no se ve. */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    // Ventana flotante del modo vídeo -------------------------------------------------------

    private fun buildVideoPipParams(eligible: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(eligible)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun setVideoPipEligible(eligible: Boolean) {
        runCatching { setPictureInPictureParams(buildVideoPipParams(eligible)) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Desde Android 12 entra solo (setAutoEnterEnabled); antes hay que pedirlo.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && VideoPipState.eligible.value) {
            runCatching { enterPictureInPictureMode(buildVideoPipParams(true)) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        VideoPipState.inPip.value = isInPictureInPictureMode
        if (!isInPictureInPictureMode) leftPipAtMs = SystemClock.elapsedRealtime()
    }

    override fun onStop() {
        super.onStop()
        // Se cerró la ventana PiP con la X: la actividad se para justo después y el vídeo no
        // debe seguir sonando sin imagen.
        if (leftPipAtMs != 0L && SystemClock.elapsedRealtime() - leftPipAtMs < 1_500L) {
            leftPipAtMs = 0L
            if (player.video?.active?.value == true) player.pause()
        }
    }
}
