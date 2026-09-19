/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortenPathTest {
    @Test
    fun `drops characters from the middle of the path and keeps the host whole`() {
        assertEquals("https://shop.example/it…42?", shortenPath("https://shop.example/item/42?", 3))
    }

    @Test
    fun `a longer cut still leaves the first and last path characters`() {
        assertEquals("https://shop.example/…?", shortenPath("https://shop.example/item/42?", 100))
    }

    @Test
    fun `without a path the middle of the whole text gives way`() {
        assertEquals("shop…ample", shortenPath("shop.example", 3))
    }

    @Test
    fun `a text already short enough is returned as is`() {
        assertEquals("https://a.b/c", shortenPath("https://a.b/c", 0))
    }
}
