/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.net

import dev.gswizz.shear.core.engine.FailureKind
import dev.gswizz.shear.core.url.UrlParts

/** What the resolver needs from a response: the status and the raw `Location` header, if any. */
public data class TransportResponse(val status: Int, val location: String?)

/** A request that could not complete, classified into the failure the trace records. */
public class TransportException(public val kind: FailureKind, message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * The single seam between the resolver and the network.
 *
 * Implementations must not follow redirects, send cookies, credentials, or a referrer, or read response bodies, and
 * must refuse connections to non-public addresses by throwing a [TransportException] of kind
 * [FailureKind.BLOCKED_ADDRESS].
 */
public interface RedirectTransport {
    public suspend fun head(url: UrlParts): TransportResponse

    public suspend fun get(url: UrlParts): TransportResponse
}
