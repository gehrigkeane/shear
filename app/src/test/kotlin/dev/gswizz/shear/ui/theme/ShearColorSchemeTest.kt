/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShearColorSchemeTest {
    @Test
    fun `mocha is dark and latte is light`() {
        assertTrue(Catppuccin.Mocha.toColorScheme().background.luminance() < 0.2f)
        assertTrue(Catppuccin.Latte.toColorScheme().background.luminance() > 0.8f)
    }

    @Test
    fun `roles map to the flavor`() {
        val scheme = Catppuccin.Mocha.toColorScheme()
        assertEquals(Catppuccin.Mocha.mauve, scheme.primary)
        assertEquals(Catppuccin.Mocha.blue, scheme.secondary)
        assertEquals(Catppuccin.Mocha.peach, scheme.tertiary)
        assertEquals(Catppuccin.Mocha.red, scheme.error)
        assertEquals(Catppuccin.Mocha.base, scheme.surface)
        assertEquals(Catppuccin.Mocha.text, scheme.onSurface)
        assertEquals(Catppuccin.Mocha.surface2, scheme.surfaceContainerHighest)
    }

    @Test
    fun `containers read against their content in both flavors`() {
        for (flavor in listOf(Catppuccin.Mocha, Catppuccin.Latte)) {
            val scheme = flavor.toColorScheme()
            val pairs =
                listOf(
                    scheme.primaryContainer to scheme.onPrimaryContainer,
                    scheme.secondaryContainer to scheme.onSecondaryContainer,
                    scheme.tertiaryContainer to scheme.onTertiaryContainer,
                    scheme.errorContainer to scheme.onErrorContainer,
                    scheme.primary to scheme.onPrimary,
                    scheme.surface to scheme.onSurface,
                )
            for ((container, content) in pairs) {
                assertNotEquals(container, content)
                assertTrue("$flavor $container vs $content", contrast(container, content) >= 3f)
            }
        }
    }

    @Test
    fun `the accent list holds the fourteen accents`() {
        assertEquals(14, Catppuccin.Mocha.accents.size)
        assertTrue(Catppuccin.Mocha.rosewater in Catppuccin.Mocha.accents)
        assertTrue(Catppuccin.Mocha.lavender in Catppuccin.Mocha.accents)
        assertTrue(Catppuccin.Mocha.base !in Catppuccin.Mocha.accents)
    }

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return if (abs(la) > abs(lb)) la / lb else lb / la
    }
}
