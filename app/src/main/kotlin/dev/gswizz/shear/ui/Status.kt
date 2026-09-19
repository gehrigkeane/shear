/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gswizz.shear.R
import dev.gswizz.shear.data.ShareStatus

/** The user-facing name of a [ShareStatus]. */
@Composable
@ReadOnlyComposable
fun statusLabel(status: ShareStatus): String =
    stringResource(
        when (status) {
            ShareStatus.CLEANED -> R.string.status_cleaned
            ShareStatus.UNCHANGED -> R.string.status_unchanged
            ShareStatus.NO_URLS -> R.string.status_no_urls
            ShareStatus.RULES_UNAVAILABLE -> R.string.status_rules_unavailable
            ShareStatus.RESOLUTION_INCOMPLETE -> R.string.status_resolution_incomplete
            ShareStatus.CANCELLED -> R.string.status_cancelled
        }
    )

/**
 * A read-only pill naming a share's [ShareStatus], tinted by how well the share went.
 *
 * Success sits on the primary container, a partial result on the tertiary one, and a failure on the error container;
 * the neutral outcomes share a plain surface. It is a label, not a control, so it exposes no click action.
 */
@Composable
fun StatusBadge(status: ShareStatus, modifier: Modifier = Modifier) {
    val (container, content) = statusColors(status)
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = container, contentColor = content) {
        Text(
            text = statusLabel(status),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
@ReadOnlyComposable
private fun statusColors(status: ShareStatus): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        ShareStatus.CLEANED -> scheme.primaryContainer to scheme.onPrimaryContainer
        ShareStatus.RESOLUTION_INCOMPLETE -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        ShareStatus.RULES_UNAVAILABLE -> scheme.errorContainer to scheme.onErrorContainer
        ShareStatus.UNCHANGED,
        ShareStatus.NO_URLS,
        ShareStatus.CANCELLED -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
}
