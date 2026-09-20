/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.gswizz.shear.ui.drawScissors
import dev.gswizz.shear.ui.theme.Catppuccin

/**
 * Shear's one sharing shortcut, the entry the sharesheet's direct-share row can show above the app grid.
 *
 * The row is ordered by the system's prediction service from how often each shortcut is used, so every share reports a
 * use; long-lived and rank zero are the only static hints the platform accepts. Placement is the system's call and is
 * not guaranteed.
 *
 * The sharesheet badges every direct-share entry with its app's icon, so the shortcut shows the tool rather than the
 * animal: scissors, with the shorn launcher sheep as the badge. The scissors lean away from that corner.
 */
object ShareShortcut {
    const val ID = "shear"

    /** Matches the category in `res/xml/shortcuts.xml`, which routes the shortcut to the relay. */
    const val CATEGORY = "dev.gswizz.shear.SHARE"

    /** The full adaptive-icon canvas; the system keeps the central 72dp after masking. */
    private const val ADAPTIVE_DP = 108

    /** The launcher icon's palette; icons cannot follow the theme, so like the launcher this one is always Mocha. */
    private val flavor = Catppuccin.Mocha

    /** Installs or refreshes the shortcut. Idempotent; call at startup. */
    fun publish(context: Context) {
        ShortcutManagerCompat.setDynamicShortcuts(context, listOf(shortcut(context)))
    }

    /** The shortcut as published: the app's name and icon, routed to the relay by [CATEGORY]. */
    fun shortcut(context: Context): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, ID)
            .setShortLabel(context.getString(R.string.app_name))
            .setLongLabel(context.getString(R.string.shortcut_long_label))
            .setIcon(IconCompat.createWithAdaptiveBitmap(icon(context)))
            .setIntent(Intent(Intent.ACTION_MAIN).setClass(context, MainActivity::class.java))
            .setCategories(setOf(CATEGORY))
            .setLongLived(true)
            .setRank(0)
            .build()

    /**
     * The scissors on the launcher background, full bleed, for the system to mask like an app icon.
     *
     * Handing a drawable resource over would show it unmasked, a raw square; a bitmap declared adaptive lets the
     * sharesheet apply the same shape it uses everywhere else.
     */
    fun icon(context: Context): Bitmap {
        val density = context.resources.displayMetrics.density
        val side = (ADAPTIVE_DP * density).toInt().coerceAtLeast(1)
        val image = ImageBitmap(side, side)
        val bounds = Rect(Offset.Zero, Size(side.toFloat(), side.toFloat()))
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(image), bounds.size) {
            drawRect(color = Color(context.getColor(R.color.ic_launcher_background)))
            drawScissors(bounds = bounds, flavor = flavor)
        }
        return image.asAndroidBitmap()
    }

    /** Tells the system the shortcut was used, the signal that lifts it in the direct-share row. */
    fun reportUsed(context: Context) {
        ShortcutManagerCompat.reportShortcutUsed(context, ID)
    }
}
