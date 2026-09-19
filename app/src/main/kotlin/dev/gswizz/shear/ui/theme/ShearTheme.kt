/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/** Material 3 over Catppuccin: Mocha when the system is dark, Latte when it is light. */
@Composable
fun ShearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = shearFlavor().toColorScheme(), content = content)
}

/** The flavor in effect, for colors that are brand tokens rather than Material roles. */
@Composable
@ReadOnlyComposable
fun shearFlavor(): Flavor = if (isSystemInDarkTheme()) Catppuccin.Mocha else Catppuccin.Latte
