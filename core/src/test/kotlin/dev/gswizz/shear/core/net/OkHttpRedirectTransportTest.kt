/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.net

import dev.gswizz.shear.core.engine.FailureKind
import dev.gswizz.shear.core.url.UrlParts
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Real sockets need real time: `runTest` would fast-forward the resolver's budget while I/O is in flight. */
class OkHttpRedirectTransportTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stop() {
        server.close()
    }

    private fun url(path: String) = UrlParts.parse(server.url(path).toString())!!

    /** Local servers live on loopback, which the default policy rightly blocks, so tests opt out of the guard. */
    private fun transport(
        policy: AddressPolicy = AddressPolicy.AllowAll,
        readTimeout: kotlin.time.Duration = 5.seconds,
    ) =
        OkHttpRedirectTransport(
            OkHttpRedirectTransport.defaultClient(policy, connectTimeout = 2.seconds, readTimeout = readTimeout)
        )

    @Test
    fun `returns redirects without following them and sends nothing identifying`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "https://dest.example/x").build())
        val response = transport().head(url("/short"))
        assertEquals(302, response.status)
        assertEquals("https://dest.example/x", response.location)
        val request = server.takeRequest()
        assertEquals("HEAD", request.method)
        assertNull(request.headers["Cookie"])
        assertNull(request.headers["Authorization"])
        assertNull(request.headers["Referer"])
        assertTrue(request.headers["User-Agent"]!!.startsWith("Shear/"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `get uses GET and never reads the body`() = runBlocking {
        val huge = "x".repeat(4 shl 20)
        server.enqueue(MockResponse.Builder().code(200).body(huge).build())
        val started = System.nanoTime()
        val response = transport().get(url("/page"))
        assertEquals(200, response.status)
        assertNull(response.location)
        assertEquals("GET", server.takeRequest().method)
        assertTrue((System.nanoTime() - started) < 2_000_000_000L, "a 4 MiB body must not be downloaded")
    }

    @Test
    fun `the default address policy blocks loopback before any request is made`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).build())
        val failure = assertThrows<TransportException> { transport(AddressPolicy.Default).head(url("/local")) }
        assertEquals(FailureKind.BLOCKED_ADDRESS, failure.kind)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `classifies timeouts and connection failures`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).onRequestStart(SocketEffect.Stall).build())
        val timeout = assertThrows<TransportException> { transport(readTimeout = 300.milliseconds).head(url("/slow")) }
        assertEquals(FailureKind.TIMEOUT, timeout.kind)
        val dead = url("/gone")
        server.close()
        val io = assertThrows<TransportException> { transport().head(dead) }
        assertTrue(io.kind == FailureKind.IO || io.kind == FailureKind.TIMEOUT, "got ${io.kind}")
    }

    @Test
    fun `end to end through the resolver against a local chain`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(301).addHeader("Location", "/two?utm_source=x").build())
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/three").build())
        server.enqueue(MockResponse.Builder().code(200).build())
        val shear = dev.gswizz.shear.core.Shear.default(transport())
        val result = shear.process("see ${url("/one")}", ResolveMode.RESOLVE_ALL)
        assertEquals("see ${url("/three")}", result.outputText)
        assertEquals(listOf("/one", "/two", "/three"), (1..3).map { server.takeRequest().url.encodedPath })
        assertEquals(2, result.urls.single().redirects.size)
    }
}
