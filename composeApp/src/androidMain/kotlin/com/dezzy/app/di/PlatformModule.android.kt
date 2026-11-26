/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dezzy.app.core.database.db.DezzyDatabase
import com.dezzy.app.core.prefs.KeystoreSecureStorage
import com.dezzy.app.core.prefs.SecureStorage
import com.dezzy.app.source.stream.StreamEngine
import com.dezzy.app.source.stream.WebViewStreamEngine
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {
    single<SqlDriver> { AndroidSqliteDriver(DezzyDatabase.Schema, androidContext(), "dezzy.db") }
    single<SecureStorage> { KeystoreSecureStorage(androidContext()) }
    single<StreamEngine> { WebViewStreamEngine(androidContext()) }
}
