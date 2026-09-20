/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.gswizz.shear.data.ShareStatus
import java.time.Instant
import org.junit.Assert.assertEquals
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
            summary = "dest.example",
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
        // The header mirrors the history row: hosts as the title, time and destination beneath.
        compose.onNodeWithText("dest.example").assertIsDisplayed()
        compose.onNode(hasText(" · Messages", substring = true)).assertIsDisplayed()
        // The badge and the box below it both say Sheared; only the box is tappable.
        compose.onNode(hasText("Sheared") and hasNoClickAction()).assertIsDisplayed()
        // Robolectric's default viewport is short; the trace card is composed but scrolled out of view.
        compose.onNodeWithText("clean-urls rule #44").assertExists()
        compose.onNodeWithText("utm_source=x").assertExists()
    }

    @Test
    fun `an unchanged share shows its text once, under Original`() {
        val same = event.copy(cleanedText = event.originalText!!, status = ShareStatus.UNCHANGED)
        compose.setContent { EventDetailScreen(state = EventDetailUiState.Loaded(same), onBack = {}) }
        // Unmerged, so the card that merges its text into one tappable node does not count twice.
        compose.onAllNodesWithText("see https://go.example/r?u=x", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("Original").assertIsDisplayed()
        compose.onNodeWithText("Sheared").assertDoesNotExist()
    }

    @Test
    fun `an unchanged share whose original was not kept still shows what went out`() {
        val same = event.copy(originalText = null, cleanedText = event.originalText!!, status = ShareStatus.UNCHANGED)
        compose.setContent { EventDetailScreen(state = EventDetailUiState.Loaded(same), onBack = {}) }
        compose.onNodeWithText("see https://go.example/r?u=x").assertIsDisplayed()
    }

    @Test
    fun `a redacted original shows the hash`() {
        compose.setContent {
            EventDetailScreen(state = EventDetailUiState.Loaded(event.copy(originalText = null)), onBack = {})
        }
        compose.onNodeWithText("Original not retained (hash abc)").assertIsDisplayed()
    }

    @Test
    fun `tapping Sheared copies the sheared text`() {
        compose.setContent { EventDetailScreen(state = EventDetailUiState.Loaded(event), onBack = {}) }
        compose.onNodeWithText("see https://dest.example/a?id=1").performClick()
        assertEquals("see https://dest.example/a?id=1", clipboardText())
    }

    @Test
    fun `tapping Original copies the original text`() {
        compose.setContent { EventDetailScreen(state = EventDetailUiState.Loaded(event), onBack = {}) }
        compose.onNodeWithText("see https://go.example/r?u=x").performClick()
        assertEquals("see https://go.example/r?u=x", clipboardText())
    }

    @Test
    fun `a redacted original offers nothing to copy`() {
        compose.setContent {
            EventDetailScreen(state = EventDetailUiState.Loaded(event.copy(originalText = null)), onBack = {})
        }
        compose.onNodeWithText("Original not retained (hash abc)").assertHasNoClickAction()
    }

    private fun clipboardText(): String? {
        compose.waitForIdle()
        val manager =
            ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
        return manager.primaryClip?.getItemAt(0)?.text?.toString()
    }

    @Test
    fun `a missing event says so`() {
        compose.setContent { EventDetailScreen(state = EventDetailUiState.Missing, onBack = {}) }
        compose.onNodeWithText("This share is no longer in history").assertIsDisplayed()
    }
}
