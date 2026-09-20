/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.withContext

/**
 * A sharesheet opened from Shear itself so the user can long-press Shear in it and choose Pin.
 *
 * Android offers no API to promote a share target; pinning is the user's gesture and the system remembers it for every
 * app's sharesheet. The sample link is sheared so the sheet shows a real result, but the demo is not a share: nothing
 * is recorded in history and no destination is asked for. Unlike the relay chooser, this one deliberately keeps Shear
 * listed.
 */
object PinDemo {
    /** A tracked link, so the demo shows a real clean. */
    const val SAMPLE_TEXT = "https://example.com/read?utm_source=shear&utm_medium=pin"

    /** Shears the sample and returns the chooser to start. */
    suspend fun share(context: Context, graph: AppGraph): Intent {
        val result = withContext(graph.defaultDispatcher) { graph.engine().clean(SAMPLE_TEXT) }
        ShareShortcut.reportUsed(context)
        return Choosers.forText(context, result.outputText, subject = null, eventId = null, excludeSelf = false)
    }
}
