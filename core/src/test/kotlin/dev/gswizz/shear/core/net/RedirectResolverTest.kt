/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.net

import dev.gswizz.shear.core.engine.CleanOptions
import dev.gswizz.shear.core.engine.FailureKind
import dev.gswizz.shear.core.engine.RedirectKind
import dev.gswizz.shear.core.engine.RuleSource
import dev.gswizz.shear.core.engine.UrlCleaner
import dev.gswizz.shear.core.psl.PublicSuffixList
import dev.gswizz.shear.core.rules.BraveRules
import dev.gswizz.shear.core.url.PercentCodec
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RedirectResolverTest {
    private val cleaner = UrlCleaner(BraveRules.load(), PublicSuffixList.load(), CleanOptions())
    private val opaque = OpaqueRedirectors.parse("bit.ly\nt.co\n")

    private fun resolver(transport: FakeTransport, policy: ResolverPolicy = ResolverPolicy()) =
        RedirectResolver(cleaner, transport, opaque, policy)

    private val a = "https://a.example/start"
    private val b = "https://b.example/middle"
    private val c = "https://c.example/end"

    @Test
    fun `follows a chain of redirects and records every hop`() = runTest {
        val transport = FakeTransport(mapOf(a to listOf(redirect(301, b)), b to listOf(redirect(302, c))))
        val result = resolver(transport).resolve(a, ResolveMode.RESOLVE_ALL)
        assertEquals(c, result.finalUrl)
        assertNull(result.failure)
        assertEquals(listOf(301, 302), result.redirects.map { it.status })
        assertEquals(listOf(RedirectKind.NETWORK, RedirectKind.NETWORK), result.redirects.map { it.kind })
        assertEquals(listOf("HEAD" to a, "HEAD" to b, "HEAD" to c), transport.requests)
    }

    @Test
    fun `resolves relative locations against the current url`() = runTest {
        val base = "https://a.example/x/y?q=0"
        for ((location, expected) in
            listOf(
                "/next" to "https://a.example/next",
                "../z" to "https://a.example/z",
                "//b.example/p" to "https://b.example/p",
                "?q=1" to "https://a.example/x/y?q=1",
                "https://c.example/abs" to "https://c.example/abs",
            )) {
            val transport = FakeTransport(mapOf(base to listOf(redirect(302, location))))
            assertEquals(expected, resolver(transport).resolve(base, ResolveMode.RESOLVE_ALL).finalUrl, location)
        }
    }

    @Test
    fun `detects loops and stops before revisiting`() = runTest {
        val transport = FakeTransport(mapOf(a to listOf(redirect(302, b)), b to listOf(redirect(302, a))))
        val result = resolver(transport).resolve(a, ResolveMode.RESOLVE_ALL)
        assertEquals(FailureKind.LOOP, result.failure?.kind)
        assertEquals(b, result.finalUrl)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `stops at the hop limit with the limit failure and the last url reached`() = runTest {
        val urls = (0..9).map { "https://h$it.example/" }
        val script = urls.zipWithNext().associate { (from, to) -> from to listOf(redirect(301, to)) }
        val transport = FakeTransport(script)
        val result = resolver(transport, ResolverPolicy(maxHops = 7)).resolve(urls[0], ResolveMode.RESOLVE_ALL)
        assertEquals(FailureKind.HOP_LIMIT, result.failure?.kind)
        assertEquals(urls[7], result.finalUrl)
        assertEquals(7, result.redirects.size)
        assertEquals(8, transport.requests.size)
    }

    @Test
    fun `exactly the hop limit of redirects followed by a page is not a failure`() = runTest {
        val urls = (0..7).map { "https://h$it.example/" }
        val transport = FakeTransport(urls.zipWithNext().associate { (from, to) -> from to listOf(redirect(301, to)) })
        val result = resolver(transport, ResolverPolicy(maxHops = 7)).resolve(urls[0], ResolveMode.RESOLVE_ALL)
        assertNull(result.failure)
        assertEquals(urls[7], result.finalUrl)
    }

    @Test
    fun `rejects locations that are not http or https or cannot be parsed`() = runTest {
        for ((location, kind) in
            listOf(
                "mailto:x@y.example" to FailureKind.NON_HTTP_LOCATION,
                "javascript:alert(1)" to FailureKind.NON_HTTP_LOCATION,
                "http://exa mple.com/" to FailureKind.BAD_LOCATION,
                "http://" to FailureKind.BAD_LOCATION,
            )) {
            val transport = FakeTransport(mapOf(a to listOf(redirect(302, location))))
            val result = resolver(transport).resolve(a, ResolveMode.RESOLVE_ALL)
            assertEquals(kind, result.failure?.kind, location)
            assertEquals(a, result.finalUrl)
        }
    }

    @Test
    fun `a transport failure keeps the last url reached and records why`() = runTest {
        val transport =
            FakeTransport(
                mapOf(
                    a to listOf(redirect(302, b)),
                    b to listOf(TransportException(FailureKind.BLOCKED_ADDRESS, "private address")),
                )
            )
        val result = resolver(transport).resolve(a, ResolveMode.RESOLVE_ALL)
        assertEquals(FailureKind.BLOCKED_ADDRESS, result.failure?.kind)
        assertEquals(b, result.finalUrl)
        assertEquals(b, result.failure?.atUrl)
        assertEquals(1, result.redirects.size)
    }

    @Test
    fun `falls back to GET when HEAD is refused and not otherwise`() = runTest {
        val refused = FakeTransport(mapOf(a to listOf(TransportResponse(405, null), redirect(302, b))))
        assertEquals(b, resolver(refused).resolve(a, ResolveMode.RESOLVE_ALL).finalUrl)
        assertEquals(listOf("HEAD" to a, "GET" to a, "HEAD" to b), refused.requests)
        val notFound = FakeTransport(mapOf(a to listOf(TransportResponse(404, null), TransportResponse(404, null))))
        val result = resolver(notFound).resolve(a, ResolveMode.RESOLVE_ALL)
        assertEquals(a, result.finalUrl)
        assertNull(result.failure)
        assertEquals(listOf("HEAD" to a, "GET" to a), notFound.requests)
        val fine = FakeTransport(mapOf(a to listOf(TransportResponse(200, null))))
        resolver(fine).resolve(a, ResolveMode.RESOLVE_ALL)
        assertEquals(listOf("HEAD" to a), fine.requests)
    }

    @Test
    fun `a redirect status without a location is terminal`() = runTest {
        val transport = FakeTransport(mapOf(a to listOf(TransportResponse(302, null))))
        val result = resolver(transport).resolve(a, ResolveMode.RESOLVE_ALL)
        assertEquals(a, result.finalUrl)
        assertNull(result.failure)
    }

    @Test
    fun `gives up when the total budget runs out`() = runTest {
        val transport =
            FakeTransport(
                mapOf(
                    a to listOf(redirect(302, b)),
                    b to listOf(FakeTransport.Delay(20_000), TransportResponse(200, null)),
                )
            )
        val result = resolver(transport, ResolverPolicy(totalBudget = 10.seconds)).resolve(a, ResolveMode.RESOLVE_ALL)
        assertEquals(FailureKind.TIMEOUT, result.failure?.kind)
        assertEquals(b, result.finalUrl)
    }

    @Test
    fun `cleans every hop before following it so wrappers and trackers never get fetched`() = runTest {
        val dest = "https://dest.example/p?utm_source=x&keep=1"
        val skim = "https://go.skimresources.com/?id=1&url=${PercentCodec.formEncode(dest)}"
        val transport = FakeTransport(mapOf(a to listOf(redirect(302, skim))))
        val result = resolver(transport).resolve(a, ResolveMode.RESOLVE_ALL)
        assertEquals("https://dest.example/p?keep=1", result.finalUrl)
        assertEquals(listOf(RedirectKind.NETWORK, RedirectKind.OFFLINE), result.redirects.map { it.kind })
        assertEquals(listOf("HEAD" to a, "HEAD" to "https://dest.example/p?keep=1"), transport.requests)
        val strip = result.applications.first { it.source == RuleSource.CLEAN_URLS }
        assertEquals(1, strip.hop)
        assertEquals(listOf("utm_source=x"), strip.removedParameters)
    }

    @Test
    fun `smart mode only touches opaque hosts and stops once the chain leaves them`() = runTest {
        val short = "https://bit.ly/abc"
        val transport = FakeTransport(mapOf(short to listOf(redirect(301, c)), c to listOf(redirect(301, b))))
        val result = resolver(transport).resolve(short, ResolveMode.SMART)
        assertEquals(c, result.finalUrl)
        assertEquals(listOf("HEAD" to short), transport.requests)
        val plain = FakeTransport(mapOf(a to listOf(redirect(301, b))))
        assertEquals(a, resolver(plain).resolve(a, ResolveMode.SMART).finalUrl)
        assertTrue(plain.requests.isEmpty())
    }

    @Test
    fun `off mode and invalid input never touch the network`() = runTest {
        val transport = FakeTransport(mapOf(a to listOf(redirect(301, b))))
        assertEquals(a, resolver(transport).resolve(a, ResolveMode.OFF).finalUrl)
        val invalid = resolver(transport).resolve("nope", ResolveMode.RESOLVE_ALL)
        assertEquals(FailureKind.INVALID_URL, invalid.failure?.kind)
        assertTrue(transport.requests.isEmpty())
    }
}
