/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.psl

import dev.gswizz.shear.core.url.HostNames
import dev.gswizz.shear.core.url.UrlParts
import java.net.IDN

/**
 * Mozilla's Public Suffix List, used to decide what "same site" means when a debounce rule wants to redirect.
 *
 * Debouncing follows Brave and consults the ICANN section only; [includePrivate] exists for callers that want the
 * complete list, such as the publicsuffix.org conformance tests. Lookups are case-insensitive and accept one trailing
 * dot. IP literals and hosts with empty labels have no public suffix.
 */
public class PublicSuffixList internal constructor(private val icann: Rules, private val all: Rules) {
    internal class Rules(val exact: Set<String>, val wildcard: Set<String>, val exception: Set<String>)

    /** The public suffix of [host], or null when [host] is an IP literal, empty, or malformed. */
    public fun publicSuffix(host: String, includePrivate: Boolean = false): String? {
        val labels = labelsOf(host) ?: return null
        return suffixLabels(labels, if (includePrivate) all else icann).joinToString(".")
    }

    /** The registrable domain (eTLD+1) of [host], lowercased, or null when [host] is itself a suffix or not a name. */
    public fun registrableDomain(host: String, includePrivate: Boolean = false): String? {
        val labels = labelsOf(host) ?: return null
        val suffix = suffixLabels(labels, if (includePrivate) all else icann)
        if (labels.size <= suffix.size) return null
        return labels.takeLast(suffix.size + 1).joinToString(".")
    }

    /**
     * Chromium's `SameDomainOrHost`: equal registrable domains, or equal hosts when either has no registrable domain.
     */
    public fun sameSite(a: UrlParts, b: UrlParts): Boolean {
        val ra = registrableDomain(a.hostLower)
        val rb = registrableDomain(b.hostLower)
        return if (ra != null && rb != null) ra == rb else a.hostLower == b.hostLower
    }

    private fun labelsOf(host: String): List<String>? {
        if (host.isEmpty() || HostNames.isIpLiteral(host)) return null
        val labels = HostNames.normalizeForMatching(host).split('.')
        return labels.takeIf { it.none(String::isEmpty) }
    }

    private fun suffixLabels(labels: List<String>, rules: Rules): List<String> {
        var best = 0
        for (i in labels.indices) {
            val candidate = labels.subList(i, labels.size).joinToString(".")
            if (candidate in rules.exception) return labels.subList(i + 1, labels.size)
            if (candidate in rules.exact) best = maxOf(best, labels.size - i)
            if (i + 1 < labels.size && labels.subList(i + 1, labels.size).joinToString(".") in rules.wildcard) {
                best = maxOf(best, labels.size - i)
            }
        }
        return labels.takeLast(if (best == 0) 1 else best)
    }

    public companion object {
        private const val RESOURCE = "/mozilla/public_suffix_list.dat"
        private const val BEGIN_PRIVATE = "// ===BEGIN PRIVATE DOMAINS==="

        /** Loads the vendored list from the classpath. */
        public fun load(): PublicSuffixList {
            val text =
                requireNotNull(PublicSuffixList::class.java.getResourceAsStream(RESOURCE)) { "missing $RESOURCE" }
            return parse(text.bufferedReader().readText())
        }

        /** Parses list text in the publicsuffix.org format. */
        public fun parse(dat: String): PublicSuffixList {
            val icannExact = HashSet<String>()
            val icannWildcard = HashSet<String>()
            val icannException = HashSet<String>()
            val allExact = HashSet<String>()
            val allWildcard = HashSet<String>()
            val allException = HashSet<String>()
            var private = false
            for (raw in dat.lineSequence()) {
                val line = raw.trim()
                if (line.startsWith(BEGIN_PRIVATE)) private = true
                if (line.isEmpty() || line.startsWith("//")) continue
                val rule = line.substringBefore(' ').lowercase()
                val (target, key) =
                    when {
                        rule.startsWith("!") -> allException to rule.substring(1)
                        rule.startsWith("*.") -> allWildcard to rule.substring(2)
                        else -> allExact to rule
                    }
                for (variant in variants(key)) {
                    target += variant
                    if (!private) {
                        when (target) {
                            allException -> icannException += variant
                            allWildcard -> icannWildcard += variant
                            else -> icannExact += variant
                        }
                    }
                }
            }
            return PublicSuffixList(
                Rules(icannExact, icannWildcard, icannException),
                Rules(allExact, allWildcard, allException),
            )
        }

        /** A rule and, for Unicode rules, its punycode form so hosts written either way match. */
        private fun variants(rule: String): List<String> {
            if (rule.all { it.code < HIGH_BIT }) return listOf(rule)
            val ascii = runCatching { IDN.toASCII(rule, IDN.ALLOW_UNASSIGNED) }.getOrNull()
            return if (ascii == null || ascii == rule) listOf(rule) else listOf(rule, ascii)
        }

        private const val HIGH_BIT = 0x80
    }
}
