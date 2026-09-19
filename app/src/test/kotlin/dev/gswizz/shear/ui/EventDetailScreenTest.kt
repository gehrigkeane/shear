/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.gswizz.shear.data.ShareStatus
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EventDetailScreenTest {
    @get:Rule val compose = createComposeRule()

    private val event =
        EventDetail(
            id = "e1",
            timestamp = Instant.parse("2026-09-19T10:15:30Z"),
            originalText = "see https://go.example/r?u=x",
            originalHash = "abc",
            cleanedText = "see https://dest.example/a?id=1",
            status = ShareStatus.CLEANED,
            rulesVersion = "c304773bcacaa22171b93f818321d89657d9f55e",
            destination = DestinationUi("Messages", "com.messages/.Share", null),
            traces =
                listOf(
                    TraceUi(
                        originalUrl = "https://go.example/r?u=x",
                        finalUrl = "https://dest.example/a?id=1",
                        rules = listOf("debounce rule #3 (redirect)", "clean-urls rule #44"),
                        removedParams = listOf("utm_source=x"),
                        redirectSteps =
                            listOf("offline: https://go.example/r?u=x → https://dest.example/a?utm_source=x&id=1"),
                        failure = null,
                    )
                ),
        )

    @Test
    fun `renders the texts destination and trace`() {
        compose.setContent { EventDetailScreen(state = EventDetailUiState.Loaded(event), onBack = {}) }
        compose.onNodeWithText("see https://go.example/r?u=x").assertIsDisplayed()
        compose.onNodeWithText("see https://dest.example/a?id=1").assertIsDisplayed()
        compose.onNodeWithText("Sent to Messages").assertIsDisplayed()
        // Robolectric's default viewport is short; the trace card is composed but scrolled out of view.
        compose.onNodeWithText("clean-urls rule #44").assertExists()
        compose.onNodeWithText("utm_source=x").assertExists()
    }

    @Test
    fun `a redacted original shows the hash`() {
        compose.setContent {
            EventDetailScreen(state = EventDetailUiState.Loaded(event.copy(originalText = null)), onBack = {})
        }
        compose.onNodeWithText("Original not retained (hash abc)").assertIsDisplayed()
    }

    @Test
    fun `a missing event says so`() {
        compose.setContent { EventDetailScreen(state = EventDetailUiState.Missing, onBack = {}) }
        compose.onNodeWithText("This share is no longer in history").assertIsDisplayed()
    }
}
