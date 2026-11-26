/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social

import com.dezzy.app.feature.social.crypto.NostrCrypto
import com.dezzy.app.feature.social.crypto.platformNostrCrypto
import com.dezzy.app.feature.social.data.P2PStore
import com.dezzy.app.feature.social.nostr.RelayPool
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Red social (Nostr). Opus llama a `get<P2PSocial>().start()` al arrancar la app: carga la identidad
 * y, si hay cuenta, abre relés y buzón. Las dependencias circulares (social <-> mensajería) se
 * resuelven con lambdas perezosas.
 */
val socialModule: Module = module {
    single<NostrCrypto> { platformNostrCrypto() }
    single { RelayPool(get()) }
    single { P2PStore(get(), get()) }
    single { P2PSettings(get()) }
    single { P2PIdentity(get(), get()) }
    single<P2PImageTools> { createP2PImageTools(this) }
    single<P2PNotifier> { createP2PNotifier(this) }
    single<P2PBle> { createP2PBle(this) }
    single { P2PNotifications(get()) }
    single { P2PInboxWorker(get(), get()) }
    single { P2PMessaging(get(), get(), get(), get(), get(), { get<P2PSocial>() }) }
    single { P2PSocial(get(), get(), get(), get(), get(), get(), get(), get(), get(), { get<P2PMessaging>() }) }
    single { P2PPresence(get(), get(), get(), { get<P2PSocial>() }) }
    single { P2PMap(get(), { get<P2PSocial>() }) }
    single { P2PNearby(get(), get(), get(), { get<P2PSocial>() }) }
    single { DesktopLink(get(), get(), get(), get(), get()) }
    single { P2PNowPlayingBridge(get(), get()) }
}
