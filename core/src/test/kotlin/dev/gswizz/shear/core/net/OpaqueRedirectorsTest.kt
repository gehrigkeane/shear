/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.net

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpaqueRedirectorsTest {
    @Test
    fun `parses one host per line ignoring comments and blanks and matches subdomains case-insensitively`() {
        val list = OpaqueRedirectors.parse("# comment\n\nbit.ly\n  T.co  \n")
        assertTrue(list.contains("bit.ly"))
        assertTrue(list.contains("www.bit.ly"))
        assertTrue(list.contains("BIT.LY"))
        assertTrue(list.contains("t.co"))
        assertFalse(list.contains("notbit.ly"))
        assertFalse(list.contains("bit.ly.evil"))
        assertFalse(list.contains("example.com"))
    }

    @Test
    fun `the bundled list has the usual shorteners and no security interstitials`() {
        val list = OpaqueRedirectors.load()
        assertTrue(list.contains("bit.ly") && list.contains("t.co") && list.contains("amzn.to"))
        assertFalse(list.contains("youtu.be"))
        assertFalse(list.contains("l.facebook.com"))
        assertFalse(list.contains("www.google.com"))
    }
}
