/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.engine

import dev.gswizz.shear.core.url.UrlParts
import kotlinx.serialization.Serializable

/** Which Brave list a rule came from. */
@Serializable
public enum class RuleSource {
    DEBOUNCE,
    QUERY_FILTER,
    CLEAN_URLS,
}

/** Whether a redirect was unwrapped from the URL itself or observed from a server response. */
@Serializable
public enum class RedirectKind {
    OFFLINE,
    NETWORK,
}

/** Everything that can stop cleaning short of a fixed point; the result still carries the last good URL. */
@Serializable
public enum class FailureKind {
    INVALID_URL,
    OFFLINE_DEPTH_EXCEEDED,
    HOP_LIMIT,
    LOOP,
    NON_HTTP_LOCATION,
    BAD_LOCATION,
    BLOCKED_ADDRESS,
    TIMEOUT,
    TLS,
    IO,
    BAD_STATUS,
}

/**
 * One rule doing one thing to one URL.
 *
 * [ruleIndex] is the position in the source file; `-1` marks the conditional trackers, which Brave keeps in code rather
 * than in a list. [hop] counts network redirects followed so far and [iteration] the offline pass within that hop, so a
 * trace reads in the order the transformations happened.
 */
@Serializable
public data class RuleApplication(
    val source: RuleSource,
    val ruleIndex: Int,
    val hop: Int,
    val iteration: Int,
    val inputUrl: String,
    val outputUrl: String,
    /** Raw `key=value` tokens removed, in their original order. */
    val removedParameters: List<String> = emptyList(),
    /** Debounce action name when the rule was a debounce rule. */
    val action: String? = null,
)

/** A stripped query parameter and the rule that removed it. */
@Serializable
public data class RemovedParameter(val name: String, val token: String, val source: RuleSource, val ruleIndex: Int)

/** One step in the redirect chain, offline or observed. */
@Serializable
public data class RedirectStep(
    val fromUrl: String,
    val toUrl: String,
    val kind: RedirectKind,
    val status: Int? = null,
    val ruleIndex: Int? = null,
)

/** Why cleaning stopped early, and where. */
@Serializable public data class CleanFailure(val kind: FailureKind, val atUrl: String, val detail: String? = null)

/** The complete account of what happened to one URL. */
@Serializable
public data class UrlCleanResult(
    val originalUrl: String,
    val finalUrl: String,
    val applications: List<RuleApplication>,
    val removedParameters: List<RemovedParameter>,
    val redirects: List<RedirectStep>,
    val failure: CleanFailure?,
    val rulesVersion: String,
) {
    public val changed: Boolean
        get() = originalUrl != finalUrl
}

/** Mutable collector the stages write into while a URL is being processed; the first failure recorded wins. */
internal class TraceSink(private val rulesVersion: String) {
    val applications = mutableListOf<RuleApplication>()
    val removedParameters = mutableListOf<RemovedParameter>()
    val redirects = mutableListOf<RedirectStep>()
    var failure: CleanFailure? = null
        private set

    var hop: Int = 0
    var iteration: Int = 0

    fun application(
        source: RuleSource,
        ruleIndex: Int,
        input: UrlParts,
        output: UrlParts,
        removed: List<String> = emptyList(),
        action: String? = null,
    ) {
        applications +=
            RuleApplication(
                source,
                ruleIndex,
                hop,
                iteration,
                input.toUrlString(),
                output.toUrlString(),
                removed,
                action,
            )
        for (token in removed) removedParameters +=
            RemovedParameter(token.substringBefore('='), token, source, ruleIndex)
    }

    fun redirect(from: UrlParts, to: UrlParts, kind: RedirectKind, status: Int? = null, ruleIndex: Int? = null) {
        redirects += RedirectStep(from.toUrlString(), to.toUrlString(), kind, status, ruleIndex)
    }

    fun fail(kind: FailureKind, at: UrlParts, detail: String? = null) {
        if (failure == null) failure = CleanFailure(kind, at.toUrlString(), detail)
    }

    fun result(originalUrl: String, finalUrl: String, failure: CleanFailure? = this.failure): UrlCleanResult =
        UrlCleanResult(
            originalUrl,
            finalUrl,
            applications.toList(),
            removedParameters.toList(),
            redirects.toList(),
            failure,
            rulesVersion,
        )
}
