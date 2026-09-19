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
import dev.gswizz.shear.core.net.ResolveMode
import dev.gswizz.shear.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun noop() = SettingsCallbacks({}, {}, {}, {}, {}, {}, {})

    private val disclosure =
        "Resolving redirects contacts the redirect service, which learns that you visited the link. Shear sends no cookies, credentials, or referrer."

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
        compose.onNodeWithText(disclosure).assertIsDisplayed()
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
