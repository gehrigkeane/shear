/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.engine

import dev.gswizz.shear.core.psl.PublicSuffixList
import dev.gswizz.shear.core.rules.BraveRules
import dev.gswizz.shear.core.url.PercentCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** `clean(clean(x)) == clean(x)` over every dataset-derived golden, through the whole offline pipeline. */
class IdempotenceTest {
    private val rules = BraveRules.load()
    private val cleaner = UrlCleaner(rules, PublicSuffixList.load())

    private fun inputs(): List<Pair<String, String>> {
        val strip =
            (rules.cleanUrls + rules.queryFilter).flatMap { rule ->
                rule.patterns.include
                    .mapNotNull { GoldenSupport.synthesize(it.raw, rule.params.toList()) }
                    .map { it.url to it.expected }
            }
        val dest = "https://dest.example/landing?x=1&y=2"
        val wrappers =
            rules.debounce
                .filter { it.action.wireName == "redirect" && it.prependScheme == null }
                .mapNotNull { rule ->
                    GoldenSupport.base(rule.patterns.include.first().raw)
                        ?.withQuery("${rule.param}=${PercentCodec.formEncode(dest)}")
                        ?.let { it to dest }
                }
        return strip + wrappers
    }

    @TestFactory
    fun `cleaning is idempotent and reaches the expected fixed point`(): List<DynamicTest> {
        val cases = inputs()
        assertTrue(cases.size > 100, "expected a large golden corpus, got ${cases.size}")
        return cases.map { (url, expected) ->
            DynamicTest.dynamicTest(url.take(80)) {
                val first = cleaner.clean(url)
                assertEquals(expected, first.finalUrl, "first pass")
                val second = cleaner.clean(first.finalUrl)
                assertEquals(first.finalUrl, second.finalUrl, "second pass")
                assertTrue(second.applications.isEmpty(), "second pass must apply nothing: ${second.applications}")
                assertTrue(second.redirects.isEmpty())
            }
        }
    }
}
