/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core

import dev.gswizz.shear.core.engine.FailureKind
import dev.gswizz.shear.core.net.FakeTransport
import dev.gswizz.shear.core.net.ResolveMode
import dev.gswizz.shear.core.net.redirect
import dev.gswizz.shear.core.url.PercentCodec
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShearTest {
    private val transport =
        FakeTransport(
            mapOf("https://bit.ly/abc" to listOf(redirect(301, "https://long.example/article?utm_source=tw")))
        )
    private val shear = Shear.default(transport)

    @Test
    fun `cleans every url in the text and leaves the prose byte for byte`() {
        val wrapped =
            "https://go.skimresources.com/?id=9&url=${PercentCodec.formEncode("https://shop.example/item?utm_campaign=x&sku=1")}"
        val text = "Look: https://news.example/a?fbclid=abc&id=7, and $wrapped (nice)"
        val result = shear.clean(text)
        assertEquals("Look: https://news.example/a?id=7, and https://shop.example/item?sku=1 (nice)", result.outputText)
        assertEquals(text, result.originalText)
        assertTrue(result.changed)
        assertEquals(listOf("https://news.example/a?fbclid=abc&id=7", wrapped), result.urls.map { it.originalUrl })
        assertEquals(
            listOf("https://news.example/a?id=7", "https://shop.example/item?sku=1"),
            result.urls.map { it.finalUrl },
        )
    }

    @Test
    fun `text without urls or with clean urls is returned unchanged`() {
        assertEquals("just words", shear.clean("just words").outputText)
        assertTrue(shear.clean("just words").urls.isEmpty())
        val clean = "see https://a.example/x?keep=1 ok"
        val result = shear.clean(clean)
        assertEquals(clean, result.outputText)
        assertFalse(result.changed)
        assertEquals(1, result.urls.size)
    }

    @Test
    fun `process resolves over the network according to the mode`() = runTest {
        val text = "short https://bit.ly/abc here"
        assertEquals(text, shear.process(text, ResolveMode.OFF).outputText)
        assertTrue(transport.requests.isEmpty())
        assertEquals("short https://long.example/article here", shear.process(text, ResolveMode.SMART).outputText)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `needsNetwork is true only when a url would actually be fetched`() {
        assertFalse(shear.needsNetwork("https://bit.ly/abc", ResolveMode.OFF))
        assertTrue(shear.needsNetwork("https://bit.ly/abc", ResolveMode.SMART))
        assertFalse(shear.needsNetwork("https://plain.example/x", ResolveMode.SMART))
        assertTrue(shear.needsNetwork("https://plain.example/x", ResolveMode.RESOLVE_ALL))
        assertFalse(shear.needsNetwork("no links", ResolveMode.RESOLVE_ALL))
        val wrappedShort = "https://go.skimresources.com/?url=${PercentCodec.formEncode("https://bit.ly/zzz")}"
        assertTrue(shear.needsNetwork(wrappedShort, ResolveMode.SMART))
    }

    @Test
    fun `a failed resolution keeps the offline result and surfaces the failure`() = runTest {
        val looping = FakeTransport(mapOf("https://bit.ly/loop" to listOf(redirect(301, "https://bit.ly/loop"))))
        val result = Shear.default(looping).process("https://bit.ly/loop", ResolveMode.SMART)
        assertEquals("https://bit.ly/loop", result.outputText)
        assertEquals(FailureKind.LOOP, result.urls.single().failure?.kind)
    }

    @Test
    fun `exposes the rules version and health`() {
        assertEquals(40, shear.rulesVersion.length)
        assertTrue(shear.rulesHealthy)
    }
}
