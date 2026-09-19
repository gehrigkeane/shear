/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import dev.gswizz.shear.core.TextResult
import dev.gswizz.shear.core.engine.UrlCleanResult

/** A run of the original URL that either survives the cut or falls away. */
data class Segment(val text: String, val removed: Boolean)

/**
 * How The Cut should slice one URL: the original in order, each run marked kept or removed, such that the kept runs
 * concatenate to [final].
 *
 * Removed query parameters take a separator with them so the survivors still read as a URL: a leading parameter takes
 * the `&` after it, any other takes the one before it, and the `?` goes when nothing is left behind it. When the
 * cleaning did more than drop parameters (a debounce or redirect changed the host or path) the kept runs cannot equal
 * the final URL, so the plan is [wholesale]: the whole original falls and [final] is typed in afterwards.
 */
data class CutPlan(val original: String, val final: String, val segments: List<Segment>, val wholesale: Boolean) {
    companion object {
        /** The plan for the first URL that changed, or null when nothing did. */
        fun of(result: TextResult): CutPlan? = result.urls.firstOrNull { it.changed }?.let(::of)

        fun of(url: UrlCleanResult): CutPlan {
            val segments = sliceByParameters(url)
            val kept = segments?.filter { !it.removed }?.joinToString("") { it.text }
            return if (segments != null && kept == url.finalUrl) {
                CutPlan(url.originalUrl, url.finalUrl, segments, wholesale = false)
            } else {
                CutPlan(
                    url.originalUrl,
                    url.finalUrl,
                    listOf(Segment(url.originalUrl, removed = true)),
                    wholesale = true,
                )
            }
        }

        /** Marks removed parameters and their separators, or null when a removed token is not a parameter here. */
        private fun sliceByParameters(url: UrlCleanResult): List<Segment>? {
            val original = url.originalUrl
            val queryStart = original.indexOf('?')
            if (queryStart < 0) return null
            val fragmentStart = original.indexOf('#', queryStart).let { if (it < 0) original.length else it }
            val query = original.substring(queryStart + 1, fragmentStart)
            val removedTokens = url.removedParameters.map { it.token }.toSet()
            val params = query.split('&')
            if (removedTokens.any { it !in params }) return null
            val kept = params.map { it !in removedTokens }
            val anyKept = kept.any { it }
            val pieces = mutableListOf<Segment>()
            pieces += Segment(original.substring(0, queryStart), removed = false)
            pieces += Segment("?", removed = !anyKept)
            var keptBefore = false
            for ((i, param) in params.withIndex()) {
                if (i > 0) pieces += Segment("&", removed = !(kept[i] && keptBefore))
                pieces += Segment(param, removed = !kept[i])
                if (kept[i]) keptBefore = true
            }
            if (fragmentStart < original.length) pieces += Segment(original.substring(fragmentStart), removed = false)
            return coalesce(pieces)
        }

        private fun coalesce(pieces: List<Segment>): List<Segment> =
            pieces
                .filter { it.text.isNotEmpty() }
                .fold(mutableListOf()) { acc, piece ->
                    val last = acc.lastOrNull()
                    if (last != null && last.removed == piece.removed)
                        acc[acc.lastIndex] = last.copy(text = last.text + piece.text)
                    else acc += piece
                    acc
                }
    }
}
