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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.gswizz.shear.core.TextResult
import dev.gswizz.shear.core.net.ResolveMode
import dev.gswizz.shear.data.Settings
import dev.gswizz.shear.data.ShareStatus
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The share target: receives text, cleans it, records the event, and relays the result to the native Sharesheet.
 *
 * The activity is translucent and, on the offline path, finishes before a frame is drawn. When the user has enabled
 * redirect resolution and a URL would actually be fetched, a small progress surface appears until resolution finishes,
 * is cancelled, or runs out of its budget, and the offline result is shared in the latter two cases. Leaving the app
 * mid-resolution records a cancelled event and shares nothing, since launching the chooser from the background is not
 * permitted. The event id is allocated up front and travels inside the chooser callback so the chosen destination can
 * be attached later, and Shear excludes itself from the chooser so a user cannot loop.
 */
class ShareReceiverActivity : ComponentActivity() {
    private lateinit var graph: AppGraph
    private lateinit var text: String
    private lateinit var eventId: String
    private var subject: CharSequence? = null
    private var mainJob: Job? = null
    private var resolutionJob: Job? = null
    private var inFlight: InFlight? = null

    /** True while the progress surface is up and a network resolution is running. */
    internal var isResolving: Boolean = false
        private set

    private class InFlight(val offline: TextResult, val settings: Settings)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared =
            intent?.takeIf { it.action == Intent.ACTION_SEND }?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (shared == null) {
            Toast.makeText(this, R.string.share_text_only, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        text = shared
        subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)
        eventId = UUID.randomUUID().toString()
        graph = (application as ShearApplication).graph
        mainJob = lifecycleScope.launch { run() }
    }

    private suspend fun run() {
        val settings = graph.settings.settings.first()
        val engine = graph.engine()
        if (!engine.rulesHealthy) {
            Toast.makeText(this, R.string.rules_unavailable, Toast.LENGTH_LONG).show()
            share(TextResult(text, text, emptyList()), ShareStatus.RULES_UNAVAILABLE, settings)
            return
        }
        val offline = withContext(graph.defaultDispatcher) { engine.clean(text) }
        val mode = settings.redirectMode
        if (mode == ResolveMode.OFF || !engine.needsNetwork(text, mode)) {
            share(offline, statusOf(offline), settings)
            return
        }
        inFlight = InFlight(offline, settings)
        isResolving = true
        setContent { MaterialTheme { ResolvingOverlay(onCancel = ::cancelResolution) } }
        val resolved =
            withTimeoutOrNull(RESOLUTION_BUDGET_MS) {
                val job = async(graph.defaultDispatcher) { engine.process(text, mode) }
                resolutionJob = job
                try {
                    job.await()
                } catch (e: CancellationException) {
                    if (!isActive) throw e
                    null
                }
            }
        isResolving = false
        inFlight = null
        if (resolved == null) {
            share(offline, ShareStatus.RESOLUTION_INCOMPLETE, settings)
        } else {
            val status =
                if (resolved.urls.any { it.failure != null }) ShareStatus.RESOLUTION_INCOMPLETE else statusOf(resolved)
            share(resolved, status, settings)
        }
    }

    /** Abandons the network resolution; the offline result is shared instead. */
    internal fun cancelResolution() {
        resolutionJob?.cancel()
    }

    override fun onStop() {
        super.onStop()
        val pending = inFlight
        if (isResolving && !isFinishing && pending != null) {
            isResolving = false
            inFlight = null
            mainJob?.cancel()
            graph.history.record(eventId, text, pending.offline, ShareStatus.CANCELLED, pending.settings)
            finish()
        }
    }

    private fun share(result: TextResult, status: ShareStatus, settings: Settings) {
        graph.history.record(eventId, text, result, status, settings)
        startActivity(chooser(result.outputText, subject, eventId))
        finish()
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

    companion object {
        /** The whole resolution, all hops included, gets this long before the offline result is shared instead. */
        const val RESOLUTION_BUDGET_MS = 8_000L
    }
}

/** The only thing Shear ever draws during a share: progress and a way out. Back does the same as the button. */
@Composable
private fun ResolvingOverlay(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onCancel)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text(text = stringResource(R.string.resolving), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onCancel) { Text(text = stringResource(R.string.share_without_resolving)) }
            }
        }
    }
}
