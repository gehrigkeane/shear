/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.engine

import dev.gswizz.shear.core.psl.PublicSuffixList
import dev.gswizz.shear.core.rules.DebounceAction
import dev.gswizz.shear.core.rules.DebounceRule
import dev.gswizz.shear.core.url.PercentCodec
import dev.gswizz.shear.core.url.UrlParts
import dev.gswizz.shear.core.url.UrlReference
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * The `debounce.json` stage, a port of Brave's `DebounceRule::Apply` driven the way `DebounceService::Debounce` drives
 * it: rules run top to bottom and the first one that produces a different URL wins.
 *
 * A rule applies when its patterns match and its preference gate is open. The destination is read from a query
 * parameter (optionally base64url-encoded) or captured from the path with a regex, optionally given a scheme, and then
 * has to survive Brave's failsafes: it must parse as an http(s) URL, sit on a different site than the wrapper, have a
 * host that a naive parser agrees on, and own a registrable domain.
 */
internal class Debouncer(
    private val rules: List<DebounceRule>,
    private val psl: PublicSuffixList,
    private val deAmpEnabled: Boolean,
) {
    fun apply(url: UrlParts, sink: TraceSink): UrlParts {
        for (rule in rules) {
            val target = applyRule(rule, url) ?: continue
            if (target == url) continue
            sink.application(RuleSource.DEBOUNCE, rule.index, url, target, action = rule.action.wireName)
            sink.redirect(url, target, RedirectKind.OFFLINE, ruleIndex = rule.index)
            return target
        }
        return url
    }

    private fun applyRule(rule: DebounceRule, url: UrlParts): UrlParts? {
        if (rule.requiresDeAmp && !deAmpEnabled) return null
        if (rule.patterns.matches(url) == null) return null
        val extracted =
            when (rule.action) {
                DebounceAction.REDIRECT -> queryValue(url, rule.param)
                DebounceAction.BASE64_REDIRECT -> queryValue(url, rule.param)?.let(::base64UrlDecode)
                DebounceAction.REGEX_PATH -> capturePath(rule, url)
                DebounceAction.REGEX_PATH_TEMPLATE -> captureTemplate(rule, url)
            }
        if (extracted.isNullOrEmpty()) return null
        var spec = extracted
        if (rule.prependScheme != null) {
            if (UrlReference.schemeOf(spec) != null) return null
            spec = "${rule.prependScheme}://$spec"
        }
        val candidate = UrlParts.parse(PercentCodec.escapeIllegal(spec)) ?: return null
        if (psl.sameSite(url, candidate)) return null
        if (naiveHost(spec) != candidate.hostLower) return null
        if (psl.registrableDomain(candidate.host) == null) return null
        return candidate
    }

    /** `net::GetValueForKeyInQuery`: the first token whose raw key equals [key], form-decoded. */
    private fun queryValue(url: UrlParts, key: String): String? {
        val query = url.query ?: return null
        for (token in query.split('&')) {
            val eq = token.indexOf('=')
            val tokenKey = if (eq < 0) token else token.substring(0, eq)
            if (tokenKey != key) continue
            val raw = if (eq < 0) "" else token.substring(eq + 1)
            return PercentCodec.unescapeChromium(raw, plusToSpace = true)
        }
        return null
    }

    private fun capturePath(rule: DebounceRule, url: UrlParts): String? {
        val match = rule.regex?.find(url.path) ?: return null
        val joined = buildString { for (i in 1..rule.groupCount) match.groups[i]?.value?.let(::append) }
        if (joined.isEmpty()) return null
        return PercentCodec.unescapeChromium(joined, plusToSpace = true)
    }

    private fun captureTemplate(rule: DebounceRule, url: UrlParts): String? {
        val match = rule.regex?.find(url.path) ?: return null
        val template = rule.template ?: return null
        return placeholder.replace(template) { m -> match.groups[m.groupValues[1].toInt()]?.value ?: "" }
    }

    private fun base64UrlDecode(value: String): String? {
        val bytes = runCatching { Base64.getUrlDecoder().decode(value) }.getOrNull() ?: return null
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }

    /** Brave's `NaivelyExtractHostnameFromUrl`: strip the scheme, cut at the first `:`, `/`, or `?`. */
    private fun naiveHost(spec: String): String {
        val withoutScheme =
            when {
                spec.startsWith("https://", ignoreCase = true) -> spec.substring("https://".length)
                spec.startsWith("http://", ignoreCase = true) -> spec.substring("http://".length)
                else -> spec
            }
        val end = withoutScheme.indexOfAny(charArrayOf(':', '/', '?')).let { if (it < 0) withoutScheme.length else it }
        return withoutScheme.substring(0, end).lowercase()
    }

    private companion object {
        val placeholder = Regex("""\$([1-9])""")
    }
}
