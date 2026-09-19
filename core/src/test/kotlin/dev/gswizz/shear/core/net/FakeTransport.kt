/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.net

import dev.gswizz.shear.core.url.UrlParts
import kotlinx.coroutines.delay

/** A scripted [RedirectTransport]: each URL has a queue of steps consumed in order across HEAD and GET. */
class FakeTransport(script: Map<String, List<Any>> = emptyMap()) : RedirectTransport {
    /** A step that waits virtual time before the next step for the same URL. */
    data class Delay(val millis: Long)

    private val queues = script.mapValues { ArrayDeque(it.value) }.toMutableMap()
    val requests = mutableListOf<Pair<String, String>>()

    override suspend fun head(url: UrlParts): TransportResponse = step("HEAD", url)

    override suspend fun get(url: UrlParts): TransportResponse = step("GET", url)

    private suspend fun step(method: String, url: UrlParts): TransportResponse {
        requests += method to url.toUrlString()
        val queue = queues[url.toUrlString()] ?: return TransportResponse(200, null)
        while (true) {
            return when (val next = queue.removeFirstOrNull() ?: return TransportResponse(200, null)) {
                is Delay -> {
                    delay(next.millis)
                    continue
                }
                is Throwable -> throw next
                is TransportResponse -> next
                else -> error("unsupported step $next")
            }
        }
    }
}

fun redirect(status: Int, location: String) = TransportResponse(status, location)
