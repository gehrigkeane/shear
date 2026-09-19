/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.gswizz.shear.core.TextResult
import dev.gswizz.shear.core.engine.RemovedParameter
import dev.gswizz.shear.core.engine.RuleSource
import dev.gswizz.shear.core.engine.UrlCleanResult
import dev.gswizz.shear.data.MomentStyle

/** Plays [style] over a sample link, full screen, so a choice in Settings can be seen before a real share. */
@Composable
fun MomentPreviewDialog(style: MomentStyle, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        MomentHost(
            style = style,
            summary = SAMPLE,
            onFinished = onDismiss,
            modifier = modifier.clickable(onClick = onDismiss),
        )
    }
}

private const val SAMPLE_ORIGINAL = "https://shop.example/item/42?utm_source=news&fbclid=abc"
private const val SAMPLE_FINAL = "https://shop.example/item/42"

private val SAMPLE: MomentSummary =
    MomentSummary.of(
        TextResult(
            originalText = SAMPLE_ORIGINAL,
            outputText = SAMPLE_FINAL,
            urls =
                listOf(
                    UrlCleanResult(
                        originalUrl = SAMPLE_ORIGINAL,
                        finalUrl = SAMPLE_FINAL,
                        applications = emptyList(),
                        removedParameters =
                            listOf(
                                RemovedParameter("utm_source", "utm_source=news", RuleSource.QUERY_FILTER, 0),
                                RemovedParameter("fbclid", "fbclid=abc", RuleSource.QUERY_FILTER, 1),
                            ),
                        redirects = emptyList(),
                        failure = null,
                        rulesVersion = "sample",
                    )
                ),
        )
    )
