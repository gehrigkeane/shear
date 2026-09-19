/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.engine

import dev.gswizz.shear.core.psl.PublicSuffixList
import dev.gswizz.shear.core.rules.BraveRules
import dev.gswizz.shear.core.url.UrlParts

/** Knobs for the offline pipeline. */
public data class CleanOptions(
    /** How many debounce → filter → sanitize passes may change the URL before cleaning gives up. */
    val maxOfflineIterations: Int = DEFAULT_MAX_ITERATIONS,
    /** Whether debounce rules gated on Brave's De-AMP preference apply. */
    val deAmpEnabled: Boolean = true,
) {
    private companion object {
        const val DEFAULT_MAX_ITERATIONS = 5
    }
}

/**
 * The offline cleaning pipeline: Brave's strict clean (debounce, query filter, URL sanitizer) repeated until the URL
 * stops changing.
 *
 * Reaching a fixed point is what makes cleaning idempotent: a URL that is already clean passes through with an empty
 * trace. Unparsable input is returned untouched with an [FailureKind.INVALID_URL] failure, and a wrapper chain deeper
 * than [CleanOptions.maxOfflineIterations] stops with [FailureKind.OFFLINE_DEPTH_EXCEEDED] and the last URL reached.
 */
public class UrlCleaner(
    public val rules: BraveRules,
    psl: PublicSuffixList,
    public val options: CleanOptions = CleanOptions(),
) {
    private val debouncer = Debouncer(rules.debounce, psl, options.deAmpEnabled)
    private val filter = QueryFilter(rules.queryFilter, rules.conditionalTrackers)
    private val sanitizer = QuerySanitizer(rules.cleanUrls)

    /** Cleans one URL without touching the network. */
    public fun clean(url: String): UrlCleanResult {
        val sink = TraceSink(rules.version)
        val parts =
            UrlParts.parse(url) ?: return sink.result(url, url, failure = CleanFailure(FailureKind.INVALID_URL, url))
        return sink.result(url, cleanParts(parts, sink).toUrlString())
    }

    /** Runs the pipeline to a fixed point, recording into [sink]; shared with the network resolver per hop. */
    internal fun cleanParts(start: UrlParts, sink: TraceSink): UrlParts {
        var current = start
        for (iteration in 0 until options.maxOfflineIterations) {
            sink.iteration = iteration
            val next = sanitizer.apply(filter.apply(debouncer.apply(current, sink), sink), sink)
            if (UrlParts.parse(next.toUrlString()) == null) {
                sink.fail(FailureKind.INVALID_URL, next, "stage produced an unparsable url")
                return current
            }
            if (next == current) return current
            current = next
        }
        sink.fail(FailureKind.OFFLINE_DEPTH_EXCEEDED, current)
        return current
    }
}
