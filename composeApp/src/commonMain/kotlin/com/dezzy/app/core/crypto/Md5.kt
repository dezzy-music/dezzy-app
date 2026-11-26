/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.crypto

/** MD5 de [input] (UTF-8) en hexadecimal minúscula (32 caracteres). Sólo para la firma de Last.fm. */
expect fun md5Hex(input: String): String
