/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.discord

import org.koin.dsl.module

val discordModule = module {
    single { DiscordPresence(get(), get(), get()) }
}
