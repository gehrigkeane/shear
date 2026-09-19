/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharedScopesTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `without a shared transition in scope the modifier is left untouched`() {
        var result: Modifier? = null
        compose.setContent { result = Modifier.sharedElementIn(null, "summary/e1") }
        assertSame(Modifier, result)
    }
}
