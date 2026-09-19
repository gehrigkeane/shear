/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.rules

import dev.gswizz.shear.core.url.UrlParts

/**
 * A Chromium extension match pattern restricted to http and https, the pattern language of Brave's rule files.
 *
 * Shape: `<scheme>://<host><glob>` or `<all_urls>`. The scheme is `http`, `https`, or `*` (either). The host is `*`,
 * `*.example.com` (the domain and every subdomain), or an exact name. The glob is matched against the URL's path plus
 * `?query`; `*` is its only wildcard and spans every character, including `/` and `?`.
 */
public class MatchPattern
private constructor(
    public val scheme: Scheme,
    /** Lowercased host with any trailing dot removed; empty when the pattern accepts every host. */
    public val host: String,
    public val matchSubdomains: Boolean,
    public val pathGlob: String,
    public val matchAllUrls: Boolean,
    /** The pattern text exactly as it appeared in the rule file. */
    public val raw: String,
) {
    public enum class Scheme {
        HTTP,
        HTTPS,
        ANY,
    }

    public fun matches(url: UrlParts): Boolean {
        if (matchAllUrls) return true
        if (!schemeMatches(url.scheme)) return false
        val urlHost = url.hostLower
        val hostOk =
            host.isEmpty() ||
                urlHost == host ||
                (matchSubdomains && urlHost.endsWith(".$host") && urlHost.length > host.length)
        if (!hostOk) return false
        return pathMatches(url.pathForRequest)
    }

    private fun schemeMatches(urlScheme: String): Boolean =
        when (scheme) {
            Scheme.ANY -> true
            Scheme.HTTP -> urlScheme.equals("http", ignoreCase = true)
            Scheme.HTTPS -> urlScheme.equals("https", ignoreCase = true)
        }

    private fun pathMatches(target: String): Boolean {
        if (glob(target, pathGlob)) return true
        // Chromium quirk: a glob ending in "/*" also matches the path without that suffix.
        return pathGlob.length > 2 && pathGlob.endsWith("/*") && target == pathGlob.substring(0, pathGlob.length - 2)
    }

    override fun toString(): String = raw

    public companion object {
        private const val ALL_URLS = "<all_urls>"
        private val shape = Regex("""^(\*|https?)://([^/:]*)(/.*)$""")

        /** Parses [pattern], or returns null when it is not a valid http(s) match pattern. */
        public fun parse(pattern: String): MatchPattern? {
            if (pattern == ALL_URLS) return MatchPattern(Scheme.ANY, "", true, "/*", matchAllUrls = true, raw = pattern)
            val m = shape.find(pattern) ?: return null
            val scheme =
                when (m.groupValues[1]) {
                    "*" -> Scheme.ANY
                    "http" -> Scheme.HTTP
                    else -> Scheme.HTTPS
                }
            val rawHost = m.groupValues[2]
            val glob = m.groupValues[3]
            val (host, subdomains) =
                when {
                    rawHost == "*" -> "" to true
                    rawHost.startsWith("*.") -> rawHost.substring(2) to true
                    else -> rawHost to false
                }
            if (host.isEmpty() && rawHost != "*") return null
            if (host.contains('*')) return null
            return MatchPattern(
                scheme,
                host.lowercase().removeSuffix("."),
                subdomains,
                glob,
                matchAllUrls = false,
                raw = pattern,
            )
        }

        /** Iterative glob match where `*` is the only wildcard; case-sensitive. */
        private fun glob(text: String, pattern: String): Boolean {
            var t = 0
            var p = 0
            var starP = -1
            var starT = -1
            while (t < text.length) {
                when {
                    p < pattern.length && pattern[p] == '*' -> {
                        starP = p++
                        starT = t
                    }
                    p < pattern.length && pattern[p] == text[t] -> {
                        p++
                        t++
                    }
                    starP >= 0 -> {
                        p = starP + 1
                        t = ++starT
                    }
                    else -> return false
                }
            }
            while (p < pattern.length && pattern[p] == '*') p++
            return p == pattern.length
        }
    }
}
