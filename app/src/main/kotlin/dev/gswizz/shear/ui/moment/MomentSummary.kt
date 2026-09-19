/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import dev.gswizz.shear.core.TextResult

/** What a share moment has to say about the share: how many links, whether anything changed, and what fell. */
data class MomentSummary(
    val urlCount: Int,
    val changed: Boolean,
    /** Distinct names of removed parameters across every link, in order of appearance. */
    val removedNames: List<String>,
    val firstUrl: String?,
    val cut: CutPlan?,
) {
    companion object {
        fun of(result: TextResult): MomentSummary =
            MomentSummary(
                urlCount = result.urls.size,
                changed = result.changed,
                removedNames = result.urls.flatMap { url -> url.removedParameters.map { it.name } }.distinct(),
                firstUrl = result.urls.firstOrNull()?.originalUrl,
                cut = CutPlan.of(result),
            )
    }
}
