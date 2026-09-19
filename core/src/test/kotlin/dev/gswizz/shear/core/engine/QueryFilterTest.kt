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

class QueryFilterTest {
    private val rules =
        BraveRules.compile(
            version = "t",
            cleanUrls = emptyList(),
            queryFilter =
                listOf(
                    ParamRuleDto(include = listOf("*://*/*"), params = listOf("fbclid", "gclid")),
                    ParamRuleDto(include = listOf("*://*.pic.example/*"), params = listOf("igshid")),
                ),
            conditionalTrackers = mapOf("mkt_tok" to "([uU]nsubscribe|emailWebview)", "h_sid" to "/email/"),
            debounce = emptyList(),
        )
    private val filter = QueryFilter(rules.queryFilter, rules.conditionalTrackers)

    private fun clean(url: String, sink: TraceSink = TraceSink("t")): String =
        filter.apply(UrlParts.parse(url)!!, sink).toUrlString()

    @Test
    fun `strips blocked parameters from matching rules only`() {
        assertEquals("https://a.example/p?x=1", clean("https://a.example/p?fbclid=abc&x=1&gclid=d"))
        assertEquals("https://a.example/p?igshid=1", clean("https://a.example/p?igshid=1"))
        assertEquals("https://www.pic.example/p/1/", clean("https://www.pic.example/p/1/?igshid=1"))
    }

    @Test
    fun `keeps tokens without a key or a value and tokens without an equals sign`() {
        assertEquals("https://a.example/p?fbclid=&x", clean("https://a.example/p?fbclid=&x"))
        assertEquals("https://a.example/p?=1&fbclid", clean("https://a.example/p?=1&fbclid"))
        assertEquals("https://a.example/p?", clean("https://a.example/p?"))
    }

    @Test
    fun `conditional trackers are stripped unless the url matches their pattern`() {
        assertEquals("https://a.example/p?x=1", clean("https://a.example/p?mkt_tok=abc&x=1"))
        assertEquals("https://a.example/unsubscribe?mkt_tok=abc", clean("https://a.example/unsubscribe?mkt_tok=abc"))
        assertEquals("https://a.example/emailWebview?mkt_tok=abc", clean("https://a.example/emailWebview?mkt_tok=abc"))
        assertEquals("https://a.example/email/x?h_sid=1", clean("https://a.example/email/x?h_sid=1"))
        assertEquals("https://a.example/x", clean("https://a.example/x?h_sid=1"))
    }

    @Test
    fun `urldefense is exempt exactly and only by host`() {
        assertEquals("https://urldefense.com/v3/?fbclid=1", clean("https://urldefense.com/v3/?fbclid=1"))
        assertEquals("https://www.urldefense.com/v3/", clean("https://www.urldefense.com/v3/?fbclid=1"))
    }

    @Test
    fun `attributes removals to the first rule listing the key and conditional trackers to index minus one`() {
        val sink = TraceSink("t")
        clean("https://www.pic.example/p?fbclid=1&igshid=2&mkt_tok=3&keep=4", sink)
        assertEquals(listOf(0, 1, -1), sink.applications.map { it.ruleIndex })
        assertEquals(listOf("fbclid=1"), sink.applications[0].removedParameters)
        assertEquals(listOf("igshid=2"), sink.applications[1].removedParameters)
        assertEquals(listOf("mkt_tok=3"), sink.applications[2].removedParameters)
        assertEquals(RuleSource.QUERY_FILTER, sink.applications[2].source)
        assertEquals("https://www.pic.example/p?keep=4", sink.applications[2].outputUrl)
    }

    @Test
    fun `the vendored list strips fbclid everywhere`() {
        val real = BraveRules.load()
        val out =
            QueryFilter(real.queryFilter, real.conditionalTrackers)
                .apply(UrlParts.parse("https://news.example/a?fbclid=x&id=7")!!, TraceSink("r"))
        assertEquals("https://news.example/a?id=7", out.toUrlString())
    }
}
