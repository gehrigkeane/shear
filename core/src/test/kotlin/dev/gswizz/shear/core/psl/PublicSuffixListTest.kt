/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.psl

import dev.gswizz.shear.core.url.UrlParts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class PublicSuffixListTest {
    private val fixture =
        PublicSuffixList.parse(
            """
            // ===BEGIN ICANN DOMAINS===
            com
            uk
            co.uk
            jp
            kawasaki.jp
            *.kawasaki.jp
            !city.kawasaki.jp
            ck
            *.ck
            !www.ck
            // ===END ICANN DOMAINS===
            // ===BEGIN PRIVATE DOMAINS===
            blogspot.com
            // ===END PRIVATE DOMAINS===
            """
                .trimIndent()
        )

    @Test
    fun `exact wildcard and exception rules`() {
        assertEquals("com", fixture.publicSuffix("example.com"))
        assertEquals("example.com", fixture.registrableDomain("www.example.com"))
        assertEquals("example.co.uk", fixture.registrableDomain("a.b.example.co.uk"))
        assertNull(fixture.registrableDomain("co.uk"))
        assertEquals("kawasaki.jp", fixture.publicSuffix("kawasaki.jp"))
        assertEquals("x.kawasaki.jp", fixture.publicSuffix("a.x.kawasaki.jp"))
        assertEquals("kawasaki.jp", fixture.publicSuffix("city.kawasaki.jp"))
        assertEquals("city.kawasaki.jp", fixture.registrableDomain("www.city.kawasaki.jp"))
        assertEquals("www.ck", fixture.registrableDomain("www.ck"))
        assertEquals("a.other.ck", fixture.registrableDomain("a.other.ck"))
    }

    @Test
    fun `unlisted tlds fall back to the last label and single labels have no registrable domain`() {
        assertEquals("example", fixture.publicSuffix("foo.example"))
        assertEquals("foo.example", fixture.registrableDomain("bar.foo.example"))
        assertNull(fixture.registrableDomain("example"))
        assertNull(fixture.registrableDomain("com"))
    }

    @Test
    fun `private registries are excluded unless asked for`() {
        assertEquals("blogspot.com", fixture.registrableDomain("me.blogspot.com"))
        assertEquals("me.blogspot.com", fixture.registrableDomain("me.blogspot.com", includePrivate = true))
    }

    @Test
    fun `ip literals empty labels and trailing dots`() {
        assertNull(fixture.registrableDomain("127.0.0.1"))
        assertNull(fixture.registrableDomain("[::1]"))
        assertNull(fixture.registrableDomain(".example.com"))
        assertNull(fixture.registrableDomain("example..com"))
        assertNull(fixture.registrableDomain(""))
        assertEquals("example.com", fixture.registrableDomain("WWW.Example.COM."))
    }

    @Test
    fun `same site compares registrable domains and falls back to hosts`() {
        fun u(s: String) = UrlParts.parse(s)!!
        assertTrue(fixture.sameSite(u("https://a.example.com/x"), u("http://b.example.com/y")))
        assertFalse(fixture.sameSite(u("https://example.com/"), u("https://example.co.uk/")))
        assertTrue(fixture.sameSite(u("https://localhost/"), u("https://localhost/x")))
        assertFalse(fixture.sameSite(u("https://localhost/"), u("https://other/")))
        assertTrue(fixture.sameSite(u("https://127.0.0.1/"), u("https://127.0.0.1:8080/")))
    }

    // Commented-out vectors in the upstream file are skipped, exactly as the reference harness skips them.
    private val checks = Regex("""^checkPublicSuffix\((null|'[^']*'), (null|'[^']*')\);""", RegexOption.MULTILINE)

    @TestFactory
    fun `publicsuffix org test vectors pass against the vendored list`(): List<DynamicTest> {
        val list = PublicSuffixList.load()
        val text = requireNotNull(javaClass.getResourceAsStream("/psl/test_psl.txt")).bufferedReader().readText()
        return checks
            .findAll(text)
            .map { m ->
                fun arg(s: String) = if (s == "null") null else s.trim('\'')
                val input = arg(m.groupValues[1])
                val expected = arg(m.groupValues[2])
                DynamicTest.dynamicTest("${input ?: "null"} -> ${expected ?: "null"}") {
                    val actual = input?.let { list.registrableDomain(it, includePrivate = true) }
                    assertEquals(expected, actual)
                }
            }
            .toList()
    }

    @Test
    fun `the vendored list loads with both sections`() {
        val list = PublicSuffixList.load()
        assertEquals("example.co.uk", list.registrableDomain("www.example.co.uk"))
        assertEquals("github.io", list.publicSuffix("me.github.io", includePrivate = true))
        assertEquals("io", list.publicSuffix("me.github.io"))
    }
}
