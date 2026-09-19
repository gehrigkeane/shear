/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.gswizz.shear.core.TextResult
import dev.gswizz.shear.data.ShareStatus
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The share target: receives text, cleans it, records the event, and relays the result to the native Sharesheet.
 *
 * The activity is translucent and normally finishes before a frame is drawn. The event id is allocated up front and
 * travels inside the chooser callback, so the destination the user picks can be attached later without any in-memory
 * state. Shear excludes itself from the chooser so a user cannot loop.
 */
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text =
            intent?.takeIf { it.action == Intent.ACTION_SEND }?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (text == null) {
            Toast.makeText(this, R.string.share_text_only, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)
        val eventId = UUID.randomUUID().toString()
        val graph = (application as ShearApplication).graph
        lifecycleScope.launch {
            val settings = graph.settings.settings.first()
            val engine = graph.engine()
            val (result, status) =
                if (engine.rulesHealthy) {
                    val cleaned = withContext(graph.defaultDispatcher) { engine.clean(text) }
                    cleaned to statusOf(cleaned)
                } else {
                    Toast.makeText(this@ShareReceiverActivity, R.string.rules_unavailable, Toast.LENGTH_LONG).show()
                    TextResult(text, text, emptyList()) to ShareStatus.RULES_UNAVAILABLE
                }
            graph.history.record(eventId, text, result, status, settings)
            startActivity(chooser(result.outputText, subject, eventId))
            finish()
        }
    }

    private fun statusOf(result: TextResult): ShareStatus =
        when {
            result.urls.isEmpty() -> ShareStatus.NO_URLS
            result.changed -> ShareStatus.CLEANED
            else -> ShareStatus.UNCHANGED
        }

    private fun chooser(text: String, subject: CharSequence?, eventId: String): Intent {
        val relay = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        if (subject != null) relay.putExtra(Intent.EXTRA_SUBJECT, subject)
        val callback =
            PendingIntent.getBroadcast(
                this,
                eventId.hashCode(),
                ChosenComponentReceiver.callback(this, eventId),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val self = ComponentName(this, ShareReceiverActivity::class.java)
        return Intent.createChooser(relay, null, callback.intentSender)
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(self))
    }
}
