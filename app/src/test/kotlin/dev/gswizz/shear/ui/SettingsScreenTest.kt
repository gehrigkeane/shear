/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.gswizz.shear.core.net.ResolveMode
import dev.gswizz.shear.data.MomentStyle
import dev.gswizz.shear.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun noop() = SettingsCallbacks({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    private val disclosure =
        "Following a link online lets that link's owner see that you opened it. Shear never sends cookies, logins, or where you came from."

    @Test
    fun `the network disclosure is absent while resolution is off`() {
        compose.setContent {
            SettingsScreen(
                state = SettingsUiState(settings = Settings(redirectMode = ResolveMode.OFF)),
                callbacks = noop(),
                onBack = {},
            )
        }
        compose.onNodeWithText(disclosure).assertDoesNotExist()
    }

    @Test
    fun `the network disclosure appears when resolution is on`() {
        compose.setContent {
            SettingsScreen(
                state = SettingsUiState(settings = Settings(redirectMode = ResolveMode.SMART)),
                callbacks = noop(),
                onBack = {},
            )
        }
        // Follow redirects is the last section, below Robolectric's short viewport until scrolled.
        compose.onNodeWithText(disclosure).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `choosing a share moment calls back with the style`() {
        var chosen: MomentStyle? = null
        compose.setContent {
            SettingsScreen(state = SettingsUiState(), callbacks = noop().copy(onMoment = { chosen = it }), onBack = {})
        }
        compose.onNodeWithText("Confetti").performScrollTo().performClick()
        assertEquals(MomentStyle.CONFETTI, chosen)
    }

    @Test
    fun `the preview button asks for a preview`() {
        var previews = 0
        compose.setContent {
            SettingsScreen(state = SettingsUiState(), callbacks = noop().copy(onPreview = { previews++ }), onBack = {})
        }
        compose.onNodeWithText("Preview").performScrollTo().performClick()
        assertEquals(1, previews)
    }

    @Test
    fun `a pending preview plays the moment over a sample link`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SettingsScreen(state = SettingsUiState(preview = MomentStyle.CUT), callbacks = noop(), onBack = {})
        }
        compose.onNodeWithText("Sheared!").assertExists()
    }

    @Test
    fun `sections run pin me, animation, history, follow redirects, about`() {
        compose.setContent { SettingsScreen(state = SettingsUiState(), callbacks = noop(), onBack = {}) }
        // Nodes below Robolectric's viewport report empty bounds, so composition order stands in for position: the
        // column composes top to bottom and semantics ids are handed out in that order.
        val ids =
            listOf("Pin me", "Animation", "History", "Follow redirects", "About").map {
                compose.onNodeWithText(it).fetchSemanticsNode().id
            }
        assertEquals(ids.sorted(), ids)
        assertEquals(5, ids.distinct().size)
    }

    @Test
    fun `descriptions speak plainly`() {
        compose.setContent { SettingsScreen(state = SettingsUiState(), callbacks = noop(), onBack = {}) }
        compose.onNodeWithText("Watch the overgrown fluff get sheared!").assertExists()
        compose.onNodeWithText("Skip straight to sharing").assertExists()
        compose.onNodeWithText("Never go online").assertExists()
        compose.onNodeWithText("Keep forever").assertExists()
        compose.onNodeWithText("Opens a share sheet. Hold Shear, then tap Pin.").assertExists()
    }

    @Test
    fun `the share sheet row launches the pin demo`() {
        var demos = 0
        compose.setContent {
            SettingsScreen(state = SettingsUiState(), callbacks = noop().copy(onPinDemo = { demos++ }), onBack = {})
        }
        // The row sits below Robolectric's short viewport until scrolled into it.
        compose.onNodeWithText("Pin Shear for quick access").performScrollTo().performClick()
        assertEquals(1, demos)
    }

    @Test
    fun `confirmation dialogs render and route their buttons`() {
        var confirmed = 0
        compose.setContent {
            SettingsScreen(
                state = SettingsUiState(confirmation = Confirmation.CLEAR_HISTORY),
                callbacks = noop().copy(onConfirm = { confirmed++ }),
                onBack = {},
            )
        }
        compose.onNodeWithText("Clear history?").assertIsDisplayed()
        compose.onNodeWithText("Confirm").performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun `about shows the rules snapshot and the app version`() {
        compose.setContent {
            SettingsScreen(
                state =
                    SettingsUiState(rulesVersion = "c304773bcacaa22171b93f818321d89657d9f55e", appVersion = "0.1.0"),
                callbacks = noop(),
                onBack = {},
            )
        }
        // The about section sits below Robolectric's short viewport; it is composed, just scrolled away.
        compose.onNodeWithText("Shear 0.1.0").assertExists()
        compose.onNodeWithText("c304773bcacaa", substring = true).assertExists()
    }
}
