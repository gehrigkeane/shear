/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core

import dev.gswizz.shear.core.engine.UrlCleanResult
import dev.gswizz.shear.core.engine.UrlCleaner
import dev.gswizz.shear.core.net.OpaqueRedirectors
import dev.gswizz.shear.core.net.RedirectResolver
import dev.gswizz.shear.core.net.RedirectTransport
import dev.gswizz.shear.core.net.ResolveMode
import dev.gswizz.shear.core.psl.PublicSuffixList
import dev.gswizz.shear.core.rules.BraveRules
import dev.gswizz.shear.core.url.UrlParts

/** Shared text after cleaning, with one result per URL in order of appearance. */
public data class TextResult(val originalText: String, val outputText: String, val urls: List<UrlCleanResult>) {
    public val changed: Boolean
        get() = originalText != outputText
}

/**
 * The text-level entry point: finds every URL in shared text, cleans each, and splices the results back in place.
 *
 * Everything outside the URLs is untouched, and a URL that cleaning could not change (including one that failed) is
 * left exactly as the user shared it.
 */
public class Shear(
    private val cleaner: UrlCleaner,
    private val resolver: RedirectResolver,
    private val opaque: OpaqueRedirectors,
) {
    /** The upstream commit the bundled rules were taken from. */
    public val rulesVersion: String
        get() = cleaner.rules.version

    /** False when any rule file was missing or any rule was dropped; the app then forwards text unchanged. */
    public val rulesHealthy: Boolean
        get() = cleaner.rules.isHealthy

    /** Cleans every URL in [text] offline. */
    public fun clean(text: String): TextResult = splice(text) { cleaner.clean(it) }

    /** Cleans every URL in [text], following redirects over the network as [mode] allows. */
    public suspend fun process(text: String, mode: ResolveMode): TextResult =
        splice(text) { resolver.resolve(it, mode) }

    /** True when [process] with [mode] would contact the network for at least one URL in [text]. */
    public fun needsNetwork(text: String, mode: ResolveMode): Boolean {
        if (mode == ResolveMode.OFF) return false
        return UrlExtractor.extract(text).any { match ->
            val cleaned = UrlParts.parse(cleaner.clean(match.url).finalUrl) ?: return@any false
            mode == ResolveMode.RESOLVE_ALL || opaque.contains(cleaned.hostLower)
        }
    }

    private inline fun splice(text: String, perUrl: (String) -> UrlCleanResult): TextResult {
        val matches = UrlExtractor.extract(text)
        val results = matches.map { perUrl(it.url) }
        val output = StringBuilder(text)
        for (i in matches.indices.reversed()) {
            val result = results[i]
            if (result.finalUrl != result.originalUrl) {
                output.replace(matches[i].range.first, matches[i].range.last + 1, result.finalUrl)
            }
        }
        return TextResult(text, output.toString(), results)
    }

    public companion object {
        /**
         * A [Shear] over the bundled rules, public suffix list, and opaque-redirector list, fetching via [transport].
         */
        public fun default(transport: RedirectTransport): Shear {
            val cleaner = UrlCleaner(BraveRules.load(), PublicSuffixList.load())
            val opaque = OpaqueRedirectors.load()
            return Shear(cleaner, RedirectResolver(cleaner, transport, opaque), opaque)
        }
    }
}
