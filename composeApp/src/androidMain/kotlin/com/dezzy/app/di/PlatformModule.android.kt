/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dezzy.app.core.database.db.DezzyDatabase
import com.dezzy.app.core.prefs.KeystoreSecureStorage
import com.dezzy.app.core.prefs.SecureStorage
import com.dezzy.app.source.stream.StreamEngine
import com.dezzy.app.source.stream.WebViewStreamEngine
import com.dezzy.app.feature.player.DezzyPlayer
import com.dezzy.app.source.youtube.YouTubeMusicClient
import com.dezzy.app.feature.player.PlayerController
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {
    single<SqlDriver> { AndroidSqliteDriver(DezzyDatabase.Schema, androidContext(), "dezzy.db") }
    single<SecureStorage> { KeystoreSecureStorage(androidContext()) }
    single<StreamEngine> { WebViewStreamEngine(androidContext()) }
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create { androidContext().filesDir.resolve("datastore/dezzy.preferences_pb") }
    }
    single {
        val client = get<YouTubeMusicClient>()
        DezzyPlayer(androidContext(), get(), get(), get(), get(), get()).apply {
            radioProvider = { seed -> seed.videoId?.let { client.getUpNext(it).getOrNull() }.orEmpty() }
        }
    }
    single<PlayerController> { get<DezzyPlayer>() }
}
