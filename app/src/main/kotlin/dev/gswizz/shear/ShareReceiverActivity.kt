/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.animation.ValueAnimator
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
import dev.gswizz.shear.data.MomentStyle
import dev.gswizz.shear.data.Settings
import dev.gswizz.shear.data.ShareStatus
import dev.gswizz.shear.ui.moment.MomentHost
import dev.gswizz.shear.ui.moment.MomentSummary
import dev.gswizz.shear.ui.theme.ShearTheme
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
 *
 * Between the result and the chooser, unless turned off, a share moment plays: a short scene over the dimmed source app
 * that shows what happened, so the second sharesheet does not arrive unexplained. Back skips it. Leaving during it is
 * treated like leaving during resolution.
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

    /** True while a share moment is on screen and the chooser has not been launched. */
    private var presenting: Boolean = false

    private class InFlight(val result: TextResult, val settings: Settings)

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
            present(offline, offline.toShareStatus(), settings)
            return
        }
        inFlight = InFlight(offline, settings)
        isResolving = true
        setContent { ShearTheme { ResolvingOverlay(onCancel = ::cancelResolution) } }
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
            present(offline, ShareStatus.RESOLUTION_INCOMPLETE, settings)
        } else {
            val status =
                if (resolved.urls.any { it.failure != null }) ShareStatus.RESOLUTION_INCOMPLETE
                else resolved.toShareStatus()
            present(resolved, status, settings)
        }
    }

    /** Abandons the network resolution; the offline result is shared instead. */
    internal fun cancelResolution() {
        resolutionJob?.cancel()
    }

    override fun onStop() {
        super.onStop()
        val pending = inFlight
        if ((isResolving || presenting) && !isFinishing && pending != null) {
            isResolving = false
            presenting = false
            inFlight = null
            mainJob?.cancel()
            graph.history.record(eventId, text, pending.result, ShareStatus.CANCELLED, pending.settings)
            finish()
        }
    }

    /** Plays the share moment before [share], or goes straight there when it is off or animations are disabled. */
    private fun present(result: TextResult, status: ShareStatus, settings: Settings) {
        val style = settings.moment
        if (style == MomentStyle.OFF || !ValueAnimator.areAnimatorsEnabled()) {
            share(result, status, settings)
            return
        }
        inFlight = InFlight(result, settings)
        presenting = true
        setContent {
            ShearTheme {
                MomentHost(
                    style = style,
                    summary = MomentSummary.of(result),
                    onFinished = { finishMoment(result, status, settings) },
                )
            }
        }
    }

    /** Ends the moment once, whether the scene ran out or the user pressed back. */
    private fun finishMoment(result: TextResult, status: ShareStatus, settings: Settings) {
        if (!presenting) return
        presenting = false
        inFlight = null
        share(result, status, settings)
    }

    private fun share(result: TextResult, status: ShareStatus, settings: Settings) {
        graph.history.record(eventId, text, result, status, settings)
        startActivity(Choosers.forText(this, result.outputText, subject, eventId, excludeSelf = true))
        finish()
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
