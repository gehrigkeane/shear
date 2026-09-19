/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.url

/**
 * An http(s) URL split into its components exactly as written, with no normalization of any kind.
 *
 * Every transformation in the engine operates on this model so that untouched segments survive byte for byte:
 * [toUrlString] reassembles precisely the text that [parse] accepted. Only [hostLower] is derived, and only for
 * matching.
 *
 * `query == null` means the URL has no `?`; `query == ""` means a bare `?`. The same holds for [fragment] and `#`.
 */
public class UrlParts
private constructor(
    public val scheme: String,
    public val userInfo: String?,
    public val host: String,
    public val port: String?,
    public val path: String,
    public val query: String?,
    public val fragment: String?,
) {
    /** The host as rules compare it: lowercased, one trailing dot removed. */
    public val hostLower: String = HostNames.normalizeForMatching(host)

    /** The request target Chromium match patterns are tested against: path (or `/`) plus `?query` when present. */
    public val pathForRequest: String
        get() = path.ifEmpty { "/" } + (query?.let { "?$it" } ?: "")

    public fun withQuery(query: String?): UrlParts = UrlParts(scheme, userInfo, host, port, path, query, fragment)

    public fun withPath(path: String): UrlParts = UrlParts(scheme, userInfo, host, port, path, query, fragment)

    /** Reassembles the URL text; `parse(x)?.toUrlString() == x` for every accepted input. */
    public fun toUrlString(): String = buildString {
        append(scheme).append("://")
        userInfo?.let { append(it).append('@') }
        append(host)
        port?.let { append(':').append(it) }
        append(path)
        query?.let { append('?').append(it) }
        fragment?.let { append('#').append(it) }
    }

    override fun equals(other: Any?): Boolean = other is UrlParts && other.toUrlString() == toUrlString()

    override fun hashCode(): Int = toUrlString().hashCode()

    override fun toString(): String = toUrlString()

    public companion object {
        /**
         * Splits [raw] into components, or returns null when it is not a plausible http(s) URL.
         *
         * Accepted: an `http` or `https` scheme in any case followed by `//`; a non-empty authority whose host is a
         * bracketed IPv6 literal or a dotted name without empty labels (one trailing dot allowed, non-ASCII kept as
         * written); an optional port of at most five digits not exceeding 65535, possibly empty; and a path, query, and
         * fragment free of whitespace and control characters. Percent sequences are never validated or decoded.
         */
        public fun parse(raw: String): UrlParts? {
            if (raw.length > MAX_LENGTH) return null
            val colon = raw.indexOf(':')
            if (colon <= 0) return null
            val scheme = raw.substring(0, colon)
            if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) return null
            if (!raw.startsWith("//", colon + 1)) return null
            val rest = raw.substring(colon + SCHEME_SEPARATOR_LENGTH)
            val authorityEnd = rest.indexOfAny(AUTHORITY_TERMINATORS).let { if (it < 0) rest.length else it }
            val authority = rest.substring(0, authorityEnd)
            if (authority.isEmpty() || authority.any { it in FORBIDDEN_AUTHORITY || isWhitespaceOrControl(it) })
                return null
            val at = authority.lastIndexOf('@')
            val userInfo = if (at >= 0) authority.substring(0, at) else null
            val hostPort = authority.substring(at + 1)
            val (host, port) = splitHostPort(hostPort) ?: return null
            val remainder = rest.substring(authorityEnd)
            if (remainder.any(::isWhitespaceOrControl)) return null
            val hash = remainder.indexOf('#')
            val beforeHash = if (hash >= 0) remainder.substring(0, hash) else remainder
            val fragment = if (hash >= 0) remainder.substring(hash + 1) else null
            val question = beforeHash.indexOf('?')
            val path = if (question >= 0) beforeHash.substring(0, question) else beforeHash
            val query = if (question >= 0) beforeHash.substring(question + 1) else null
            return UrlParts(scheme, userInfo, host, port, path, query, fragment)
        }

        private fun splitHostPort(hostPort: String): Pair<String, String?>? {
            val host: String
            val afterHost: String
            if (hostPort.startsWith('[')) {
                val close = hostPort.indexOf(']')
                if (close < 0) return null
                val literal = hostPort.substring(1, close)
                if (literal.isEmpty() || literal.any { !(it.isLetterOrDigit() || it == ':' || it == '.') }) return null
                if (literal.any { it.isLetter() && Character.digit(it, HEX_RADIX) < 0 }) return null
                host = hostPort.substring(0, close + 1)
                afterHost = hostPort.substring(close + 1)
            } else {
                val colon = hostPort.lastIndexOf(':')
                host = if (colon >= 0) hostPort.substring(0, colon) else hostPort
                afterHost = if (colon >= 0) hostPort.substring(colon) else ""
                if (!isRegisteredName(host)) return null
            }
            if (afterHost.isEmpty()) return host to null
            if (!afterHost.startsWith(':')) return null
            val port = afterHost.substring(1)
            if (port.length > MAX_PORT_DIGITS || !port.all(Char::isDigit)) return null
            if (port.isNotEmpty() && port.toInt() > MAX_PORT) return null
            return host to port
        }

        private fun isRegisteredName(host: String): Boolean {
            if (host.isEmpty()) return false
            val body = if (host.endsWith('.')) host.dropLast(1) else host
            if (body.isEmpty()) return false
            return body.split('.').all { label -> label.isNotEmpty() && label.all(::isHostChar) }
        }

        private fun isHostChar(c: Char): Boolean =
            c.isLetterOrDigit() || c == '-' || c == '_' || c == '%' || c.code > DEL

        private fun isWhitespaceOrControl(c: Char): Boolean = c.isWhitespace() || c.code < SPACE || c.code == DEL

        private const val MAX_LENGTH = 8192
        private const val MAX_PORT = 65535
        private const val MAX_PORT_DIGITS = 5
        private const val SCHEME_SEPARATOR_LENGTH = 3
        private const val HEX_RADIX = 16
        private const val SPACE = 0x20
        private const val DEL = 0x7F
        private const val FORBIDDEN_AUTHORITY = "\\<>\"{}|^`"
        private val AUTHORITY_TERMINATORS = charArrayOf('/', '?', '#')
    }
}
