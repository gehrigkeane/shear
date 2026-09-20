/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat

/**
 * Shear's one sharing shortcut, the entry the sharesheet's direct-share row can show above the app grid.
 *
 * The row is ordered by the system's prediction service from how often each shortcut is used, so every share reports a
 * use; long-lived and rank zero are the only static hints the platform accepts. Placement is the system's call and is
 * not guaranteed.
 */
object ShareShortcut {
    const val ID = "shear"

    /** Matches the category in `res/xml/shortcuts.xml`, which routes the shortcut to the relay. */
    const val CATEGORY = "dev.gswizz.shear.SHARE"

    /** The full adaptive-icon canvas; the system keeps the central 72dp after masking. */
    private const val ADAPTIVE_DP = 108

    /** Installs or refreshes the shortcut. Idempotent; call at startup. */
    fun publish(context: Context) {
        ShortcutManagerCompat.setDynamicShortcuts(context, listOf(shortcut(context)))
    }

    /** The shortcut as published: the app's name and icon, routed to the relay by [CATEGORY]. */
    fun shortcut(context: Context): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, ID)
            .setShortLabel(context.getString(R.string.app_name))
            .setLongLabel(context.getString(R.string.shortcut_long_label))
            .setIcon(launcherIcon(context))
            .setIntent(Intent(Intent.ACTION_MAIN).setClass(context, MainActivity::class.java))
            .setCategories(setOf(CATEGORY))
            .setLongLived(true)
            .setRank(0)
            .build()

    /**
     * The launcher icon's layers flattened into one full-bleed bitmap that the system masks like an app icon.
     *
     * Handing the adaptive drawable over as a resource shows it unmasked, a raw square; drawing the layers ourselves
     * and declaring the result adaptive lets the sharesheet apply the same shape it uses everywhere else.
     */
    private fun launcherIcon(context: Context): IconCompat {
        val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val size = (ADAPTIVE_DP * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        for (layer in listOfNotNull(drawable.background, drawable.foreground)) {
            layer.setBounds(0, 0, size, size)
            layer.draw(canvas)
        }
        return IconCompat.createWithAdaptiveBitmap(bitmap)
    }

    /** Tells the system the shortcut was used, the signal that lifts it in the direct-share row. */
    fun reportUsed(context: Context) {
        ShortcutManagerCompat.reportShortcutUsed(context, ID)
    }
}
