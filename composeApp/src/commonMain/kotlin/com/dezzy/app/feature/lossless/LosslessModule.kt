/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.lossless

import com.dezzy.app.feature.downloads.DownloadPostProcessor
import com.dezzy.app.feature.downloads.LosslessUrlProvider
import com.dezzy.app.feature.downloads.export.DownloadExporter
import com.dezzy.app.feature.downloads.export.createDownloadExporter
import org.koin.core.module.Module
import org.koin.dsl.module

val losslessModule: Module = module {
    single { LosslessClient(get(), get()) }
    single<LosslessUrlProvider> { LosslessResolver(get()) }
    single<PublicMusicCopier> { createPublicMusicCopier(this) }
    single<DownloadPostProcessor> { LosslessPostProcessor(get(), get(), get(), get()) }
    single<DownloadExporter> { createDownloadExporter(this) }
}
