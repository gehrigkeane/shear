/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.engine

import dev.gswizz.shear.core.psl.PublicSuffixList
import dev.gswizz.shear.core.rules.BraveRules
import dev.gswizz.shear.core.rules.DebounceAction
import dev.gswizz.shear.core.rules.DebounceRule
import dev.gswizz.shear.core.url.PercentCodec
import dev.gswizz.shear.core.url.UrlParts
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/** Every debounce rule in the vendored snapshot unwraps a synthesized wrapper to a cross-site destination. */
class DebounceGoldenTest {
    private val rules = BraveRules.load()
    private val psl = PublicSuffixList.load()
    private val dest = "https://dest.example/landing?x=1&y=2"

    /** Regex rules need a hand-authored path per regex; an unknown regex fails loudly so dataset refreshes surface. */
    private val regexPaths =
        mapOf(
            "^/c/s/(.*)$" to ("/c/s/dest.example/landing" to "https://dest.example/landing"),
            "^/CL0/([^/]+)/.*$" to ("/CL0/https%3A%2F%2Fdest.example%2Flanding/1/x" to "https://dest.example/landing"),
            "^/[^/]+/(.*)$" to ("/abc/https://dest.example/landing" to "https://dest.example/landing"),
            "^/([^/]+)/s(/.*)$" to ("/dest.example/s/landing" to "https://dest.example/landing"),
            "^/[23]t?sm?/([^/]+)/.*$" to ("/2s/dest.example/x" to "https://dest.example"),
            "^/[23]m?/([^/]+)/.*$" to ("/2/dest.example/x" to "http://dest.example"),
            "^/L0/([^/]+)/.*$" to ("/L0/https%3A%2F%2Fdest.example%2Flanding/1/2" to "https://dest.example/landing"),
            "^/amp/s/(.*)$" to ("/amp/s/dest.example/landing" to "https://dest.example/landing"),
            "^/navigation-tracking/regex-multiple-capture-groups/(.*)/test/(.*);.html$" to
                ("/navigation-tracking/regex-multiple-capture-groups/dest.example%2F/test/landing;.html" to
                    "https://dest.example/landing"),
            "^/navigation-tracking/(.*);.html$" to
                ("/navigation-tracking/dest.example%2Flanding;.html" to "https://dest.example/landing"),
            "^/([^/]+)$" to ("/abc123" to "https://www.youtube.com/watch?v=abc123"),
        )

    /**
     * Include patterns whose path can never carry a query; an upstream fix will fail this list and prompt an update.
     */
    private val knownDead = setOf("*://www.tkqlhce.com/click-", "*://t.lever-analytics.com/email-link?")

    private fun debouncer(deAmp: Boolean = true) = Debouncer(rules.debounce, psl, deAmpEnabled = deAmp)

    private fun apply(url: String, sink: TraceSink = TraceSink("g"), deAmp: Boolean = true): String =
        debouncer(deAmp).apply(UrlParts.parse(url) ?: fail("golden url must parse: $url"), sink).toUrlString()

    private fun wrapperFor(rule: DebounceRule, pattern: String): Pair<String, String>? {
        val base = GoldenSupport.base(pattern) ?: return null
        return when (rule.action) {
            DebounceAction.REDIRECT,
            DebounceAction.BASE64_REDIRECT -> {
                val value = if (rule.prependScheme != null) dest.removePrefix("https://") else dest
                val encoded =
                    if (rule.action == DebounceAction.BASE64_REDIRECT)
                        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
                    else PercentCodec.formEncode(value)
                val expected =
                    if (rule.prependScheme != null) "${rule.prependScheme}://" + dest.removePrefix("https://") else dest
                base.withQuery("${rule.param}=$encoded") to expected
            }
            DebounceAction.REGEX_PATH,
            DebounceAction.REGEX_PATH_TEMPLATE -> {
                val (path, expected) =
                    regexPaths[rule.param] ?: fail("no golden path for regex ${rule.param}; extend regexPaths")
                base.withPath(path) to expected
            }
        }
    }

    @TestFactory
    fun `every debounce rule unwraps its wrapper`(): List<DynamicTest> {
        val tests = mutableListOf<DynamicTest>()
        val dead = mutableSetOf<String>()
        for (rule in rules.debounce) {
            for ((pi, include) in rule.patterns.include.withIndex()) {
                val (url, expected) =
                    wrapperFor(rule, include.raw)
                        ?: run {
                            dead += include.raw
                            null
                        }
                        ?: continue
                assertNotNull(rule.patterns.matches(UrlParts.parse(url)!!), "golden must match its own pattern: $url")
                tests +=
                    DynamicTest.dynamicTest(
                        "debounce[${rule.index}] include[$pi] ${rule.action.wireName}: ${url.take(70)}"
                    ) {
                        val sink = TraceSink("g")
                        assertEquals(expected, apply(url, sink))
                        assertEquals(rule.index, sink.applications.single().ruleIndex)
                        assertEquals(RuleSource.DEBOUNCE, sink.applications.single().source)
                        assertEquals(RedirectKind.OFFLINE, sink.redirects.single().kind)
                    }
                if (rule.requiresDeAmp) {
                    tests +=
                        DynamicTest.dynamicTest("debounce[${rule.index}] include[$pi] stays put when de-amp is off") {
                            assertEquals(url, apply(url, deAmp = false))
                        }
                }
                if (rule.action == DebounceAction.REDIRECT && rule.prependScheme == null) {
                    val host = GoldenSupport.base(include.raw)!!.host
                    val sameSite = "https://elsewhere.${psl.registrableDomain(host) ?: host}/landing"
                    val wrapped =
                        GoldenSupport.base(include.raw)!!.withQuery(
                            "${rule.param}=${PercentCodec.formEncode(sameSite)}"
                        )
                    tests +=
                        DynamicTest.dynamicTest(
                            "debounce[${rule.index}] include[$pi] refuses a same-site destination"
                        ) {
                            assertEquals(wrapped, apply(wrapped))
                        }
                }
            }
        }
        assertEquals(knownDead, dead, "include patterns that cannot carry a query")
        return tests
    }

    @Test
    fun `the snapshot exercises every action`() {
        assertEquals(DebounceAction.entries.toSet(), rules.debounce.map { it.action }.toSet())
    }
}
