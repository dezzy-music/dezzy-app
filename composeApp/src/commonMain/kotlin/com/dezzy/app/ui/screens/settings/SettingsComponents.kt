/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.ui.components.glass.DezzySwitch
import com.dezzy.app.ui.screens.ScreenContext

/** Página de Ajustes: sin `onBack` es la principal (título grande); con `onBack`, barra con flecha. */
@Composable
internal fun SettingsPage(title: String, onBack: (() -> Unit)?, backAbove: (() -> Unit)? = null, content: LazyListScope.() -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize()) {
        if (onBack == null && backAbove != null) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = backAbove) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") }
            }
        }
        if (onBack != null) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = if (onBack == null && backAbove == null) top else 0.dp, bottom = bottom + 16.dp),
        ) {
            if (onBack == null) {
                item(key = "settings-title") {
                    Text(
                        title,
                        fontSize = 32.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
                    )
                }
            }
            content()
        }
    }
}

/** Grupo de filas dentro de una tarjeta redondeada, con etiqueta opcional encima. */
@Composable
internal fun SettingsGroup(label: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 6.dp),
            )
        }
        // Como la app actual: cada fila es su propia tarjeta, separadas por un hueco fino.
        Column(
            Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp)),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@Composable
internal fun SettingsDivider() {
    // Las filas ya van separadas en tarjetas propias; no hace falta línea.
}

/** Fondo de tarjeta de cada fila de ajustes. */
@Composable
internal fun Modifier.settingsCard(): Modifier =
    this.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)

@Composable
private fun SettingsIconCircle(icon: ImageVector) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.surface)
    }
}

@Composable
private fun RowTexts(title: String, subtitle: String?, modifier: Modifier) {
    Column(modifier) {
        Text(title, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(
                subtitle,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    badge: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .settingsCard()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) SettingsIconCircle(icon)
        RowTexts(title, subtitle, Modifier.weight(1f))
        if (badge) Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
        if (trailing != null) trailing()
    }
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .settingsCard()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) SettingsIconCircle(icon)
        RowTexts(title, subtitle, Modifier.weight(1f))
        DezzySwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
internal fun <T> SettingsChoiceRow(
    title: String,
    icon: ImageVector? = null,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    subtitle: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    SettingsRow(title = title, subtitle = subtitle ?: label(selected), icon = icon, onClick = { open = true })
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { open = false; onSelect(option) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == selected, onClick = { open = false; onSelect(option) })
                            Text(label(option), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
internal fun SettingsPendingRow(
    title: String,
    phase: String,
    ctx: ScreenContext,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle ?: "Llega en la fase $phase",
        icon = icon,
        onClick = { ctx.pending(title, phase) },
    )
}

@Composable
internal fun SettingsSliderRow(
    title: String,
    icon: ImageVector?,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: (Float) -> String,
    onValueChangeFinished: (Float) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).settingsCard().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (icon != null) SettingsIconCircle(icon)
            RowTexts(title, valueLabel(draft), Modifier.weight(1f))
        }
        Slider(
            value = draft,
            onValueChange = { draft = it },
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = { onValueChangeFinished(draft) },
        )
    }
}
