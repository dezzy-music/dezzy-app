/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dezzy.app.core.database.db.DezzyDatabase
import com.dezzy.app.core.prefs.InMemorySecureStorage
import com.dezzy.app.core.prefs.SecureStorage
import org.koin.dsl.module
import java.io.File
import java.util.Properties

actual val platformModule = module {
    single<SqlDriver> {
        val dir = File(System.getProperty("user.home"), ".dezzy-app").apply { mkdirs() }
        JdbcSqliteDriver("jdbc:sqlite:" + File(dir, "dezzy.db").absolutePath, Properties(), DezzyDatabase.Schema)
    }
    single<SecureStorage> { InMemorySecureStorage() }
}
