/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.url

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class UrlReferenceTest {
    private val base = UrlParts.parse("http://a.example/b/c/d;p?q")!!

    // RFC 3986 §5.4.1 normal examples that stay within http(s).
    private val normal =
        listOf(
            "g" to "http://a.example/b/c/g",
            "./g" to "http://a.example/b/c/g",
            "g/" to "http://a.example/b/c/g/",
            "/g" to "http://a.example/g",
            "//g.example" to "http://g.example",
            "?y" to "http://a.example/b/c/d;p?y",
            "g?y" to "http://a.example/b/c/g?y",
            "#s" to "http://a.example/b/c/d;p?q#s",
            "g#s" to "http://a.example/b/c/g#s",
            "g?y#s" to "http://a.example/b/c/g?y#s",
            ";x" to "http://a.example/b/c/;x",
            "g;x" to "http://a.example/b/c/g;x",
            "" to "http://a.example/b/c/d;p?q",
            "." to "http://a.example/b/c/",
            "./" to "http://a.example/b/c/",
            ".." to "http://a.example/b/",
            "../" to "http://a.example/b/",
            "../g" to "http://a.example/b/g",
            "../.." to "http://a.example/",
            "../../" to "http://a.example/",
            "../../g" to "http://a.example/g",
            "../../../g" to "http://a.example/g",
            "/./g" to "http://a.example/g",
            "/../g" to "http://a.example/g",
            "g." to "http://a.example/b/c/g.",
            ".g" to "http://a.example/b/c/.g",
            "g.." to "http://a.example/b/c/g..",
            "..g" to "http://a.example/b/c/..g",
            "./../g" to "http://a.example/b/g",
            "g/./h" to "http://a.example/b/c/g/h",
            "g/../h" to "http://a.example/b/c/h",
            "https://other.example/x?y#z" to "https://other.example/x?y#z",
        )

    @TestFactory
    fun `resolves references like rfc 3986`(): List<DynamicTest> = normal.map { (ref, expected) ->
        DynamicTest.dynamicTest("'$ref'") { assertEquals(expected, UrlReference.resolve(base, ref)?.toUrlString()) }
    }

    @Test
    fun `a location with a foreign scheme is not resolvable but is recognized as absolute`() {
        assertNull(UrlReference.resolve(base, "mailto:x@y.example"))
        assertEquals("mailto", UrlReference.schemeOf("mailto:x@y.example"))
        assertEquals("javascript", UrlReference.schemeOf("javascript:alert(1)"))
        assertNull(UrlReference.schemeOf("/relative"))
        assertNull(UrlReference.schemeOf("no-colon"))
        assertNull(UrlReference.schemeOf("1http://digit-first.example"))
    }

    @Test
    fun `keeps the base port and drops the base userinfo only when authority changes`() {
        val portBase = UrlParts.parse("https://u@a.example:8443/x/y?z")!!
        assertEquals("https://u@a.example:8443/x/g", UrlReference.resolve(portBase, "g")?.toUrlString())
        assertEquals("https://b.example/g", UrlReference.resolve(portBase, "//b.example/g")?.toUrlString())
    }
}
