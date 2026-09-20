/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.content.Context
import android.content.Intent

/**
 * A sharesheet opened from Shear itself so the user can long-press Shear in it and choose Pin.
 *
 * Android offers no API to promote a share target; pinning is the user's gesture and the system remembers it for every
 * app's sharesheet. The sample link goes out dirty: tapping Shear in this sheet is what cleans it, moment and all, so
 * the demo doubles as a first share. Nothing is recorded and no destination is asked for. Unlike the relay chooser,
 * this one deliberately keeps Shear listed.
 */
object PinDemo {
    /**
     * A link carrying two trackers the bundled rules remove, a campaign tag and a Facebook click id, and nothing else.
     */
    const val SAMPLE_TEXT = "https://example.com/read?utm_source=newsletter&fbclid=IwAR0pin"

    /** The chooser to start. */
    fun share(context: Context): Intent {
        ShareShortcut.reportUsed(context)
        return Choosers.forText(context, SAMPLE_TEXT, subject = null, eventId = null, excludeSelf = false)
    }
}
