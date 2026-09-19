/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.engine

import dev.gswizz.shear.core.rules.ParamRule
import dev.gswizz.shear.core.url.UrlParts

/**
 * The `clean-urls.json` stage, a port of Brave's `URLSanitizerService::SanitizeURL`.
 *
 * Matchers run in file order against the current URL, so later rules see earlier removals. A query token is removed
 * only when it has a non-empty value and its key is listed by the rule, compared exactly and case-sensitively.
 * Surviving tokens keep their text, order, and position; when a removal empties the query the `?` goes with it.
 */
internal class QuerySanitizer(private val rules: List<ParamRule>) {
    fun apply(url: UrlParts, sink: TraceSink): UrlParts {
        var current = url
        for (rule in rules) {
            val query = current.query ?: continue
            if (rule.patterns.matches(current) == null) continue
            val removed = mutableListOf<String>()
            val kept = mutableListOf<String>()
            for (token in query.split('&')) {
                val pieces = token.split('=').filter { it.isNotEmpty() }
                if (pieces.size >= 2 && pieces[0] in rule.params) removed += token else kept += token
            }
            if (removed.isEmpty()) continue
            val next = current.withQuery(kept.joinToString("&").ifEmpty { null })
            sink.application(RuleSource.CLEAN_URLS, rule.index, current, next, removed)
            current = next
        }
        return current
    }
}
