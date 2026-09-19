/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.net

import dev.gswizz.shear.core.engine.FailureKind
import dev.gswizz.shear.core.engine.RedirectKind
import dev.gswizz.shear.core.engine.TraceSink
import dev.gswizz.shear.core.engine.UrlCleanResult
import dev.gswizz.shear.core.engine.UrlCleaner
import dev.gswizz.shear.core.url.PercentCodec
import dev.gswizz.shear.core.url.UrlParts
import dev.gswizz.shear.core.url.UrlReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull

/** How much of the network Shear may use for a URL. */
public enum class ResolveMode {
    /** Offline cleaning only. */
    OFF,
    /** Fetch only hosts in the opaque-redirector list. */
    SMART,
    /** Fetch every URL. */
    RESOLVE_ALL,
}

/** Limits on one resolution. */
public data class ResolverPolicy(val maxHops: Int = DEFAULT_MAX_HOPS, val totalBudget: Duration = DEFAULT_BUDGET) {
    private companion object {
        const val DEFAULT_MAX_HOPS = 7
        val DEFAULT_BUDGET = 10.seconds
    }
}

/**
 * Follows server-side redirects one hop at a time, cleaning each hop before it is fetched.
 *
 * HEAD is tried first and GET only when the server refuses HEAD. Redirect targets are resolved against the current URL,
 * must be http(s), and must not revisit a URL already seen. Any failure leaves the result at the last cleaned URL that
 * was reached, with the reason recorded, so sharing always has something valid to fall back on.
 */
public class RedirectResolver(
    private val cleaner: UrlCleaner,
    private val transport: RedirectTransport,
    private val opaque: OpaqueRedirectors,
    private val policy: ResolverPolicy = ResolverPolicy(),
) {
    public suspend fun resolve(url: String, mode: ResolveMode): UrlCleanResult {
        val sink = TraceSink(cleaner.rules.version)
        val start = UrlParts.parse(url) ?: return cleaner.clean(url)
        var current = cleaner.cleanParts(start, sink)
        if (mode == ResolveMode.OFF) return sink.result(url, current.toUrlString())
        val visited = mutableSetOf(current.toUrlString())
        val finished =
            withTimeoutOrNull(policy.totalBudget) {
                var hop = 0
                while (true) {
                    if (mode == ResolveMode.SMART && !opaque.contains(current.hostLower)) break
                    val response =
                        try {
                            redirectOf(current)
                        } catch (e: TransportException) {
                            sink.fail(e.kind, current, e.message)
                            break
                        } ?: break
                    if (hop == policy.maxHops) {
                        sink.fail(FailureKind.HOP_LIMIT, current, "still redirecting after ${policy.maxHops} hops")
                        break
                    }
                    val location = response.location ?: break
                    val target = UrlReference.resolve(current, PercentCodec.escapeIllegal(location))
                    if (target == null) {
                        val scheme = UrlReference.schemeOf(location.trim())
                        val kind =
                            if (scheme != null && !scheme.equals("http", true) && !scheme.equals("https", true))
                                FailureKind.NON_HTTP_LOCATION
                            else FailureKind.BAD_LOCATION
                        sink.fail(kind, current, location)
                        break
                    }
                    hop++
                    sink.hop = hop
                    sink.redirect(current, target, RedirectKind.NETWORK, status = response.status)
                    val cleaned = cleaner.cleanParts(target, sink)
                    if (!visited.add(cleaned.toUrlString())) {
                        sink.fail(FailureKind.LOOP, current, cleaned.toUrlString())
                        break
                    }
                    current = cleaned
                }
            }
        if (finished == null) sink.fail(FailureKind.TIMEOUT, current, "exceeded ${policy.totalBudget}")
        return sink.result(url, current.toUrlString())
    }

    /** The redirect [url] answers with, or null when it serves a page or an error instead. */
    private suspend fun redirectOf(url: UrlParts): TransportResponse? {
        val head = transport.head(url)
        if (head.isRedirect()) return head
        if (head.status < HTTP_CLIENT_ERROR) return null
        val get = transport.get(url)
        return if (get.isRedirect()) get else null
    }

    private fun TransportResponse.isRedirect(): Boolean = status in REDIRECT_STATUSES && location != null

    private companion object {
        val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
        const val HTTP_CLIENT_ERROR = 400
    }
}
