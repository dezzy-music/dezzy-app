/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.downloads

/** true si la red activa es de datos móviles / medida. Desktop = false. */
expect fun isMeteredNetwork(): Boolean
