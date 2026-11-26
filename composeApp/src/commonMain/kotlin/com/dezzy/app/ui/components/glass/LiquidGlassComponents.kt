/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.components.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ChipElevation
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.ui.theme.NothingBoneWhite
import com.dezzy.app.ui.theme.NothingCardLight
import com.dezzy.app.ui.theme.NothingCharcoal
import com.dezzy.app.ui.theme.NothingInkLight
import com.dezzy.app.ui.theme.NothingOledBlack
import com.dezzy.app.ui.theme.NothingVermilion
import com.dezzy.app.ui.theme.dezzyStyledSurface
import com.dezzy.app.ui.theme.isCustomStyledTheme
import com.dezzy.app.ui.theme.isLiquidGlassTheme
import com.dezzy.app.ui.theme.isNothingTheme
import com.dezzy.app.ui.theme.liquidGlassPressScale
import com.dezzy.app.ui.theme.rememberLiquidGlassPalette
import androidx.compose.ui.graphics.luminance

@Composable
private fun styledContentColor(
    prominent: Boolean,
    tintColor: Color = Color.Unspecified,
    requested: Color = Color.Unspecified,
    preferRequestedOnProminent: Boolean = true,
): Color {
    if (isNothingTheme()) {
        return when {
            tintColor == NothingBoneWhite -> NothingOledBlack
            tintColor == NothingInkLight -> NothingBoneWhite
            prominent && tintColor == Color.Unspecified -> Color.White
            prominent && preferRequestedOnProminent && requested != Color.Unspecified -> requested
            prominent -> if (tintColor.luminance() > 0.65f) NothingOledBlack else Color.White
            requested != Color.Unspecified -> requested
            else -> MaterialTheme.colorScheme.onSurface
        }
    }
    val palette = rememberLiquidGlassPalette(prominent = prominent, tintColor = tintColor)
    return if (preferRequestedOnProminent && requested != Color.Unspecified && prominent) {
        requested
    } else {
        palette.contentColor
    }
}

@Composable
fun DezzyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    prominent: Boolean = true,
    dashedWhenNothing: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    if (!isCustomStyledTheme()) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }

    val src = interactionSource ?: remember { MutableInteractionSource() }
    val tint = if (colors.containerColor != Color.Unspecified && colors.containerColor != Color.Transparent) {
        colors.containerColor
    } else {
        Color.Unspecified
    }
    val contentColor = styledContentColor(
        prominent = prominent,
        tintColor = tint,
        requested = colors.contentColor,
    )

    Row(
        modifier = modifier
            .defaultMinSize(minWidth = ButtonDefaults.MinWidth, minHeight = ButtonDefaults.MinHeight)
            .liquidGlassPressScale(src, enabled = enabled)
            .alpha(if (enabled) 1f else 0.45f)
            .dezzyStyledSurface(
                shape = shape,
                prominent = prominent,
                tintColor = tint,
                dashedWhenNothing = dashedWhenNothing,
            )
            .clickable(
                interactionSource = src,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                content()
            }
        }
    }
}

@Composable
fun DezzyFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (!isCustomStyledTheme()) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    DezzyButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        prominent = false,
        content = content,
    )
}

@Composable
fun DezzyElevatedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: ButtonColors = ButtonDefaults.elevatedButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.elevatedButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (!isCustomStyledTheme()) {
        ElevatedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    DezzyButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        prominent = false,
        content = content,
    )
}

@Composable
fun DezzyOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (!isCustomStyledTheme()) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    DezzyButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors.copy(containerColor = Color.Unspecified),
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        prominent = false,
        dashedWhenNothing = true,
        content = content,
    )
}

@Composable
fun DezzyTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (!isCustomStyledTheme()) {
        TextButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    val src = interactionSource ?: remember { MutableInteractionSource() }
    val contentColor = styledContentColor(prominent = false, requested = colors.contentColor)

    Row(
        modifier = modifier
            .defaultMinSize(minWidth = ButtonDefaults.MinWidth, minHeight = 36.dp)
            .liquidGlassPressScale(src, enabled = enabled)
            .alpha(if (enabled) 1f else 0.45f)
            .dezzyStyledSurface(
                shape = shape,
                prominent = false,
                enableShadow = false,
                refractionScale = 0.75f,
            )
            .clickable(
                interactionSource = src,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                content()
            }
        }
    }
}

@Composable
fun DezzyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    prominent: Boolean = false,
    shape: Shape = CircleShape,
    dashedWhenNothing: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (!isCustomStyledTheme()) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    val src = interactionSource ?: remember { MutableInteractionSource() }
    val tint = if (colors.containerColor != Color.Unspecified && colors.containerColor != Color.Transparent) {
        colors.containerColor
    } else {
        Color.Unspecified
    }
    val iconTint = styledContentColor(prominent = prominent, tintColor = tint, requested = colors.contentColor)

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
            .liquidGlassPressScale(src, enabled = enabled, pressedScale = 0.88f)
            .alpha(if (enabled) 1f else 0.42f)
            .dezzyStyledSurface(
                shape = shape,
                prominent = prominent,
                tintColor = tint,
                refractionScale = 0.85f,
                enableShadow = prominent,
                dashedWhenNothing = dashedWhenNothing,
            )
            .clickable(
                interactionSource = src,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides iconTint) {
            content()
        }
    }
}

@Composable
fun DezzyIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconToggleButtonColors = IconButtonDefaults.iconToggleButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    if (!isCustomStyledTheme()) {
        IconToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    DezzyIconButton(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        enabled = enabled,
        prominent = checked,
        interactionSource = interactionSource,
        content = content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DezzyIconButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val isStyled = isCustomStyledTheme()
    val tint = if (colors.containerColor != Color.Unspecified && colors.containerColor != Color.Transparent) {
        colors.containerColor
    } else {
        Color.Unspecified
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .then(
                if (isStyled) {
                    Modifier
                        .liquidGlassPressScale(interactionSource, enabled = enabled, pressedScale = 0.88f)
                        .dezzyStyledSurface(
                            shape = CircleShape,
                            prominent = false,
                            tintColor = tint,
                            refractionScale = 0.85f,
                            enableShadow = false,
                        )
                } else {
                    Modifier
                        .background(color = colors.containerColor, shape = CircleShape)
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(bounded = false, radius = 24.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.contentColor, content = content)
    }
}

@Composable
fun DezzyFilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    if (!isCustomStyledTheme()) {
        FilledIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    DezzyIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        prominent = true,
        shape = shape,
        content = content,
    )
}

@Composable
fun DezzyFilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    if (!isCustomStyledTheme()) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    DezzyIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        prominent = false,
        shape = shape,
        content = content,
    )
}

@Composable
fun DezzyOutlinedIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: IconButtonColors = IconButtonDefaults.outlinedIconButtonColors(),
    border: BorderStroke? = IconButtonDefaults.outlinedIconButtonBorder(enabled),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    if (!isCustomStyledTheme()) {
        OutlinedIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            border = border,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    DezzyIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        prominent = false,
        shape = shape,
        dashedWhenNothing = true,
        content = content,
    )
}

@Composable
fun DezzyFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    if (!isCustomStyledTheme()) {
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            elevation = elevation,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    val src = interactionSource ?: remember { MutableInteractionSource() }
    val tint = if (isNothingTheme()) NothingVermilion else containerColor
    val effectiveContent = styledContentColor(prominent = true, tintColor = tint, requested = contentColor, preferRequestedOnProminent = false)

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
            .liquidGlassPressScale(src, pressedScale = 0.90f)
            .dezzyStyledSurface(
                shape = shape,
                prominent = true,
                tintColor = tint,
                enableShadow = true,
            )
            .clickable(
                interactionSource = src,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides effectiveContent) {
            content()
        }
    }
}

@Composable
fun DezzySmallFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    if (!isCustomStyledTheme()) {
        SmallFloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            elevation = elevation,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    DezzyFloatingActionButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 40.dp, minHeight = 40.dp),
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun DezzyExtendedFloatingActionButton(
    text: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    shape: Shape = CircleShape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
) {
    if (!isCustomStyledTheme()) {
        ExtendedFloatingActionButton(
            text = text,
            icon = icon,
            onClick = onClick,
            modifier = modifier,
            expanded = expanded,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            elevation = elevation,
            interactionSource = interactionSource,
        )
        return
    }
    val src = interactionSource ?: remember { MutableInteractionSource() }
    val tint = if (isNothingTheme()) NothingVermilion else containerColor
    val effectiveContent = styledContentColor(prominent = true, tintColor = tint, requested = contentColor, preferRequestedOnProminent = false)

    Row(
        modifier = modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
            .liquidGlassPressScale(src, pressedScale = 0.92f)
            .dezzyStyledSurface(
                shape = shape,
                prominent = true,
                tintColor = tint,
                enableShadow = true,
            )
            .clickable(
                interactionSource = src,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = if (expanded) 20.dp else 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides effectiveContent) {
            icon()
            if (expanded) {
                Spacer(Modifier.width(12.dp))
                ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                    text()
                }
            }
        }
    }
}

@Composable
private fun GlassChipRow(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    selected: Boolean,
    role: Role,
    shape: Shape,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    interactionSource: MutableInteractionSource?,
) {
    val src = interactionSource ?: remember { MutableInteractionSource() }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val chipTint = when {
        isNothingTheme() && selected -> if (isDark) NothingBoneWhite else NothingInkLight
        else -> Color.Unspecified
    }
    val contentColor = styledContentColor(prominent = selected, tintColor = chipTint)

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 34.dp)
            .liquidGlassPressScale(src, enabled = enabled)
            .alpha(if (enabled) 1f else 0.45f)
            .dezzyStyledSurface(
                shape = shape,
                prominent = selected,
                tintColor = chipTint,
                enableShadow = selected,
                refractionScale = 0.8f,
            )
            .clickable(
                interactionSource = src,
                indication = LocalIndication.current,
                enabled = enabled,
                role = role,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            if (leadingIcon != null) leadingIcon()
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                label()
            }
            if (trailingIcon != null) trailingIcon()
        }
    }
}

@Composable
fun DezzyFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(50),
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
) {
    if (!isCustomStyledTheme()) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = label,
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            interactionSource = interactionSource,
        )
        return
    }
    GlassChipRow(
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        selected = selected,
        role = Role.Checkbox,
        shape = shape,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        interactionSource = interactionSource,
    )
}

@Composable
fun DezzyElevatedFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(50),
    colors: SelectableChipColors = FilterChipDefaults.elevatedFilterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.elevatedFilterChipElevation(),
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    if (!isCustomStyledTheme()) {
        ElevatedFilterChip(
            selected = selected,
            onClick = onClick,
            label = label,
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            interactionSource = interactionSource,
        )
        return
    }
    GlassChipRow(
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        selected = selected,
        role = Role.Checkbox,
        shape = shape,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        interactionSource = interactionSource,
    )
}

@Composable
fun DezzyAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(50),
    colors: ChipColors = AssistChipDefaults.assistChipColors(),
    elevation: ChipElevation? = AssistChipDefaults.assistChipElevation(),
    border: BorderStroke? = AssistChipDefaults.assistChipBorder(enabled),
    interactionSource: MutableInteractionSource? = null,
) {
    if (!isCustomStyledTheme()) {
        AssistChip(
            onClick = onClick,
            label = label,
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            interactionSource = interactionSource,
        )
        return
    }
    GlassChipRow(
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        selected = false,
        role = Role.Button,
        shape = shape,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        interactionSource = interactionSource,
    )
}

@Composable
fun DezzyElevatedAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(50),
    colors: ChipColors = AssistChipDefaults.elevatedAssistChipColors(),
    elevation: ChipElevation? = AssistChipDefaults.elevatedAssistChipElevation(),
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    if (!isCustomStyledTheme()) {
        ElevatedAssistChip(
            onClick = onClick,
            label = label,
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            interactionSource = interactionSource,
        )
        return
    }
    GlassChipRow(
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        selected = false,
        role = Role.Button,
        shape = shape,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        interactionSource = interactionSource,
    )
}

@Composable
fun DezzySuggestionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(50),
    colors: ChipColors = SuggestionChipDefaults.suggestionChipColors(),
    elevation: ChipElevation? = SuggestionChipDefaults.suggestionChipElevation(),
    border: BorderStroke? = SuggestionChipDefaults.suggestionChipBorder(enabled),
    interactionSource: MutableInteractionSource? = null,
) {
    if (!isCustomStyledTheme()) {
        SuggestionChip(
            onClick = onClick,
            label = label,
            modifier = modifier,
            enabled = enabled,
            icon = icon,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            interactionSource = interactionSource,
        )
        return
    }
    GlassChipRow(
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        selected = false,
        role = Role.Button,
        shape = shape,
        leadingIcon = icon,
        trailingIcon = null,
        interactionSource = interactionSource,
    )
}

@Composable
fun DezzyInputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    avatar: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(50),
    colors: SelectableChipColors = InputChipDefaults.inputChipColors(),
    elevation: SelectableChipElevation? = InputChipDefaults.inputChipElevation(),
    border: BorderStroke? = InputChipDefaults.inputChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
) {
    if (!isCustomStyledTheme()) {
        InputChip(
            selected = selected,
            onClick = onClick,
            label = label,
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            avatar = avatar,
            trailingIcon = trailingIcon,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            interactionSource = interactionSource,
        )
        return
    }
    GlassChipRow(
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        selected = selected,
        role = Role.Checkbox,
        shape = shape,
        leadingIcon = avatar ?: leadingIcon,
        trailingIcon = trailingIcon,
        interactionSource = interactionSource,
    )
}

@Composable
fun <T> DezzySingleChoiceSegmentedRow(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    icon: (@Composable (T, Boolean) -> Unit)? = null,
) {
    if (!isCustomStyledTheme()) {
        SingleChoiceSegmentedButtonRow(modifier = modifier) {
            items.forEachIndexed { index, item ->
                val isSelected = item == selected
                SegmentedButton(
                    selected = isSelected,
                    onClick = { onSelect(item) },
                    shape = SegmentedButtonDefaults.itemShape(index, items.size),
                    icon = { icon?.invoke(item, isSelected) },
                ) {
                    Text(
                        text = label(item),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        return
    }

    val isNothing = isNothingTheme()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Row(
        modifier = modifier
            .dezzyStyledSurface(
                shape = CircleShape,
                prominent = false,
                enableShadow = false,
                refractionScale = 0.85f,
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            val src = remember(item) { MutableInteractionSource() }
            val selectedTint = when {
                isNothing && isSelected -> if (isDark) NothingBoneWhite else NothingInkLight
                else -> Color.Unspecified
            }
            val itemContentColor = styledContentColor(prominent = isSelected, tintColor = selectedTint)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 36.dp)
                    .liquidGlassPressScale(src, pressedScale = 0.96f)
                    .then(
                        if (isSelected) {
                            Modifier.dezzyStyledSurface(
                                shape = CircleShape,
                                prominent = true,
                                tintColor = selectedTint,
                                enableShadow = false,
                                refractionScale = 0.7f,
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable(
                        interactionSource = src,
                        indication = LocalIndication.current,
                        role = Role.RadioButton,
                        onClick = { onSelect(item) },
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalContentColor provides itemContentColor) {
                    if (icon != null) {
                        icon(item, isSelected)
                        if (isSelected) Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = label(item),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Drop-in styled container for custom transport / play-pause / action surfaces
 * in player styles (Classic, Redesigned, Aurora, Spotify, CarMode, etc.).
 */
@Composable
fun DezzyGlassSurfaceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    normalContainerColor: Color,
    normalContentColor: Color,
    prominent: Boolean = true,
    glassTintColor: Color = normalContainerColor,
    content: @Composable () -> Unit,
) {
    val isStyled = isCustomStyledTheme()
    val src = remember { MutableInteractionSource() }
    if (!isStyled) {
        androidx.compose.material3.Surface(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            color = normalContainerColor,
            contentColor = normalContentColor,
            modifier = modifier,
        ) {
            Box(contentAlignment = Alignment.Center) {
                content()
            }
        }
        return
    }

    val effectiveTint = if (isNothingTheme() && prominent && glassTintColor == Color.Unspecified) {
        NothingVermilion
    } else {
        glassTintColor
    }
    val contentTint = styledContentColor(
        prominent = prominent,
        tintColor = effectiveTint,
        requested = normalContentColor,
    )
    Box(
        modifier = modifier
            .liquidGlassPressScale(src, enabled = enabled, pressedScale = 0.90f)
            .alpha(if (enabled) 1f else 0.45f)
            .dezzyStyledSurface(
                shape = shape,
                prominent = prominent,
                tintColor = effectiveTint,
                enableShadow = prominent,
            )
            .clickable(
                interactionSource = src,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentTint) {
            content()
        }
    }
}

/**
 * Apple iOS 26 Liquid Glass capsule switch and Nothing OS 3.0 Vermilion LED switch.
 */
@Composable
fun DezzySwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    if (!isCustomStyledTheme()) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            thumbContent = thumbContent,
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
        )
        return
    }

    val src = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by src.collectIsPressedAsState()

    if (isNothingTheme()) {
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val borderColor by animateColorAsState(
            targetValue = when {
                checked -> NothingVermilion
                isDark -> Color.White.copy(alpha = 0.35f)
                else -> NothingInkLight.copy(alpha = 0.35f)
            },
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "nothingSwitchBorder",
        )
        val thumbColor by animateColorAsState(
            targetValue = when {
                checked -> NothingVermilion
                isDark -> NothingBoneWhite.copy(alpha = 0.65f)
                else -> NothingInkLight.copy(alpha = 0.55f)
            },
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "nothingSwitchThumb",
        )
        val thumbSize by animateDpAsState(
            targetValue = if (isPressed && enabled) 19.dp else 18.dp,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "nothingSwitchThumbSize",
        )
        val thumbOffset by animateDpAsState(
            targetValue = if (checked) 25.dp else 3.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "nothingSwitchOffset",
        )
        val trackBg = if (isDark) NothingCharcoal else NothingCardLight

        Box(
            modifier = modifier
                .size(width = 50.dp, height = 28.dp)
                .alpha(if (enabled) 1f else 0.42f)
                .clip(CircleShape)
                .background(trackBg)
                .border(width = 1.2.dp, color = borderColor, shape = CircleShape)
                .then(
                    if (onCheckedChange != null) {
                        Modifier.toggleable(
                            value = checked,
                            enabled = enabled,
                            role = Role.Switch,
                            interactionSource = src,
                            indication = null,
                            onValueChange = onCheckedChange,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(thumbColor),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbContent != null) {
                    CompositionLocalProvider(LocalContentColor provides Color.White) {
                        thumbContent()
                    }
                }
            }
        }
        return
    }

    val colorScheme = MaterialTheme.colorScheme

    val trackTint by animateColorAsState(
        targetValue = when {
            checked -> colorScheme.primary
            else -> Color(0xFF90A0BE)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "switchTrack",
    )

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "switchThumbOffset",
    )
    val thumbWidth by animateDpAsState(
        targetValue = if (isPressed && enabled) 33.dp else 27.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "switchThumbW",
    )
    val thumbHeight by animateDpAsState(
        targetValue = if (isPressed && enabled) 25.dp else 22.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "switchThumbH",
    )

    val trackModifier = modifier
        .size(width = 52.dp, height = 30.dp)
        .alpha(if (enabled) 1f else 0.42f)
        .dezzyStyledSurface(
            shape = CircleShape,
            prominent = checked,
            tintColor = if (checked) trackTint else Color.Unspecified,
            useBackdrop = false,
            enableShadow = checked,
            refractionScale = 0.7f,
        )
        .then(
            if (onCheckedChange != null) {
                Modifier.toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    interactionSource = src,
                    indication = null,
                    onValueChange = onCheckedChange,
                )
            } else {
                Modifier
            },
        )
        .padding(horizontal = 3.5.dp, vertical = 3.dp)

    Box(
        modifier = trackModifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        val dropletShape = CircleShape
        val thumbBrush = if (isPressed) {
            Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.56f),
                    Color.White.copy(alpha = 0.14f),
                    Color(0xFF8BE2FF).copy(alpha = 0.24f),
                ),
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White,
                    Color(0xFFEAF2FF),
                ),
            )
        }
        val thumbBorder = if (isPressed) {
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color(0xFF8BE2FF).copy(alpha = 0.78f)))
        } else {
            Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.72f)))
        }
        val effectiveOffset = if (checked && isPressed) 12.dp else thumbOffset

        Box(
            modifier = Modifier
                .offset(x = effectiveOffset)
                .size(width = thumbWidth, height = thumbHeight)
                .shadow(elevation = if (isPressed) 6.dp else 3.dp, shape = dropletShape, clip = false)
                .clip(dropletShape)
                .background(brush = thumbBrush)
                .border(width = if (isPressed) 1.3.dp else 0.8.dp, brush = thumbBorder, shape = dropletShape),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbContent != null && !isPressed) {
                CompositionLocalProvider(LocalContentColor provides if (checked) colorScheme.primary else Color(0xFF344058)) {
                    thumbContent()
                }
            }
        }
    }
}

/**
 * Shared styled container for Settings / Accounts / Services cards and banners.
 */
@Composable
fun DezzyGlassContainer(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    prominent: Boolean = false,
    tintColor: Color = Color.Unspecified,
    useBackdrop: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val isStyled = isCustomStyledTheme()
    val src = remember { MutableInteractionSource() }
    val contentColor = styledContentColor(prominent = prominent, tintColor = tintColor)

    val containerModifier = modifier
        .then(if (onClick != null && isStyled) Modifier.liquidGlassPressScale(src, pressedScale = 0.985f) else Modifier)
        .then(
            if (isStyled) {
                Modifier.dezzyStyledSurface(
                    shape = shape,
                    prominent = prominent,
                    tintColor = tintColor,
                    useBackdrop = useBackdrop,
                    enableShadow = prominent,
                    refractionScale = 0.88f,
                )
            } else {
                Modifier.clip(shape)
            },
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = src,
                    indication = LocalIndication.current,
                    onClick = onClick,
                )
            } else {
                Modifier
            },
        )

    Box(modifier = containerModifier) {
        if (isStyled) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
            }
        } else {
            content()
        }
    }
}
