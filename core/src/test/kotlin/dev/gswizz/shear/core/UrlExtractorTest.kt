/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UrlExtractorTest {
    @Test
    fun `returns nothing when the text has no urls`() {
        assertEquals(emptyList<UrlExtractor.Match>(), UrlExtractor.extract("just some words, no links"))
    }

    @Test
    fun `finds a lone url and reports its offsets`() {
        val text = "https://example.com/a?b=c"
        assertEquals(listOf(UrlExtractor.Match(text, 0 until text.length)), UrlExtractor.extract(text))
    }

    @Test
    fun `finds several urls in order and leaves surrounding text untouched`() {
        val text = "see http://one.example/x and HTTPS://two.example/y?z=1 thanks"
        val matches = UrlExtractor.extract(text)
        assertEquals(listOf("http://one.example/x", "HTTPS://two.example/y?z=1"), matches.map { it.url })
        assertEquals(matches.map { it.url }, matches.map { text.substring(it.range) })
    }

    @Test
    fun `sheds punctuation that prose attaches to a link`() {
        val matches = UrlExtractor.extract("Read this: https://example.com/post. Then (https://example.com/other), ok?")
        assertEquals(listOf("https://example.com/post", "https://example.com/other"), matches.map { it.url })
    }

    @Test
    fun `keeps a closing parenthesis that the url itself opened`() {
        val url = "https://en.wikipedia.org/wiki/Shear_(disambiguation)"
        assertEquals(listOf(url), UrlExtractor.extract("(see $url)").map { it.url })
    }

    @Test
    fun `ignores schemes other than http and https`() {
        assertEquals(emptyList<UrlExtractor.Match>(), UrlExtractor.extract("ftp://files.example/x mailto:a@b.c"))
    }
}
