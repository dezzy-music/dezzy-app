/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.downloads

import org.koin.core.module.Module
import org.koin.dsl.module

val downloadsModule: Module = module {
    single { DownloadManager(get(), get(), get(), get(), getKoin(), createDownloadStorage()) }
}
