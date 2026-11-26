/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.accounts

import com.dezzy.app.core.model.SessionProvider
import kotlinx.serialization.Serializable

/** Cuenta conectada tal como se guarda (cifrada) en el almacén seguro. [secret] es la cookie. */
@Serializable
data class StoredAccount(
    val provider: SessionProvider,
    val secret: String,
    val name: String? = null,
    val handle: String? = null,
    val avatarUrl: String? = null,
    val dataSyncId: String? = null,
    val connectedAtEpochMs: Long,
)
