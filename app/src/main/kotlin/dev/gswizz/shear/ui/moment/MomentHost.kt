/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.gswizz.shear.R
import dev.gswizz.shear.data.MomentStyle
import dev.gswizz.shear.ui.theme.Spacing

/**
 * The scrim and card every share moment plays inside; [style] picks the scene.
 *
 * Back finishes at once: the moment is a courtesy, never a gate. Nothing is written under the scene; the outcome is the
 * card's accessibility description, so a screen reader hears it while the scene stays purely decorative.
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
            val caption = caption(summary)
            Box(
                modifier = Modifier.padding(Spacing.l).semantics { contentDescription = caption },
                contentAlignment = Alignment.Center,
            ) {
                when (style) {
                    MomentStyle.CUT -> CutMoment(summary = summary, onFinished = onFinished)
                    MomentStyle.SHEEP -> SheepMoment(onFinished = onFinished)
                    MomentStyle.TYPEWRITER -> TypewriterMoment(onFinished = onFinished)
                    MomentStyle.OFF -> onFinished()
                }
            }
        }
    }
}

/** One word for what happened; the scene shows the how. */
@Composable
private fun caption(summary: MomentSummary): String =
    when {
        summary.urlCount == 0 -> stringResource(R.string.status_no_urls)
        !summary.changed -> stringResource(R.string.status_unchanged)
        else -> stringResource(R.string.moment_sheared)
    }

private const val SCRIM_ALPHA = 0.6f
