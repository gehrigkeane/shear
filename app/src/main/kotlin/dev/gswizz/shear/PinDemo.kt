/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.content.Intent

/**
 * A sharesheet opened from Shear itself so the user can long-press Shear in it and choose Pin.
 *
 * Android offers no API to promote a share target; pinning is the user's gesture and the system remembers it for every
 * app's sharesheet. Unlike the relay chooser, this one deliberately keeps Shear in the list.
 */
object PinDemo {
    /** A harmless link; picking Shear here simply cleans it and shares again. */
    const val SAMPLE_TEXT = "https://example.com/?utm_source=shear"

    fun chooser(): Intent =
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, SAMPLE_TEXT),
            null,
        )
}
