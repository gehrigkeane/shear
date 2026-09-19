/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core

import dev.gswizz.shear.core.url.PercentCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShearTest {
    private val shear = Shear.default()

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
    fun `exposes the rules version and health`() {
        assertEquals(40, shear.rulesVersion.length)
        assertTrue(shear.rulesHealthy)
    }
}
