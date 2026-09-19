/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.gswizz.shear.R
import dev.gswizz.shear.data.MomentStyle
import dev.gswizz.shear.ui.theme.Spacing

/**
 * The scrim, card, and caption every share moment plays inside; [style] picks the scene.
 *
 * Back finishes at once: the moment is a courtesy, never a gate. The caption states the outcome in words so the scene
 * can stay purely decorative for a screen reader.
 */
@Composable
fun MomentHost(style: MomentStyle, summary: MomentSummary, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onFinished)
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(Spacing.l),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier.padding(Spacing.l),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                when (style) {
                    MomentStyle.CONFETTI -> ConfettiMoment(summary = summary, onFinished = onFinished)
                    // Typewriter arrives in its own change; until then it plays The Cut.
                    MomentStyle.CUT,
                    MomentStyle.TYPEWRITER -> CutMoment(summary = summary, onFinished = onFinished)
                    MomentStyle.OFF -> onFinished()
                }
                Text(
                    text = caption(summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun caption(summary: MomentSummary): String =
    when {
        summary.urlCount == 0 -> stringResource(R.string.status_no_urls)
        !summary.changed -> stringResource(R.string.status_unchanged)
        summary.removedNames.isNotEmpty() -> {
            val shown = summary.removedNames.take(MAX_NAMES)
            val more = summary.removedNames.size - shown.size
            val names = if (more > 0) shown.joinToString(", ") + " +$more" else shown.joinToString(", ")
            stringResource(R.string.moment_removed, names)
        }
        else -> pluralStringResource(R.plurals.moment_cleaned, summary.urlCount, summary.urlCount)
    }

private const val SCRIM_ALPHA = 0.6f
private const val MAX_NAMES = 3
