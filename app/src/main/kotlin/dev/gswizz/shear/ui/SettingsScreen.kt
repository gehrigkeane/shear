/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gswizz.shear.AppGraph
import dev.gswizz.shear.R
import dev.gswizz.shear.core.net.ResolveMode
import dev.gswizz.shear.data.HistoryRetention
import dev.gswizz.shear.data.MomentStyle
import dev.gswizz.shear.data.Settings
import dev.gswizz.shear.ui.moment.MomentPreviewDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The two destructive choices that ask before acting. */
enum class Confirmation {
    CLEAR_HISTORY,
    RETENTION_OFF,
}

data class SettingsUiState(
    val settings: Settings = Settings(),
    val rulesVersion: String = "",
    val rulesUpstream: String = UPSTREAM,
    val appVersion: String = "",
    val confirmation: Confirmation? = null,
    /** The share moment being previewed full screen, if any. */
    val preview: MomentStyle? = null,
) {
    companion object {
        const val UPSTREAM = "https://github.com/brave/adblock-lists"
    }
}

data class SettingsCallbacks(
    val onRedirectMode: (ResolveMode) -> Unit,
    val onRetention: (HistoryRetention) -> Unit,
    val onRetainOriginals: (Boolean) -> Unit,
    val onClearHistory: () -> Unit,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
    val onOpenUpstream: () -> Unit,
    val onPinDemo: () -> Unit,
    val onMoment: (MomentStyle) -> Unit,
    val onPreview: () -> Unit,
    val onDismissPreview: () -> Unit,
)

class SettingsViewModel(private val graph: AppGraph, appVersion: String) : ViewModel() {
    private val confirmation = MutableStateFlow<Confirmation?>(null)
    private val preview = MutableStateFlow<MomentStyle?>(null)
    private val rulesVersion = flow { emit(graph.engine().rulesVersion) }

    val state: StateFlow<SettingsUiState> =
        combine(graph.settings.settings, confirmation, rulesVersion, preview) { settings, pending, version, shown ->
                SettingsUiState(
                    settings = settings,
                    rulesVersion = version,
                    appVersion = appVersion,
                    confirmation = pending,
                    preview = shown,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                SettingsUiState(appVersion = appVersion),
            )

    fun setRedirectMode(mode: ResolveMode) {
        viewModelScope.launch { graph.settings.setRedirectMode(mode) }
    }

    /** Turning history off deletes what exists, so it asks first; the other choices apply at once. */
    fun requestRetention(retention: HistoryRetention) {
        if (retention == HistoryRetention.OFF) confirmation.value = Confirmation.RETENTION_OFF
        else viewModelScope.launch { graph.settings.setRetention(retention) }
    }

    fun setRetainOriginals(retain: Boolean) {
        viewModelScope.launch { graph.settings.setRetainOriginals(retain) }
    }

    fun setMoment(style: MomentStyle) {
        viewModelScope.launch { graph.settings.setMoment(style) }
    }

    /** Plays the currently chosen moment full screen. */
    fun requestPreview() {
        preview.value = state.value.settings.moment
    }

    fun dismissPreview() {
        preview.value = null
    }

    fun requestClearHistory() {
        confirmation.value = Confirmation.CLEAR_HISTORY
    }

    fun confirm() {
        val pending = confirmation.value
        confirmation.value = null
        when (pending) {
            Confirmation.RETENTION_OFF ->
                viewModelScope.launch {
                    graph.settings.setRetention(HistoryRetention.OFF)
                    graph.history.pruneExpired(Settings(retention = HistoryRetention.OFF))
                }
            Confirmation.CLEAR_HISTORY -> viewModelScope.launch { graph.history.clear() }
            null -> Unit
        }
    }

    fun dismiss() {
        confirmation.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun SettingsRoute(
    graph: AppGraph,
    appVersion: String,
    onBack: () -> Unit,
    onOpenUpstream: () -> Unit,
    onPinDemo: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel(graph, appVersion) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        callbacks =
            SettingsCallbacks(
                onRedirectMode = viewModel::setRedirectMode,
                onRetention = viewModel::requestRetention,
                onRetainOriginals = viewModel::setRetainOriginals,
                onClearHistory = viewModel::requestClearHistory,
                onConfirm = viewModel::confirm,
                onDismiss = viewModel::dismiss,
                onOpenUpstream = onOpenUpstream,
                onPinDemo = onPinDemo,
                onMoment = viewModel::setMoment,
                onPreview = viewModel::requestPreview,
                onDismissPreview = viewModel::dismissPreview,
            ),
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    callbacks: SettingsCallbacks,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionTitle(text = stringResource(R.string.settings_share_sheet))
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.settings_pin)) },
                supportingContent = { Text(text = stringResource(R.string.settings_pin_desc)) },
                modifier = Modifier.clickable(onClick = callbacks.onPinDemo),
            )
            SectionTitle(text = stringResource(R.string.settings_moment))
            for (style in MomentStyle.entries) {
                val (title, description) = momentLabels(style)
                RadioRow(
                    title = stringResource(title),
                    description = stringResource(description),
                    selected = state.settings.moment == style,
                    onSelect = { callbacks.onMoment(style) },
                )
            }
            TextButton(onClick = callbacks.onPreview, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(text = stringResource(R.string.moment_preview))
            }
            SectionTitle(text = stringResource(R.string.settings_history))
            RadioRow(
                title = stringResource(R.string.settings_retention_off),
                description = null,
                selected = state.settings.retention == HistoryRetention.OFF,
                onSelect = { callbacks.onRetention(HistoryRetention.OFF) },
            )
            RadioRow(
                title = stringResource(R.string.settings_retention_30),
                description = null,
                selected = state.settings.retention == HistoryRetention.DAYS_30,
                onSelect = { callbacks.onRetention(HistoryRetention.DAYS_30) },
            )
            RadioRow(
                title = stringResource(R.string.settings_retention_forever),
                description = null,
                selected = state.settings.retention == HistoryRetention.INDEFINITE,
                onSelect = { callbacks.onRetention(HistoryRetention.INDEFINITE) },
            )
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.settings_retain_originals)) },
                supportingContent = { Text(text = stringResource(R.string.settings_retain_originals_desc)) },
                trailingContent = {
                    Switch(checked = state.settings.retainOriginals, onCheckedChange = callbacks.onRetainOriginals)
                },
            )
            TextButton(onClick = callbacks.onClearHistory, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(text = stringResource(R.string.settings_clear_history))
            }
            SectionTitle(text = stringResource(R.string.settings_redirects))
            RadioRow(
                title = stringResource(R.string.settings_redirects_off),
                description = stringResource(R.string.settings_redirects_off_desc),
                selected = state.settings.redirectMode == ResolveMode.OFF,
                onSelect = { callbacks.onRedirectMode(ResolveMode.OFF) },
            )
            RadioRow(
                title = stringResource(R.string.settings_redirects_smart),
                description = stringResource(R.string.settings_redirects_smart_desc),
                selected = state.settings.redirectMode == ResolveMode.SMART,
                onSelect = { callbacks.onRedirectMode(ResolveMode.SMART) },
            )
            RadioRow(
                title = stringResource(R.string.settings_redirects_all),
                description = stringResource(R.string.settings_redirects_all_desc),
                selected = state.settings.redirectMode == ResolveMode.RESOLVE_ALL,
                onSelect = { callbacks.onRedirectMode(ResolveMode.RESOLVE_ALL) },
            )
            if (state.settings.redirectMode != ResolveMode.OFF) {
                Text(
                    text = stringResource(R.string.settings_redirects_disclosure),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SectionTitle(text = stringResource(R.string.settings_about))
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.settings_rules)) },
                supportingContent = { Text(text = state.rulesVersion) },
            )
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.settings_rules_upstream)) },
                modifier = Modifier.clickable(onClick = callbacks.onOpenUpstream),
            )
            ListItem(headlineContent = { Text(text = stringResource(R.string.settings_app_version, state.appVersion)) })
            Text(
                text = stringResource(R.string.settings_license),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    state.confirmation?.let { pending ->
        ConfirmationDialog(pending = pending, onConfirm = callbacks.onConfirm, onDismiss = callbacks.onDismiss)
    }
    state.preview?.let { style -> MomentPreviewDialog(style = style, onDismiss = callbacks.onDismissPreview) }
}

/** Title and one-line description resources for a [MomentStyle] radio row. */
private fun momentLabels(style: MomentStyle): Pair<Int, Int> =
    when (style) {
        MomentStyle.OFF -> R.string.moment_off to R.string.moment_off_desc
        MomentStyle.CUT -> R.string.moment_cut to R.string.moment_cut_desc
        MomentStyle.SHEEP -> R.string.moment_sheep to R.string.moment_sheep_desc
        MomentStyle.TYPEWRITER -> R.string.moment_typewriter to R.string.moment_typewriter_desc
    }

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun RadioRow(
    title: String,
    description: String?,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = description?.let { { Text(text = it) } },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    )
}

@Composable
private fun ConfirmationDialog(
    pending: Confirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, body) =
        when (pending) {
            Confirmation.CLEAR_HISTORY -> R.string.confirm_clear_title to R.string.confirm_clear_body
            Confirmation.RETENTION_OFF -> R.string.confirm_retention_off_title to R.string.confirm_retention_off_body
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(title)) },
        text = { Text(text = stringResource(body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(text = stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) } },
    )
}
