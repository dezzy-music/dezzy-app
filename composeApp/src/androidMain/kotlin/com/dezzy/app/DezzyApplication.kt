/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app

import android.app.Application
import com.dezzy.app.di.coreModule
import com.dezzy.app.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import com.dezzy.app.feature.discord.DiscordPresence
import com.dezzy.app.feature.scrobble.Scrobbler
import com.dezzy.app.feature.downloads.DownloadManager

class DezzyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DezzyApplication)
            modules(platformModule, coreModule)
        }
        // Siguen funcionando con la app en segundo plano: viven en su propio scope.
        val koin = GlobalContext.get()
        koin.get<Scrobbler>().start()
        koin.get<DiscordPresence>().start()
        koin.get<DownloadManager>().start()
        koin.get<com.dezzy.app.feature.connect.ConnectPlaybackBridge>().start()
        koin.get<com.dezzy.app.feature.social.P2PSocial>().start()
        koin.get<com.dezzy.app.feature.social.P2PNowPlayingBridge>().start()
        koin.get<com.dezzy.app.feature.together.TogetherSession>().start()
    }
}
