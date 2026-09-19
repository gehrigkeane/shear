/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.net

/**
 * The hosts Smart mode is willing to contact: shorteners and other redirectors whose links say nothing about their
 * destination. A listed host covers its subdomains. Loaded from `shear/opaque-redirectors.txt`.
 */
public class OpaqueRedirectors(hosts: Set<String>) {
    private val hosts = hosts.map { it.lowercase().removeSuffix(".") }.toSet()

    public fun contains(host: String): Boolean {
        val h = host.lowercase().removeSuffix(".")
        return h in hosts || hosts.any { h.endsWith(".$it") }
    }

    public companion object {
        private const val RESOURCE = "/shear/opaque-redirectors.txt"

        /** Loads the bundled list. */
        public fun load(): OpaqueRedirectors {
            val text =
                requireNotNull(OpaqueRedirectors::class.java.getResourceAsStream(RESOURCE)) { "missing $RESOURCE" }
            return parse(text.bufferedReader().readText())
        }

        /** Parses one host per line; blank lines and `#` comments are ignored. */
        public fun parse(text: String): OpaqueRedirectors =
            OpaqueRedirectors(
                text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
            )
    }
}
