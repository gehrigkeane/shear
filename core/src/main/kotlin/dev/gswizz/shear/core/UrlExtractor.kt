/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core

/**
 * Locates HTTP(S) URLs inside free text without altering the text around them.
 *
 * Extraction is deliberately conservative. A URL starts at `http://` or `https://` and runs to the next whitespace or
 * quote, then sheds punctuation that prose attaches to links: sentence marks, and closing brackets the URL did not open
 * itself. Nothing else is interpreted, so the cleaning stage sees exactly what the user shared.
 */
public object UrlExtractor {
    private val candidate = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private const val TRAILING_PUNCTUATION = ".,;:!?'\"]}>"

    /** A URL found in the shared text together with the range of characters it occupies. */
    public data class Match(val url: String, val range: IntRange)

    /** Returns every HTTP(S) URL in [text] in order of appearance. */
    public fun extract(text: String): List<Match> =
        candidate
            .findAll(text)
            .map { found ->
                val start = found.range.first
                var end = found.range.last + 1
                while (end > start) {
                    val last = text[end - 1]
                    when {
                        last == ')' && hasUnbalancedClose(text, start, end) -> end--
                        last != ')' && last in TRAILING_PUNCTUATION -> end--
                        else -> break
                    }
                }
                Match(text.substring(start, end), start until end)
            }
            .toList()

    private fun hasUnbalancedClose(text: String, start: Int, end: Int): Boolean {
        var depth = 0
        for (i in start until end) {
            when (text[i]) {
                '(' -> depth++
                ')' -> depth--
            }
        }
        return depth < 0
    }
}
