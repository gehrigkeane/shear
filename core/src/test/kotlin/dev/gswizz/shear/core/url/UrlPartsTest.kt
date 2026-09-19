/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.url

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class UrlPartsTest {
    private val roundTrip =
        listOf(
            "https://example.com",
            "https://example.com/",
            "HTTPS://Example.COM/Path?Q=1#Frag",
            "http://example.com:8080/a/b?c=d&e=f#g",
            "http://example.com:/empty-port",
            "https://user:pa%40ss@example.com/",
            "https://[2001:db8::1]/v6",
            "https://[2001:db8::1]:8443/v6?x",
            "https://bücher.example/straße?q=ä",
            "https://example.com/%zz/not-an-escape",
            "https://example.com/a?b[]=1&b[]=2",
            "https://example.com/a?",
            "https://example.com/a#",
            "https://example.com/a?&&=&#x",
            "https://example.com./trailing-dot",
            "https://example.com/a?utm_source=x&utm_source=y",
            "https://example.com/p?q=%2Fkeep%2Fencoded",
        )

    @TestFactory
    fun `parse then toUrlString is the identity`(): List<DynamicTest> = roundTrip.map { raw ->
        DynamicTest.dynamicTest(raw) {
            val parts = UrlParts.parse(raw)
            assertNotNull(parts, "should parse: $raw")
            assertEquals(raw, parts!!.toUrlString())
        }
    }

    @Test
    fun `splits every component without normalizing`() {
        val parts = UrlParts.parse("HTTPS://u@Example.COM.:8443/a/b?c=d#e")!!
        assertEquals("HTTPS", parts.scheme)
        assertEquals("u", parts.userInfo)
        assertEquals("Example.COM.", parts.host)
        assertEquals("example.com", parts.hostLower)
        assertEquals("8443", parts.port)
        assertEquals("/a/b", parts.path)
        assertEquals("c=d", parts.query)
        assertEquals("e", parts.fragment)
        assertEquals("/a/b?c=d", parts.pathForRequest)
    }

    @Test
    fun `distinguishes absent from empty query and fragment`() {
        val none = UrlParts.parse("https://example.com/a")!!
        assertNull(none.query)
        assertNull(none.fragment)
        assertEquals("/a", none.pathForRequest)
        val empty = UrlParts.parse("https://example.com/a?#")!!
        assertEquals("", empty.query)
        assertEquals("", empty.fragment)
    }

    @Test
    fun `an empty path is requested as the root`() {
        assertEquals("/", UrlParts.parse("https://example.com")!!.pathForRequest)
        assertEquals("/?q", UrlParts.parse("https://example.com?q")!!.pathForRequest)
    }

    @Test
    fun `withQuery and withPath rebuild exactly`() {
        val parts = UrlParts.parse("https://example.com/a?b=1#c")!!
        assertEquals("https://example.com/a#c", parts.withQuery(null).toUrlString())
        assertEquals("https://example.com/a?#c", parts.withQuery("").toUrlString())
        assertEquals("https://example.com/z?b=1#c", parts.withPath("/z").toUrlString())
    }

    private val rejects =
        listOf(
            "ftp://example.com/",
            "mailto:someone@example.com",
            "javascript:alert(1)",
            "http:/example.com",
            "http://",
            "https:///path",
            "https://exa mple.com/",
            "https://example.com/a b",
            "https://example.com/a\tb",
            "https://back\\slash.example/",
            "https://example.com\\evil/",
            "https://exam<ple.com/",
            "https://example..com/",
            "https://.example.com/",
            "https://example.com:99999/",
            "https://example.com:12a/",
            "https://[not-hex]/",
            "https://[2001:db8::1/",
            "https://example.com/\u0007bell",
            "example.com/no-scheme",
            "https://" + "a".repeat(9000) + ".example/",
        )

    @TestFactory
    fun `rejects anything that is not a plausible http or https url`(): List<DynamicTest> = rejects.map { raw ->
        DynamicTest.dynamicTest(raw.take(60)) { assertNull(UrlParts.parse(raw)) }
    }

    @Test
    fun `equality follows the exact text`() {
        assertEquals(UrlParts.parse("https://a.example/x?y"), UrlParts.parse("https://a.example/x?y"))
        assertEquals(false, UrlParts.parse("https://a.example/x") == UrlParts.parse("https://A.example/x"))
    }
}
