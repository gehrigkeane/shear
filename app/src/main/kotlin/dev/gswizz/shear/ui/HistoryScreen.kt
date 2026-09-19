/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import android.content.ComponentName
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gswizz.shear.AppGraph
import dev.gswizz.shear.R
import dev.gswizz.shear.data.DestinationResolver
import dev.gswizz.shear.data.HistoryRetention
import dev.gswizz.shear.data.ShareEventEntity
import dev.gswizz.shear.data.ShareStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DestinationUi(val label: String, val component: String, val icon: ImageBitmap?)

data class HistoryRow(
    val id: String,
    val time: Instant,
    /** Hosts of the cleaned URLs, comma separated; empty when the share had no links. */
    val summary: String,
    val urlCount: Int,
    val destination: DestinationUi?,
    val status: ShareStatus,
)

data class DaySection(val date: LocalDate, val rows: List<HistoryRow>)

data class HistoryUiState(
    val sections: List<DaySection> = emptyList(),
    val rulesUnavailable: Boolean = false,
    val retentionOff: Boolean = false,
    /** The one-time hint about pinning Shear in the sharesheet. */
    val showPinPrompt: Boolean = false,
    val loading: Boolean = true,
)

/** History grouped by day, newest first, with the engine's health and the retention setting alongside. */
class HistoryViewModel(private val graph: AppGraph) : ViewModel() {
    private val health = flow { emit(!graph.engine().rulesHealthy) }

    val state: StateFlow<HistoryUiState> =
        combine(graph.history.observeEvents(), graph.settings.settings, health) { events, settings, unhealthy ->
                HistoryUiState(
                    sections = sections(events, graph.destinations),
                    rulesUnavailable = unhealthy,
                    retentionOff = settings.retention == HistoryRetention.OFF,
                    showPinPrompt = !settings.pinPromptSeen,
                    loading = false,
                )
            }
            .flowOn(graph.defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HistoryUiState())

    fun dismissPinPrompt() {
        viewModelScope.launch { graph.settings.setPinPromptSeen() }
    }

    private fun sections(events: List<ShareEventEntity>, destinations: DestinationResolver): List<DaySection> =
        events
            .map { it.toRow(destinations) }
            .groupBy { it.time.atZone(ZoneId.systemDefault()).toLocalDate() }
            .map { (date, rows) -> DaySection(date, rows) }
            .sortedByDescending { it.date }

    private fun ShareEventEntity.toRow(destinations: DestinationResolver): HistoryRow {
        val summary = ShareSummary.of(cleanedText)
        val component = destinationComponent?.let(ComponentName::unflattenFromString)
        val destination =
            if (component != null) {
                DestinationUi(
                    label = destinationLabel ?: component.packageName,
                    component = destinationComponent.orEmpty(),
                    icon = destinations.icon(component)?.asImageBitmap(),
                )
            } else {
                null
            }
        return HistoryRow(id, Instant.ofEpochMilli(timestamp), summary.hosts, summary.urlCount, destination, status)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun HistoryRoute(
    graph: AppGraph,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    onPinDemo: () -> Unit,
    modifier: Modifier = Modifier,
    shared: SharedScopes? = null,
    viewModel: HistoryViewModel = viewModel { HistoryViewModel(graph) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryScreen(
        state = state,
        onOpen = onOpen,
        onSettings = onSettings,
        modifier = modifier,
        shared = shared,
        onPinDemo = {
            onPinDemo()
            viewModel.dismissPinPrompt()
        },
        onDismissPin = viewModel::dismissPinPrompt,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    shared: SharedScopes? = null,
    onPinDemo: () -> Unit = {},
    onDismissPin: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Wordmark(modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).heightIn(max = 48.dp)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.rulesUnavailable) Banner(text = stringResource(R.string.rules_banner), error = true)
            if (state.retentionOff) Banner(text = stringResource(R.string.history_retention_off), error = false)
            if (state.showPinPrompt) PinCard(onShow = onPinDemo, onDismiss = onDismissPin)
            when {
                state.loading -> Unit
                state.sections.isEmpty() -> EmptyState(modifier = Modifier.fillMaxSize())
                else ->
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        for (section in state.sections) {
                            stickyHeader(key = section.date.toString()) { DayHeader(date = section.date) }
                            items(section.rows, key = { it.id }) { row ->
                                HistoryRowItem(row = row, onClick = { onOpen(row.id) }, shared = shared)
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun Banner(text: String, error: Boolean, modifier: Modifier = Modifier) {
    val container =
        if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Text(text = text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

/** The one-time nudge toward pinning: what pinning does, then a demo sharesheet to do it in, or dismissal. */
@Composable
private fun PinCard(onShow: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
            Text(text = stringResource(R.string.pin_card_title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(R.string.pin_card_body),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.dismiss)) }
                TextButton(onClick = onShow) { Text(text = stringResource(R.string.pin_show_me)) }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.history_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A day's sticky header. It carries the surface color so rows scrolling beneath it do not show through. */
@Composable
private fun DayHeader(date: LocalDate, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Text(
            text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun HistoryRowItem(
    row: HistoryRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shared: SharedScopes? = null,
) {
    val time = row.time.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    ListItem(
        headlineContent = {
            Text(
                text = row.summary.ifEmpty { stringResource(R.string.no_links) },
                modifier = Modifier.sharedBoundsIn(shared, "summary/${row.id}"),
            )
        },
        supportingContent = { Text(text = row.destination?.label ?: stringResource(R.string.destination_unknown)) },
        leadingContent = {
            val icon = row.destination?.icon
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.sharedElementIn(shared, "icon/${row.id}").size(40.dp),
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(text = time, style = MaterialTheme.typography.labelMedium)
                StatusBadge(status = row.status)
            }
        },
        modifier = modifier.clickable(onClickLabel = stringResource(R.string.open_event), onClick = onClick),
    )
}
