/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.engine

import dev.gswizz.shear.core.psl.PublicSuffixList
import dev.gswizz.shear.core.rules.BraveRules
import dev.gswizz.shear.core.rules.DebounceRuleDto
import dev.gswizz.shear.core.url.PercentCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UrlCleanerTest {
    private val real = UrlCleaner(BraveRules.load(), PublicSuffixList.load())

    @Test
    fun `unwraps nested redirectors and strips trackers from the destination in one call`() {
        val dest = "https://dest.example/p?utm_source=news&keep=1"
        val awin = "https://www.awin1.com/cread.php?awinmid=1&ued=${PercentCodec.formEncode(dest)}"
        val skim = "https://go.skimresources.com/?id=1&url=${PercentCodec.formEncode(awin)}"
        val result = real.clean(skim)
        assertEquals("https://dest.example/p?keep=1", result.finalUrl)
        assertEquals(skim, result.originalUrl)
        assertTrue(result.changed)
        assertNull(result.failure)
        assertEquals(listOf(RedirectKind.OFFLINE, RedirectKind.OFFLINE), result.redirects.map { it.kind })
        assertEquals(listOf(skim, awin), result.redirects.map { it.fromUrl })
        assertEquals(listOf(awin, dest), result.redirects.map { it.toUrl })
        assertEquals(listOf(0, 1, 1), result.applications.map { it.iteration })
        assertEquals(
            listOf(RuleSource.DEBOUNCE, RuleSource.DEBOUNCE, RuleSource.CLEAN_URLS),
            result.applications.map { it.source },
        )
        assertEquals(listOf("utm_source"), result.removedParameters.map { it.name })
        assertEquals(real.rules.version, result.rulesVersion)
    }

    @Test
    fun `a clean url is a fixed point with an empty trace`() {
        val url = "https://dest.example/p?keep=1#frag"
        val result = real.clean(url)
        assertEquals(url, result.finalUrl)
        assertTrue(result.applications.isEmpty() && result.redirects.isEmpty() && result.removedParameters.isEmpty())
        assertNull(result.failure)
    }

    @Test
    fun `an unparsable input is returned untouched with an invalid-url failure`() {
        val result = real.clean("not a url at all")
        assertEquals("not a url at all", result.finalUrl)
        assertEquals(FailureKind.INVALID_URL, result.failure?.kind)
        assertTrue(result.applications.isEmpty())
    }

    @Test
    fun `a wrapper chain deeper than the iteration limit stops with the depth failure and the last url reached`() {
        val psl = PublicSuffixList.parse("// ===BEGIN ICANN DOMAINS===\nexample\n// ===END ICANN DOMAINS===")
        val rules =
            BraveRules.compile(
                version = "t",
                cleanUrls = emptyList(),
                queryFilter = emptyList(),
                conditionalTrackers = emptyMap(),
                debounce =
                    listOf(
                        DebounceRuleDto(include = listOf("*://a.example/*"), action = "redirect", param = "u"),
                        DebounceRuleDto(include = listOf("*://b.example/*"), action = "redirect", param = "u"),
                    ),
            )
        val cleaner = UrlCleaner(rules, psl, CleanOptions(maxOfflineIterations = 5))
        var url = "https://z.example/end"
        val layers = mutableListOf(url)
        repeat(7) { i ->
            url = "https://${if (i % 2 == 0) "a" else "b"}.example/?u=${PercentCodec.formEncode(url)}"
            layers += url
        }
        val result = cleaner.clean(url)
        assertEquals(FailureKind.OFFLINE_DEPTH_EXCEEDED, result.failure?.kind)
        assertEquals(layers[layers.size - 1 - 5], result.finalUrl)
        assertEquals(5, result.redirects.size)
        val shallow = cleaner.clean(layers[3])
        assertEquals("https://z.example/end", shallow.finalUrl)
        assertNull(shallow.failure)
    }
}
