/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Proves the Robolectric harness resolves the pinned SDK and the application package. */
@RunWith(RobolectricTestRunner::class)
class SmokeTest {
    @Test
    fun applicationContextIsShearOnApi37() {
        assertEquals("dev.gswizz.shear", ApplicationProvider.getApplicationContext<Context>().packageName)
        assertEquals(37, Build.VERSION.SDK_INT)
    }
}
