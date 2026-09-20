/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import dev.gswizz.shear.core.TextResult

/** What a share moment has to say about the share: how many links, whether anything changed, and what to cut. */
data class MomentSummary(val urlCount: Int, val changed: Boolean, val firstUrl: String?, val cut: CutPlan?) {
    companion object {
        fun of(result: TextResult): MomentSummary =
            MomentSummary(
                urlCount = result.urls.size,
                changed = result.changed,
                firstUrl = result.urls.firstOrNull()?.originalUrl,
                cut = CutPlan.of(result),
            )
    }
}
