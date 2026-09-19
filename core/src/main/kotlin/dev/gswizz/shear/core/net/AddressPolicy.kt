/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.net

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Decides which resolved addresses the resolver may connect to.
 *
 * A redirect chain that starts on the public internet must never be able to steer a request at the phone itself, the
 * local network, or carrier-internal ranges.
 */
public fun interface AddressPolicy {
    public fun isBlocked(address: InetAddress): Boolean

    public companion object {
        /**
         * Blocks loopback, link-local, site-local (RFC 1918 and fec0::/10), unspecified, multicast, IPv6 unique-local
         * (fc00::/7), the IPv4 "this network" block (0.0.0.0/8), and carrier-grade NAT space (100.64.0.0/10), including
         * their IPv4-mapped IPv6 forms.
         */
        public val Default: AddressPolicy = AddressPolicy { address -> isNonPublic(unmap(address)) }

        /** Permits everything; for tests that talk to a local server. */
        public val AllowAll: AddressPolicy = AddressPolicy { false }

        private fun isNonPublic(address: InetAddress): Boolean {
            if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) return true
            if (address.isAnyLocalAddress || address.isMulticastAddress) return true
            val bytes = address.address
            return when (address) {
                is Inet4Address -> {
                    val first = bytes[0].toInt() and 0xFF
                    val second = bytes[1].toInt() and 0xFF
                    first == 0 || (first == CGNAT_FIRST_OCTET && second in CGNAT_SECOND_OCTETS)
                }
                is Inet6Address -> (bytes[0].toInt() and 0xFE) == ULA_PREFIX
                else -> true
            }
        }

        /** Returns the IPv4 address inside an IPv4-mapped IPv6 address, or the input unchanged. */
        private fun unmap(address: InetAddress): InetAddress {
            if (address !is Inet6Address) return address
            val b = address.address
            val mapped =
                (0 until MAPPED_ZERO_BYTES).all { b[it].toInt() == 0 } &&
                    b[MAPPED_ZERO_BYTES] == MAPPED_MARKER &&
                    b[MAPPED_ZERO_BYTES + 1] == MAPPED_MARKER
            return if (mapped) InetAddress.getByAddress(b.copyOfRange(MAPPED_ZERO_BYTES + 2, b.size)) else address
        }

        private const val CGNAT_FIRST_OCTET = 100
        private val CGNAT_SECOND_OCTETS = 64..127
        private const val ULA_PREFIX = 0xFC
        private const val MAPPED_ZERO_BYTES = 10
        private const val MAPPED_MARKER = 0xFF.toByte()
    }
}
