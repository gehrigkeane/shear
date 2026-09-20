/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import dev.gswizz.shear.data.HistoryFixtures
import dev.gswizz.shear.data.Settings
import dev.gswizz.shear.data.ShareStatus
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `a history row opens its details and back returns`(): Unit = runBlocking {
        val installed = TestGraph.install(FakeEngine.noUrls())
        installed.history
            .record("e1", HistoryFixtures.ORIGINAL, HistoryFixtures.textResult, ShareStatus.CLEANED, Settings())
            .join()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithContentDescription("Shear").assertExists()
            compose.waitUntil(5_000) { compose.onAllNodes(hasText("dest.example")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("dest.example").performClick()
            compose.onNodeWithText(HistoryFixtures.CLEANED).assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithContentDescription("Shear").assertExists()
        }
    }
}
