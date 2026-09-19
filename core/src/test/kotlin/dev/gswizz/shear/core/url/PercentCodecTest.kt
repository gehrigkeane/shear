/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.url

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PercentCodecTest {
    @Test
    fun `decodes ascii escapes and plus when asked`() {
        assertEquals(
            "https://a.example/p?q=1&r=2",
            PercentCodec.unescapeChromium("https%3A%2F%2Fa.example%2Fp%3Fq%3D1%26r%3D2", plusToSpace = true),
        )
        assertEquals("a b", PercentCodec.unescapeChromium("a+b", plusToSpace = true))
        assertEquals("a+b", PercentCodec.unescapeChromium("a+b", plusToSpace = false))
        assertEquals("a b", PercentCodec.unescapeChromium("a%20b", plusToSpace = false))
    }

    @Test
    fun `keeps control characters and malformed escapes escaped`() {
        assertEquals("a%00b", PercentCodec.unescapeChromium("a%00b", plusToSpace = true))
        assertEquals("a%1Fb%7F", PercentCodec.unescapeChromium("a%1Fb%7F", plusToSpace = true))
        assertEquals("a%zzb%", PercentCodec.unescapeChromium("a%zzb%", plusToSpace = true))
        assertEquals("100%", PercentCodec.unescapeChromium("100%", plusToSpace = true))
    }

    @Test
    fun `decodes valid utf8 runs and keeps invalid or unsafe ones`() {
        assertEquals("straße", PercentCodec.unescapeChromium("stra%C3%9Fe", plusToSpace = true))
        assertEquals("日本", PercentCodec.unescapeChromium("%E6%97%A5%E6%9C%AC", plusToSpace = true))
        assertEquals("%C3", PercentCodec.unescapeChromium("%C3", plusToSpace = true))
        assertEquals("%C3x", PercentCodec.unescapeChromium("%C3x", plusToSpace = true))
        assertEquals("a%E2%80%8Eb", PercentCodec.unescapeChromium("a%E2%80%8Eb", plusToSpace = true))
        assertEquals("a%EF%BB%BFb", PercentCodec.unescapeChromium("a%EF%BB%BFb", plusToSpace = true))
    }

    @Test
    fun `escapeIllegal strips whitespace and encodes what gurl would`() {
        assertEquals("https://a.example/p", PercentCodec.escapeIllegal("  https://a.example/p \n"))
        assertEquals("https://a.example/pq", PercentCodec.escapeIllegal("https://a.exa\tmple/p\r\nq"))
        assertEquals(
            "https://a.example/a%20b?c=%22d%22#e%3Cf",
            PercentCodec.escapeIllegal("https://a.example/a b?c=\"d\"#e<f"),
        )
        assertEquals("https://a.example/stra%C3%9Fe", PercentCodec.escapeIllegal("https://a.example/straße"))
        assertEquals("https://a.example/x", PercentCodec.escapeIllegal("HTTPS://A.Example/x"))
        assertEquals("https://a.example/%2Fkept", PercentCodec.escapeIllegal("https://a.example/%2Fkept"))
        assertEquals("www.a.example/p", PercentCodec.escapeIllegal("www.a.example/p"))
    }

    @Test
    fun `formEncode is the inverse of unescape for destination urls`() {
        val url = "https://dest.example/landing?x=1&y=2 3"
        assertEquals(url, PercentCodec.unescapeChromium(PercentCodec.formEncode(url), plusToSpace = true))
        assertEquals("https%3A%2F%2Fdest.example%2Flanding%3Fx%3D1%26y%3D2+3", PercentCodec.formEncode(url))
    }
}
