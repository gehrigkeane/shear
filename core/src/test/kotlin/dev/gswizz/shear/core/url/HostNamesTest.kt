/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.url

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HostNamesTest {
    @Test
    fun `lowercases ascii and strips one trailing dot`() {
        assertEquals("example.com", HostNames.normalizeForMatching("Example.COM."))
        assertEquals("example.com.", HostNames.normalizeForMatching("example.com.."))
        assertEquals("bücher.example", HostNames.normalizeForMatching("BÜCHER.example"))
    }

    @Test
    fun `recognizes ip literals`() {
        assertTrue(HostNames.isIpLiteral("[::1]"))
        assertTrue(HostNames.isIpLiteral("127.0.0.1"))
        assertTrue(HostNames.isIpLiteral("10.0.0.255"))
        assertFalse(HostNames.isIpLiteral("256.0.0.1"))
        assertFalse(HostNames.isIpLiteral("1.2.3"))
        assertFalse(HostNames.isIpLiteral("example.com"))
        assertFalse(HostNames.isIpLiteral("1.2.3.4.5"))
    }
}
