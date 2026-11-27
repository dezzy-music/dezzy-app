/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.components

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.Composable

/** Chips de filtro como en la app actual: seleccionado casi blanco con texto/icono oscuro. */
@Composable
fun dezzyFilterChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
    selectedLabelColor = MaterialTheme.colorScheme.surface,
    selectedLeadingIconColor = MaterialTheme.colorScheme.surface,
    selectedTrailingIconColor = MaterialTheme.colorScheme.surface,
)
