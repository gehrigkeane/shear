/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.rules

import dev.gswizz.shear.core.url.UrlParts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MatchPatternTest {
    private fun p(pattern: String) = MatchPattern.parse(pattern) ?: error("pattern should parse: $pattern")

    private fun u(url: String) = UrlParts.parse(url) ?: error("url should parse: $url")

    @Test
    fun `parses the shapes brave uses`() {
        val any = p("*://*/*")
        assertEquals(MatchPattern.Scheme.ANY, any.scheme)
        assertEquals("", any.host)
        assertTrue(any.matchSubdomains)
        assertEquals("/*", any.pathGlob)
        val sub = p("*://*.amazon.com/*")
        assertEquals("amazon.com", sub.host)
        assertTrue(sub.matchSubdomains)
        val exact = p("https://dev-pages.bravesoftware.com/clean-urls/*")
        assertEquals(MatchPattern.Scheme.HTTPS, exact.scheme)
        assertEquals("dev-pages.bravesoftware.com", exact.host)
        assertFalse(exact.matchSubdomains)
        assertEquals("/clean-urls/*", exact.pathGlob)
        assertTrue(p("<all_urls>").matchAllUrls)
    }

    @Test
    fun `rejects ports, foreign schemes, and missing paths`() {
        assertNull(MatchPattern.parse("*://*.x.com:80/*"))
        assertNull(MatchPattern.parse("ftp://x.com/*"))
        assertNull(MatchPattern.parse("file:///*"))
        assertNull(MatchPattern.parse("*://x.com"))
        assertNull(MatchPattern.parse("x.com/*"))
        assertNull(MatchPattern.parse(""))
    }

    @Test
    fun `matches hosts with and without subdomains and ignores case`() {
        val sub = p("*://*.example.com/*")
        assertTrue(sub.matches(u("https://example.com/")))
        assertTrue(sub.matches(u("http://sub.deep.example.com/a?b")))
        assertTrue(sub.matches(u("https://EXAMPLE.com./x")))
        assertFalse(sub.matches(u("https://notexample.com/")))
        assertFalse(sub.matches(u("https://example.com.evil/")))
        val exact = p("*://example.com/*")
        assertTrue(exact.matches(u("https://example.com/")))
        assertFalse(exact.matches(u("https://www.example.com/")))
    }

    @Test
    fun `scheme wildcard means http or https only`() {
        assertFalse(p("https://*/x").matches(u("http://a.example/x")))
        assertTrue(p("http://*/x").matches(u("HTTP://a.example/x")))
        assertTrue(p("*://*/x").matches(u("https://a.example/x")))
    }

    @Test
    fun `path glob spans slashes and the query, and a literal question mark is literal`() {
        val query = p("*://*.prsm1.com/r?*")
        assertTrue(query.matches(u("https://www.prsm1.com/r?u=1")))
        assertFalse(query.matches(u("https://www.prsm1.com/r")))
        assertFalse(query.matches(u("https://www.prsm1.com/rx")))
        assertTrue(p("*://*.cdn.ampproject.org/c/s/*").matches(u("https://www-x.cdn.ampproject.org/c/s/www.x/y?z")))
        assertTrue(p("*://y2u.be/*").matches(u("https://y2u.be")))
        assertTrue(p("*://a.example/*/end").matches(u("https://a.example/x/y/end")))
        assertFalse(p("*://a.example/*/end").matches(u("https://a.example/x/y/end?q")))
    }

    @Test
    fun `a glob ending in slash star also matches the bare directory`() {
        val e = p("*://*/e/*")
        assertTrue(e.matches(u("https://a.example/e")))
        assertTrue(e.matches(u("https://a.example/e/")))
        assertTrue(e.matches(u("https://a.example/e/x")))
        assertFalse(e.matches(u("https://a.example/ex")))
    }

    @Test
    fun `path matching is case sensitive`() {
        assertFalse(p("*://a.example/A*").matches(u("https://a.example/a")))
        assertTrue(p("*://a.example/A*").matches(u("https://a.example/A")))
    }

    @Test
    fun `all_urls matches any http or https url`() {
        val all = p("<all_urls>")
        assertTrue(all.matches(u("https://anything.example/whatever?x#y")))
        assertTrue(all.matches(u("http://[::1]/")))
    }

    @Test
    fun `pattern sets report the first matching include unless excluded`() {
        val set =
            PatternSet(listOf(p("*://a.example/*"), p("*://*.b.example/*")), listOf(p("*://*.b.example/private/*")))
        assertEquals(0, set.matches(u("https://a.example/x")))
        assertEquals(1, set.matches(u("https://c.b.example/x")))
        assertNull(set.matches(u("https://c.b.example/private/x")))
        assertNull(set.matches(u("https://other.example/")))
        assertNull(PatternSet(emptyList(), emptyList()).matches(u("https://a.example/")))
    }
}
