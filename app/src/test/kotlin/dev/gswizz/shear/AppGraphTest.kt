/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.gswizz.shear.data.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppGraphTest {
    @Test
    fun `the manifest application builds a graph with defaults and a healthy engine`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        assertTrue("manifest must name ShearApplication", app is ShearApplication)
        val graph = (app as ShearApplication).graph
        assertEquals(Settings(), graph.settings.settings.first())
        assertTrue(graph.history.observeEvents().first().isEmpty())
        val engine = graph.engine()
        assertTrue(engine.rulesHealthy)
        assertEquals(40, engine.rulesVersion.length)
        assertEquals(
            "see https://a.example/x?id=1",
            engine.clean("see https://a.example/x?utm_source=t&id=1").outputText,
        )
    }
}
