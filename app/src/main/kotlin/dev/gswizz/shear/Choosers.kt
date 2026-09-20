/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dev.gswizz.shear.core.TextResult
import dev.gswizz.shear.data.ShareStatus

/** Builds the sharesheet intents Shear launches. */
object Choosers {
    /**
     * A chooser over [text] as `text/plain`.
     *
     * With an [eventId] the chooser reports the chosen component so it can be attached to that history event; without
     * one nothing is recorded, which is what the pin demo wants. The relay passes [excludeSelf] so a user cannot loop
     * Shear into Shear; the pin demo keeps Shear listed so it can be long-pressed and pinned.
     */
    fun forText(
        context: Context,
        text: String,
        subject: CharSequence?,
        eventId: String?,
        excludeSelf: Boolean,
    ): Intent {
        val relay = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        if (subject != null) relay.putExtra(Intent.EXTRA_SUBJECT, subject)
        val callback = eventId?.let {
            PendingIntent.getBroadcast(
                context,
                it.hashCode(),
                ChosenComponentReceiver.callback(context, it),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val chooser = Intent.createChooser(relay, null, callback?.intentSender)
        if (excludeSelf) {
            val self = ComponentName(context, ShareReceiverActivity::class.java)
            chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(self))
        }
        return chooser
    }
}

/** The status a share earns from its cleaning result alone: no links, cleaned, or already clean. */
fun TextResult.toShareStatus(): ShareStatus =
    when {
        urls.isEmpty() -> ShareStatus.NO_URLS
        changed -> ShareStatus.CLEANED
        else -> ShareStatus.UNCHANGED
    }
