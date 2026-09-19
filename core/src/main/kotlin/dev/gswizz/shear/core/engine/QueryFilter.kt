/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.engine

import dev.gswizz.shear.core.rules.ParamRule
import dev.gswizz.shear.core.url.UrlParts

/**
 * The query-string filter stage, a port of Brave's `query_filter::ApplyQueryFilter`.
 *
 * The blocked set is the union of parameters from every `query-filter.json` rule whose patterns match. Each token is
 * split once on `=`; tokens without a key, without a value, or without `=` are kept. A blocked key is removed; a
 * conditional tracker is removed unless its pattern matches somewhere in the full URL. `urldefense.com` is exempt by
 * exact host, as in Brave. Every shared URL is treated as a cross-site navigation.
 */
internal class QueryFilter(private val rules: List<ParamRule>, private val conditional: Map<String, Regex>) {
    fun apply(url: UrlParts, sink: TraceSink): UrlParts {
        if (url.hostLower == EXEMPT_HOST) return url
        val query = url.query
        if (query.isNullOrEmpty()) return url
        val blocked = HashMap<String, Int>()
        for (rule in rules) {
            if (rule.patterns.matches(url) == null) continue
            for (param in rule.params) blocked.putIfAbsent(param, rule.index)
        }
        val spec = url.toUrlString()
        val kept = mutableListOf<String>()
        val removedByRule = LinkedHashMap<Int, MutableList<String>>()
        for (token in query.split('&')) {
            val eq = token.indexOf('=')
            if (eq < 0) {
                kept += token
                continue
            }
            val key = token.substring(0, eq)
            val value = token.substring(eq + 1)
            val owner =
                when {
                    key.isEmpty() || value.isEmpty() -> null
                    key in blocked -> blocked.getValue(key)
                    conditional[key]?.containsMatchIn(spec) == false -> CONDITIONAL_RULE_INDEX
                    else -> null
                }
            if (owner == null) kept += token else removedByRule.getOrPut(owner) { mutableListOf() } += token
        }
        if (removedByRule.isEmpty()) return url
        val next = url.withQuery(kept.joinToString("&").ifEmpty { null })
        for ((index, removed) in removedByRule) sink.application(RuleSource.QUERY_FILTER, index, url, next, removed)
        return next
    }

    private companion object {
        const val EXEMPT_HOST = "urldefense.com"
        const val CONDITIONAL_RULE_INDEX = -1
    }
}
