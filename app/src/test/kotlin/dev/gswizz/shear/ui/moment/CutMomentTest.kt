/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import dev.gswizz.shear.core.TextResult
import dev.gswizz.shear.core.engine.RemovedParameter
import dev.gswizz.shear.core.engine.RuleSource
import dev.gswizz.shear.core.engine.UrlCleanResult
import dev.gswizz.shear.ui.theme.ShearTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CutMomentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a long URL wraps onto several lines instead of running off the edge`() {
        val original = "https://shop.example/department/category/item/42?utm_source=newsletter&utm_campaign=spring&id=7"
        val final = "https://shop.example/department/category/item/42?id=7"
        val url =
            UrlCleanResult(
                originalUrl = original,
                finalUrl = final,
                applications = emptyList(),
                removedParameters =
                    listOf(
                        RemovedParameter("utm_source", "utm_source=newsletter", RuleSource.QUERY_FILTER, 0),
                        RemovedParameter("utm_campaign", "utm_campaign=spring", RuleSource.QUERY_FILTER, 1),
                    ),
                redirects = emptyList(),
                failure = null,
                rulesVersion = "test",
            )
        val summary = MomentSummary.of(TextResult(original, final, listOf(url)))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ShearTheme { Box(modifier = Modifier.width(200.dp)) { CutMoment(summary = summary, onFinished = {}) } }
        }
        val height = compose.onRoot().fetchSemanticsNode().size.height
        // Roughly 20 monospace characters fit in 200dp at body size, so ~95 characters need five lines or so; anything
        // taller than two lines of 20dp proves the wrap.
        val density = compose.density.density
        assertTrue("height $height px", height > 2 * 20 * density)
    }
}
