/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.gswizz.shear.data.ShareStatus
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryScreenTest {
    @get:Rule val compose = createComposeRule()

    private val row =
        HistoryRow(
            id = "e1",
            time = Instant.parse("2026-09-19T10:15:30Z"),
            summary = "dest.example",
            urlCount = 1,
            destination = DestinationUi(label = "Messages", component = "com.messages/.Share", icon = null),
            status = ShareStatus.CLEANED,
        )

    @Test
    fun `renders rows with destination and status and opens on click`() {
        var opened: String? = null
        compose.setContent {
            HistoryScreen(
                state =
                    HistoryUiState(
                        sections = listOf(DaySection(LocalDate.of(2026, 9, 19), listOf(row))),
                        loading = false,
                    ),
                onOpen = { opened = it },
                onSettings = {},
            )
        }
        compose.onNodeWithContentDescription("Shear").assertExists()
        compose.onNodeWithText("dest.example").assertIsDisplayed()
        compose.onNodeWithText("Messages").assertIsDisplayed()
        compose.onNodeWithText("Sheared").assertIsDisplayed()
        // Only the settings action and the row itself are tappable; the status is a label, not a button.
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
        compose.onNodeWithText("dest.example").performClick()
        assertEquals("e1", opened)
    }

    @Test
    fun `shows the rules banner and the retention hint`() {
        compose.setContent {
            HistoryScreen(
                state = HistoryUiState(loading = false, rulesUnavailable = true, retentionOff = true),
                onOpen = {},
                onSettings = {},
            )
        }
        compose.onNodeWithText("Shear's rules failed to load. Text is being shared unchanged.").assertIsDisplayed()
        compose.onNodeWithText("History is off. Links are sheared but not recorded.").assertIsDisplayed()
    }

    @Test
    fun `the pin card offers a demo and can be dismissed`() {
        var demos = 0
        var dismissed = 0
        compose.setContent {
            HistoryScreen(
                state = HistoryUiState(loading = false, showPinPrompt = true),
                onOpen = {},
                onSettings = {},
                onPinDemo = { demos++ },
                onDismissPin = { dismissed++ },
            )
        }
        compose.onNodeWithText("Pin Shear for quick access").assertIsDisplayed()
        compose.onNodeWithText("Show me").performClick()
        compose.onNodeWithText("Not now").performClick()
        assertEquals(1, demos)
        assertEquals(1, dismissed)
    }

    @Test
    fun `the pin card stays hidden once seen`() {
        compose.setContent { HistoryScreen(state = HistoryUiState(loading = false), onOpen = {}, onSettings = {}) }
        compose.onNodeWithText("Pin Shear for quick access").assertDoesNotExist()
    }

    @Test
    fun `an empty history says so`() {
        compose.setContent { HistoryScreen(state = HistoryUiState(loading = false), onOpen = {}, onSettings = {}) }
        compose.onNodeWithText("Nothing sheared yet").assertIsDisplayed()
    }
}
