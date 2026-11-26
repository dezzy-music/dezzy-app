/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.crypto

/** SHA-1 de [input] (UTF-8) en hexadecimal minúscula (40 caracteres). */
expect fun sha1Hex(input: String): String
