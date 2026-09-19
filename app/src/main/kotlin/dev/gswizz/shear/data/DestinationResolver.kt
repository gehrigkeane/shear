/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.data

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap

/** Turns the component the Sharesheet reports into something a person recognizes. */
class DestinationResolver(private val packageManager: PackageManager) {
    /** The activity's label, falling back to its application's label, or null when the package is unknown. */
    fun label(component: ComponentName): String? = runCatching {
        packageManager.getActivityInfo(component, 0).loadLabel(packageManager).toString()
    }
        .recoverCatching {
            packageManager.getApplicationInfo(component.packageName, 0).loadLabel(packageManager).toString()
        }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }

    /** The activity's launcher icon rendered small, or null when the package is gone. */
    fun icon(component: ComponentName): Bitmap? = runCatching {
        packageManager.getActivityIcon(component).toBitmap(ICON_PX, ICON_PX)
    }
        .getOrNull()

    private companion object {
        const val ICON_PX = 96
    }
}
