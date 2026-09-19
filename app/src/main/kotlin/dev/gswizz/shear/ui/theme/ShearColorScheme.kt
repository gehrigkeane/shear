/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Material 3 roles over a [Flavor]: mauve, blue, and peach lead; red is error; the neutrals stack from base to crust.
 *
 * Containers are their accent blended a quarter of the way out of base, and their content is the accent pulled most of
 * the way to text, so tinted surfaces stay legible in both flavors without a second set of hand-picked values.
 */
fun Flavor.toColorScheme(): ColorScheme {
    val onAccent = if (dark) crust else base
    return if (dark) {
        darkColorScheme(
            primary = mauve,
            onPrimary = onAccent,
            primaryContainer = container(mauve),
            onPrimaryContainer = onContainer(mauve),
            inversePrimary = Catppuccin.Latte.mauve,
            secondary = blue,
            onSecondary = onAccent,
            secondaryContainer = container(blue),
            onSecondaryContainer = onContainer(blue),
            tertiary = peach,
            onTertiary = onAccent,
            tertiaryContainer = container(peach),
            onTertiaryContainer = onContainer(peach),
            error = red,
            onError = onAccent,
            errorContainer = container(red),
            onErrorContainer = onContainer(red),
            background = base,
            onBackground = text,
            surface = base,
            onSurface = text,
            surfaceVariant = surface0,
            onSurfaceVariant = subtext0,
            surfaceTint = mauve,
            inverseSurface = text,
            inverseOnSurface = base,
            outline = overlay0,
            outlineVariant = surface2,
            scrim = crust,
            surfaceBright = surface0,
            surfaceDim = mantle,
            surfaceContainer = surface0,
            surfaceContainerHigh = surface1,
            surfaceContainerHighest = surface2,
            surfaceContainerLow = mantle,
            surfaceContainerLowest = crust,
        )
    } else {
        lightColorScheme(
            primary = mauve,
            onPrimary = onAccent,
            primaryContainer = container(mauve),
            onPrimaryContainer = onContainer(mauve),
            inversePrimary = Catppuccin.Mocha.mauve,
            secondary = blue,
            onSecondary = onAccent,
            secondaryContainer = container(blue),
            onSecondaryContainer = onContainer(blue),
            tertiary = peach,
            onTertiary = onAccent,
            tertiaryContainer = container(peach),
            onTertiaryContainer = onContainer(peach),
            error = red,
            onError = onAccent,
            errorContainer = container(red),
            onErrorContainer = onContainer(red),
            background = base,
            onBackground = text,
            surface = base,
            onSurface = text,
            surfaceVariant = surface0,
            onSurfaceVariant = subtext0,
            surfaceTint = mauve,
            inverseSurface = text,
            inverseOnSurface = base,
            outline = overlay0,
            outlineVariant = surface2,
            scrim = crust,
            surfaceBright = base,
            surfaceDim = surface0,
            surfaceContainer = surface0,
            surfaceContainerHigh = surface1,
            surfaceContainerHighest = surface2,
            surfaceContainerLow = mantle,
            surfaceContainerLowest = crust,
        )
    }
}

private fun Flavor.container(accent: Color): Color = lerp(base, accent, CONTAINER_TINT)

private fun Flavor.onContainer(accent: Color): Color = lerp(accent, text, ON_CONTAINER_PULL)

private const val CONTAINER_TINT = 0.25f
private const val ON_CONTAINER_PULL = 0.85f
