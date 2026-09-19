/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.chooser.ChooserResult
import androidx.core.net.toUri

/**
 * Receives the Sharesheet's report of which app the user picked and attaches it to the share event.
 *
 * Modern share sheets answer with a [ChooserResult]; older ones with the chosen component alone, and both are read.
 * Copy, edit, and other non-app actions carry no component and are ignored. The callback intent carries the event id in
 * its data URI, so this works even if Shear's process died between the share and the choice. It never influences the
 * chooser; it only records.
 */
class ChosenComponentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val component = chosenComponent(intent) ?: return
        val eventId = intent.data?.lastPathSegment ?: return
        val graph = (context.applicationContext as ShearApplication).graph
        val pending: PendingResult? = goAsync()
        graph.history
            .recordDestination(eventId, component, graph.destinations.label(component), System.currentTimeMillis())
            .invokeOnCompletion { pending?.finish() }
    }

    private fun chosenComponent(intent: Intent): ComponentName? {
        val result = intent.getParcelableExtra(Intent.EXTRA_CHOOSER_RESULT, ChooserResult::class.java)
        if (result != null) {
            return result.selectedComponent?.takeIf { result.type == ChooserResult.CHOOSER_RESULT_SELECTED_COMPONENT }
        }
        return intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java)
    }

    companion object {
        /** The explicit callback intent for [eventId]; the data URI makes each event's PendingIntent distinct. */
        fun callback(context: Context, eventId: String): Intent =
            Intent(context, ChosenComponentReceiver::class.java).setData("shear://event/$eventId".toUri())
    }
}
