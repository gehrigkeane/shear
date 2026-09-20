/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import android.content.ClipData
import android.content.ComponentName
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gswizz.shear.AppGraph
import dev.gswizz.shear.R
import dev.gswizz.shear.core.engine.RedirectKind
import dev.gswizz.shear.core.engine.RuleApplication
import dev.gswizz.shear.core.engine.RuleSource
import dev.gswizz.shear.core.engine.UrlCleanResult
import dev.gswizz.shear.data.DestinationResolver
import dev.gswizz.shear.data.EventWithTraces
import dev.gswizz.shear.data.ShareStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface EventDetailUiState {
    data object Loading : EventDetailUiState

    data object Missing : EventDetailUiState

    data class Loaded(val event: EventDetail) : EventDetailUiState
}

data class EventDetail(
    val id: String,
    val timestamp: Instant,
    val originalText: String?,
    val originalHash: String,
    val cleanedText: String,
    /** Hosts of the cleaned links; empty when there were none. */
    val summary: String,
    val status: ShareStatus,
    val rulesVersion: String,
    val destination: DestinationUi?,
    val traces: List<TraceUi>,
)

/** One URL's story in words: the rules that fired, what they removed, and where redirects led. */
data class TraceUi(
    val originalUrl: String?,
    val finalUrl: String,
    val rules: List<String>,
    val removedParams: List<String>,
    val redirectSteps: List<String>,
    val failure: String?,
)

class EventDetailViewModel(graph: AppGraph, eventId: String) : ViewModel() {
    val state: StateFlow<EventDetailUiState> =
        graph.history
            .observeEvent(eventId)
            .map { stored ->
                stored?.let { EventDetailUiState.Loaded(it.toDetail(graph.destinations)) } ?: EventDetailUiState.Missing
            }
            .flowOn(graph.defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EventDetailUiState.Loading)

    private fun EventWithTraces.toDetail(destinations: DestinationResolver): EventDetail {
        val component = event.destinationComponent?.let(ComponentName::unflattenFromString)
        return EventDetail(
            id = event.id,
            timestamp = Instant.ofEpochMilli(event.timestamp),
            originalText = event.originalText,
            originalHash = event.originalHash,
            cleanedText = event.cleanedText,
            summary = ShareSummary.of(event.cleanedText).hosts,
            status = event.status,
            rulesVersion = event.rulesVersion,
            destination =
                component?.let {
                    DestinationUi(
                        event.destinationLabel ?: it.packageName,
                        event.destinationComponent.orEmpty(),
                        destinations.icon(it)?.asImageBitmap(),
                    )
                },
            traces =
                traces.map {
                    TraceUi(
                        it.originalUrl,
                        it.finalUrl,
                        it.result.ruleLines(),
                        it.result.removedParameters.map { p -> p.token },
                        it.result.redirectLines(),
                        it.failure,
                    )
                },
        )
    }

    private fun UrlCleanResult.ruleLines(): List<String> = applications.map { it.describe() }

    private fun RuleApplication.describe(): String = buildString {
        append(
            when (source) {
                RuleSource.DEBOUNCE -> "debounce"
                RuleSource.QUERY_FILTER -> "query-filter"
                RuleSource.CLEAN_URLS -> "clean-urls"
            }
        )
        if (ruleIndex < 0) append(" conditional tracker") else append(" rule #").append(ruleIndex)
        action?.let { append(" (").append(it).append(')') }
    }

    private fun UrlCleanResult.redirectLines(): List<String> = redirects.map { step ->
        val kind = if (step.kind == RedirectKind.OFFLINE) "offline" else "network ${step.status ?: ""}".trim()
        "$kind: ${step.fromUrl} → ${step.toUrl}"
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun EventDetailRoute(
    graph: AppGraph,
    eventId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EventDetailViewModel = viewModel(key = eventId) { EventDetailViewModel(graph, eventId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EventDetailScreen(state = state, onBack = onBack, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    state: EventDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val event = (state as? EventDetailUiState.Loaded)?.event
    Scaffold(
        modifier = modifier,
        topBar = {
            val back: @Composable () -> Unit = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            }
            if (event == null) {
                TopAppBar(title = { Text(text = stringResource(R.string.detail_title)) }, navigationIcon = back)
            } else {
                EventHeader(event = event, navigationIcon = back)
            }
        },
    ) { padding ->
        when (state) {
            EventDetailUiState.Loading -> Unit
            EventDetailUiState.Missing ->
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.detail_missing),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            is EventDetailUiState.Loaded -> EventDetailBody(event = state.event, modifier = Modifier.padding(padding))
        }
    }
}

/**
 * The history row again, as an app bar: hosts for the title, time and destination beneath, the app's icon at the end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventHeader(
    event: EventDetail,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val time =
        event.timestamp
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
    val destination = event.destination?.label ?: stringResource(R.string.destination_unknown)
    TopAppBar(
        title = {
            Column {
                Text(
                    text = event.summary.ifEmpty { stringResource(R.string.no_links) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "$time · $destination",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = {
            val icon = event.destination?.icon
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp).size(32.dp),
                )
            }
        },
    )
}

@Composable
private fun EventDetailBody(event: EventDetail, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copy: (String, String) -> Unit = { label, text ->
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text))) }
    }
    val originalTitle = stringResource(R.string.detail_original)
    val sharedTitle = stringResource(R.string.detail_cleaned)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatusBadge(status = event.status) }
        item {
            val original = event.originalText
            Section(title = originalTitle, onCopy = original?.let { { copy(originalTitle, it) } }) {
                Text(
                    text = original ?: stringResource(R.string.detail_original_not_retained, event.originalHash),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        // A share that left the text alone has nothing to show twice; the original stands for both. When the
        // original was not kept, the sheared text is the only text there is, so it stays.
        if (event.cleanedText != event.originalText) {
            item {
                Section(title = sharedTitle, onCopy = { copy(sharedTitle, event.cleanedText) }) {
                    Text(text = event.cleanedText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(event.traces.withIndex().toList(), key = { it.index }) { (index, trace) ->
            TraceCard(index = index + 1, trace = trace)
        }
        item {
            Text(
                text = stringResource(R.string.detail_rules_version, event.rulesVersion),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A titled card. With [onCopy] the whole card is a tap target that copies, and the title row shows a copy glyph. */
@Composable
private fun Section(
    title: String,
    modifier: Modifier = Modifier,
    onCopy: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val tappable =
        if (onCopy != null) {
            Modifier.clip(CardDefaults.shape).clickable(onClickLabel = stringResource(R.string.copy), onClick = onCopy)
        } else {
            Modifier
        }
    Card(modifier = modifier.fillMaxWidth().then(tappable)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (onCopy != null) {
                    Icon(
                        painter = painterResource(R.drawable.ic_content_copy),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun TraceCard(index: Int, trace: TraceUi, modifier: Modifier = Modifier) {
    Section(title = stringResource(R.string.detail_trace_title, index), modifier = modifier) {
        trace.originalUrl?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = trace.finalUrl, style = MaterialTheme.typography.bodyMedium)
        Text(text = stringResource(R.string.detail_rules_applied), style = MaterialTheme.typography.labelLarge)
        if (trace.rules.isEmpty())
            Text(text = stringResource(R.string.detail_nothing_applied), style = MaterialTheme.typography.bodySmall)
        for (rule in trace.rules) Text(text = rule, style = MaterialTheme.typography.bodySmall)
        if (trace.removedParams.isNotEmpty()) {
            Text(text = stringResource(R.string.detail_removed), style = MaterialTheme.typography.labelLarge)
            for (param in trace.removedParams) Text(text = param, style = MaterialTheme.typography.bodySmall)
        }
        if (trace.redirectSteps.isNotEmpty()) {
            Text(text = stringResource(R.string.detail_redirects), style = MaterialTheme.typography.labelLarge)
            for (step in trace.redirectSteps) Text(text = step, style = MaterialTheme.typography.bodySmall)
        }
        trace.failure?.let {
            Text(
                text = stringResource(R.string.detail_failure, it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
