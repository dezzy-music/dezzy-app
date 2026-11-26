/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.di

import org.koin.core.module.Module

/** Lo que cada plataforma aporta: driver de la BD y almacén de secretos. */
expect val platformModule: Module
