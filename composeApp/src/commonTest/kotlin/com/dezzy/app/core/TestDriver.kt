/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core

import app.cash.sqldelight.db.SqlDriver

/** SQLite en memoria con el esquema ya creado. */
expect fun createTestDriver(): SqlDriver
