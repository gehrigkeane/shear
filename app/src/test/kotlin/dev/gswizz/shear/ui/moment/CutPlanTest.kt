/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import dev.gswizz.shear.core.TextResult
import dev.gswizz.shear.core.engine.RemovedParameter
import dev.gswizz.shear.core.engine.RuleSource
import dev.gswizz.shear.core.engine.UrlCleanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CutPlanTest {
    private fun result(original: String, final: String, vararg removed: String) =
        UrlCleanResult(
            originalUrl = original,
            finalUrl = final,
            applications = emptyList(),
            removedParameters = removed.map { RemovedParameter(it.substringBefore('='), it, RuleSource.CLEAN_URLS, 1) },
            redirects = emptyList(),
            failure = null,
            rulesVersion = "test",
        )

    private fun CutPlan.kept() = segments.filter { !it.removed }.joinToString("") { it.text }

    @Test
    fun `a leading parameter takes the separator after it`() {
        val plan =
            CutPlan.of(result("https://a.example/p?utm_source=x&id=1", "https://a.example/p?id=1", "utm_source=x"))
        assertEquals(
            listOf(
                Segment("https://a.example/p?", removed = false),
                Segment("utm_source=x&", removed = true),
                Segment("id=1", removed = false),
            ),
            plan.segments,
        )
        assertFalse(plan.wholesale)
        assertEquals(plan.final, plan.kept())
    }

    @Test
    fun `a trailing parameter takes the separator before it`() {
        val plan = CutPlan.of(result("https://a.example/p?id=1&fbclid=z", "https://a.example/p?id=1", "fbclid=z"))
        assertEquals(
            listOf(Segment("https://a.example/p?id=1", removed = false), Segment("&fbclid=z", removed = true)),
            plan.segments,
        )
        assertEquals(plan.final, plan.kept())
    }

    @Test
    fun `removing every parameter also removes the question mark`() {
        val plan = CutPlan.of(result("https://a.example/p?utm=1", "https://a.example/p", "utm=1"))
        assertEquals(
            listOf(Segment("https://a.example/p", removed = false), Segment("?utm=1", removed = true)),
            plan.segments,
        )
    }

    @Test
    fun `two removals around a kept parameter`() {
        val plan =
            CutPlan.of(
                result(
                    "https://a.example/p?utm=1&id=7&fbclid=a#top",
                    "https://a.example/p?id=7#top",
                    "utm=1",
                    "fbclid=a",
                )
            )
        assertEquals(
            listOf(
                Segment("https://a.example/p?", removed = false),
                Segment("utm=1&", removed = true),
                Segment("id=7", removed = false),
                Segment("&fbclid=a", removed = true),
                Segment("#top", removed = false),
            ),
            plan.segments,
        )
        assertEquals(plan.final, plan.kept())
    }

    @Test
    fun `a URL that changed beyond its parameters is cut wholesale`() {
        val plan = CutPlan.of(result("https://go.example/r?u=dest", "https://dest.example/a", "u=dest"))
        assertTrue(plan.wholesale)
        assertEquals(listOf(Segment("https://go.example/r?u=dest", removed = true)), plan.segments)
        assertEquals("https://dest.example/a", plan.final)
    }

    @Test
    fun `a text result yields the first changed URL or nothing`() {
        val unchanged = result("https://a.example/", "https://a.example/")
        val changed = result("https://b.example/?utm=1", "https://b.example/", "utm=1")
        assertNull(CutPlan.of(TextResult("x", "x", listOf(unchanged))))
        assertEquals("https://b.example/", CutPlan.of(TextResult("x", "y", listOf(unchanged, changed)))?.final)
    }
}
