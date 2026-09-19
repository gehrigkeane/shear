/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.net

import java.net.InetAddress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class AddressPolicyTest {
    private val blocked =
        listOf(
            "127.0.0.1",
            "127.8.8.8",
            "::1",
            "10.0.0.1",
            "172.16.0.1",
            "172.31.255.255",
            "192.168.1.1",
            "169.254.1.1",
            "fe80::1",
            "fc00::1",
            "fd12:3456::1",
            "::ffff:10.0.0.1",
            "0.0.0.0",
            "0.1.2.3",
            "::",
            "100.64.0.1",
            "100.127.255.255",
            "224.0.0.1",
            "ff02::1",
        )
    private val allowed =
        listOf(
            "8.8.8.8",
            "1.1.1.1",
            "2001:4860:4860::8888",
            "100.128.0.1",
            "172.32.0.1",
            "192.169.0.1",
            "11.0.0.1",
            "2606:4700::1111",
        )

    @TestFactory
    fun `the default policy blocks every non-public range`(): List<DynamicTest> =
        blocked.map { ip ->
            DynamicTest.dynamicTest("blocks $ip") {
                assertEquals(true, AddressPolicy.Default.isBlocked(InetAddress.getByName(ip)))
            }
        } +
            allowed.map { ip ->
                DynamicTest.dynamicTest("allows $ip") {
                    assertEquals(false, AddressPolicy.Default.isBlocked(InetAddress.getByName(ip)))
                }
            } +
            DynamicTest.dynamicTest("AllowAll allows loopback") {
                assertEquals(false, AddressPolicy.AllowAll.isBlocked(InetAddress.getByName("127.0.0.1")))
            }
}
