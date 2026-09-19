/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * One Catppuccin flavor: fourteen accents and twelve neutrals, from brightest text to deepest crust.
 *
 * Shear's palette is Catppuccin (https://catppuccin.com, MIT). These are the only hex values in the app; every color on
 * screen is a role in [toColorScheme] or a [Brand] token over one of these.
 */
data class Flavor(
    val name: String,
    val dark: Boolean,
    val rosewater: Color,
    val flamingo: Color,
    val pink: Color,
    val mauve: Color,
    val red: Color,
    val maroon: Color,
    val peach: Color,
    val yellow: Color,
    val green: Color,
    val teal: Color,
    val sky: Color,
    val sapphire: Color,
    val blue: Color,
    val lavender: Color,
    val text: Color,
    val subtext1: Color,
    val subtext0: Color,
    val overlay2: Color,
    val overlay1: Color,
    val overlay0: Color,
    val surface2: Color,
    val surface1: Color,
    val surface0: Color,
    val base: Color,
    val mantle: Color,
    val crust: Color,
) {
    /** The accents in canonical order, for anything that wants the whole rainbow. */
    val accents: List<Color>
        get() =
            listOf(
                rosewater,
                flamingo,
                pink,
                mauve,
                red,
                maroon,
                peach,
                yellow,
                green,
                teal,
                sky,
                sapphire,
                blue,
                lavender,
            )

    override fun toString(): String = name
}

/** The two flavors Shear ships: Mocha for dark, Latte for light. */
object Catppuccin {
    val Mocha =
        Flavor(
            name = "Mocha",
            dark = true,
            rosewater = Color(0xFFF5E0DC),
            flamingo = Color(0xFFF2CDCD),
            pink = Color(0xFFF5C2E7),
            mauve = Color(0xFFCBA6F7),
            red = Color(0xFFF38BA8),
            maroon = Color(0xFFEBA0AC),
            peach = Color(0xFFFAB387),
            yellow = Color(0xFFF9E2AF),
            green = Color(0xFFA6E3A1),
            teal = Color(0xFF94E2D5),
            sky = Color(0xFF89DCEB),
            sapphire = Color(0xFF74C7EC),
            blue = Color(0xFF89B4FA),
            lavender = Color(0xFFB4BEFE),
            text = Color(0xFFCDD6F4),
            subtext1 = Color(0xFFBAC2DE),
            subtext0 = Color(0xFFA6ADC8),
            overlay2 = Color(0xFF9399B2),
            overlay1 = Color(0xFF7F849C),
            overlay0 = Color(0xFF6C7086),
            surface2 = Color(0xFF585B70),
            surface1 = Color(0xFF45475A),
            surface0 = Color(0xFF313244),
            base = Color(0xFF1E1E2E),
            mantle = Color(0xFF181825),
            crust = Color(0xFF11111B),
        )

    val Latte =
        Flavor(
            name = "Latte",
            dark = false,
            rosewater = Color(0xFFDC8A78),
            flamingo = Color(0xFFDD7878),
            pink = Color(0xFFEA76CB),
            mauve = Color(0xFF8839EF),
            red = Color(0xFFD20F39),
            maroon = Color(0xFFE64553),
            peach = Color(0xFFFE640B),
            yellow = Color(0xFFDF8E1D),
            green = Color(0xFF40A02B),
            teal = Color(0xFF179299),
            sky = Color(0xFF04A5E5),
            sapphire = Color(0xFF209FB5),
            blue = Color(0xFF1E66F5),
            lavender = Color(0xFF7287FD),
            text = Color(0xFF4C4F69),
            subtext1 = Color(0xFF5C5F77),
            subtext0 = Color(0xFF6C6F85),
            overlay2 = Color(0xFF7C7F93),
            overlay1 = Color(0xFF8C8FA1),
            overlay0 = Color(0xFF9CA0B0),
            surface2 = Color(0xFFACB0BE),
            surface1 = Color(0xFFBCC0CC),
            surface0 = Color(0xFFCCD0DA),
            base = Color(0xFFEFF1F5),
            mantle = Color(0xFFE6E9EF),
            crust = Color(0xFFDCE0E8),
        )
}
