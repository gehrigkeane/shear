/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.engine

import dev.gswizz.shear.core.rules.BraveRules
import dev.gswizz.shear.core.rules.ParamRuleDto
import dev.gswizz.shear.core.url.UrlParts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QuerySanitizerTest {
    private val rules =
        BraveRules.compile(
            version = "t",
            cleanUrls =
                listOf(
                    ParamRuleDto(
                        include = listOf("*://*.bird.example/*"),
                        exclude = listOf("*://*.bird.example/i/redirect?*"),
                        params = listOf("ref_src", "s"),
                    ),
                    ParamRuleDto(
                        include = listOf("*://*/*"),
                        params = listOf("utm_source", "utm_medium", "__cft__[0]"),
                    ),
                ),
            queryFilter = emptyList(),
            conditionalTrackers = emptyMap(),
            debounce = emptyList(),
        )
    private val sanitizer = QuerySanitizer(rules.cleanUrls)

    private fun clean(url: String, sink: TraceSink = TraceSink("t")): String =
        sanitizer.apply(UrlParts.parse(url)!!, sink).toUrlString()

    @Test
    fun `removes listed parameters and preserves order duplicates and encoding of the rest`() {
        assertEquals(
            "https://a.example/p?a=1&a=2&b=%2F%5B#f",
            clean("https://a.example/p?a=1&utm_source=x&a=2&utm_medium=y&b=%2F%5B#f"),
        )
    }

    @Test
    fun `drops the question mark only when the whole query was tracking`() {
        assertEquals("https://a.example/p", clean("https://a.example/p?utm_source=x"))
        assertEquals("https://a.example/p#f", clean("https://a.example/p?utm_source=x&utm_medium=y#f"))
        assertEquals("https://a.example/p?", clean("https://a.example/p?"))
        assertEquals("https://a.example/p?&", clean("https://a.example/p?&"))
    }

    @Test
    fun `tokens without a value are never removed`() {
        assertEquals("https://a.example/p?utm_source=&x=1", clean("https://a.example/p?utm_source=&x=1"))
        assertEquals("https://a.example/p?utm_source&x=1", clean("https://a.example/p?utm_source&x=1"))
        assertEquals("https://a.example/p?=utm_source&x=1", clean("https://a.example/p?=utm_source&x=1"))
        assertEquals("https://a.example/p?x=1", clean("https://a.example/p?utm_source==x&x=1"))
    }

    @Test
    fun `keys are exact and case sensitive and brackets are literal`() {
        assertEquals(
            "https://a.example/p?UTM_SOURCE=x&utm_sourcex=y",
            clean("https://a.example/p?UTM_SOURCE=x&utm_sourcex=y"),
        )
        assertEquals("https://a.example/p?__cft__%5B0%5D=x", clean("https://a.example/p?__cft__[0]=a&__cft__%5B0%5D=x"))
    }

    @Test
    fun `later matchers see the output of earlier ones and excludes protect a rule`() {
        assertEquals(
            "https://x.bird.example/status?lang=en",
            clean("https://x.bird.example/status?ref_src=twsrc&lang=en&utm_source=x&s=20"),
        )
        assertEquals(
            "https://x.bird.example/i/redirect?ref_src=a&s=1",
            clean("https://x.bird.example/i/redirect?ref_src=a&utm_source=x&s=1"),
        )
    }

    @Test
    fun `records one application per rule with the removed tokens`() {
        val sink = TraceSink("t")
        clean("https://x.bird.example/s?s=1&utm_source=x&k=1", sink)
        assertEquals(listOf(0, 1), sink.applications.map { it.ruleIndex })
        assertEquals(listOf(RuleSource.CLEAN_URLS, RuleSource.CLEAN_URLS), sink.applications.map { it.source })
        assertEquals(listOf("s=1"), sink.applications[0].removedParameters)
        assertEquals(listOf("utm_source=x"), sink.applications[1].removedParameters)
        assertEquals("https://x.bird.example/s?utm_source=x&k=1", sink.applications[0].outputUrl)
        assertEquals(listOf("s", "utm_source"), sink.removedParameters.map { it.name })
    }

    @Test
    fun `the real facebook rule strips both encodings of the click tracker`() {
        val real = QuerySanitizer(BraveRules.load().cleanUrls)
        val out =
            real.apply(
                UrlParts.parse("https://www.facebook.com/x/posts/1?__cft__[0]=a&__cft__%5B0%5D=b&__tn__=c&keep=1")!!,
                TraceSink("r"),
            )
        assertEquals("https://www.facebook.com/x/posts/1?keep=1", out.toUrlString())
    }
}
