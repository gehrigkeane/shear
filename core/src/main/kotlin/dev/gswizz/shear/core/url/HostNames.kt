/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.url

/** Host-name helpers shared by matching and the resolver. Nothing here performs IDN conversion. */
public object HostNames {
    /**
     * Returns [host] in the form rules compare against: lowercased with one trailing dot removed.
     *
     * Mirrors Chromium's `CanonicalizeHostForMatching`. Bracketed IPv6 literals pass through unchanged apart from case.
     */
    public fun normalizeForMatching(host: String): String {
        val stripped = if (host.length > 1 && host.endsWith('.')) host.dropLast(1) else host
        return stripped.lowercase()
    }

    /** True for a bracketed IPv6 literal or a dotted-decimal IPv4 address; false for every registered name. */
    public fun isIpLiteral(host: String): Boolean {
        if (host.startsWith('[') && host.endsWith(']')) return true
        val labels = host.split('.')
        if (labels.size != 4) return false
        return labels.all { label -> label.length in 1..3 && label.all(Char::isDigit) && label.toInt() <= MAX_OCTET }
    }

    private const val MAX_OCTET = 255
}
