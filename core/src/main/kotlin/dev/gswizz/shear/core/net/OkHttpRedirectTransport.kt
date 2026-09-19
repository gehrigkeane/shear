/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.net

import dev.gswizz.shear.core.engine.FailureKind
import dev.gswizz.shear.core.url.UrlParts
import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * [RedirectTransport] over OkHttp.
 *
 * The client never follows redirects, never sends cookies (OkHttp's default jar is empty), adds no `Authorization` or
 * `Referer`, identifies itself as Shear, and resolves every host through [GuardedDns], which drops non-public addresses
 * before a connection is attempted. Response bodies are closed unread.
 */
public class OkHttpRedirectTransport(private val client: OkHttpClient = defaultClient(AddressPolicy.Default)) :
    RedirectTransport {
    override suspend fun head(url: UrlParts): TransportResponse = execute(url) { it.head() }

    override suspend fun get(url: UrlParts): TransportResponse = execute(url) { it.get() }

    private suspend fun execute(url: UrlParts, method: (Request.Builder) -> Request.Builder): TransportResponse {
        val httpUrl =
            url.toUrlString().toHttpUrlOrNull()
                ?: throw TransportException(FailureKind.BAD_LOCATION, "not an http url: $url")
        val request = method(Request.Builder().url(httpUrl).header("User-Agent", USER_AGENT)).build()
        return try {
            runInterruptible(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    TransportResponse(response.code, response.header("Location"))
                }
            }
        } catch (e: IOException) {
            throw TransportException(classify(e), e.message ?: e.javaClass.simpleName, e)
        }
    }

    private fun classify(e: IOException): FailureKind =
        when {
            generateSequence<Throwable>(e) { it.cause }.any { it is GuardedDns.BlockedAddressException } ->
                FailureKind.BLOCKED_ADDRESS
            e is SocketTimeoutException || e is InterruptedIOException -> FailureKind.TIMEOUT
            e is SSLException -> FailureKind.TLS
            e is UnknownHostException -> FailureKind.IO
            else -> FailureKind.IO
        }

    /** A [Dns] that resolves through [delegate] and refuses every address [policy] blocks. */
    public class GuardedDns(private val delegate: Dns, private val policy: AddressPolicy) : Dns {
        /** Raised when every address for a host was blocked; a subclass so OkHttp reports it as a DNS failure. */
        public class BlockedAddressException(host: String) :
            UnknownHostException("blocked non-public address for $host")

        override fun lookup(hostname: String): List<InetAddress> {
            val allowed = delegate.lookup(hostname).filterNot(policy::isBlocked)
            if (allowed.isEmpty()) throw BlockedAddressException(hostname)
            return allowed
        }
    }

    public companion object {
        private const val USER_AGENT = "Shear/0.1 (+https://github.com/gehrigkeane/shear)"
        private val DEFAULT_TIMEOUT = 5.seconds

        /** The client configuration Shear ships with; tests pass [AddressPolicy.AllowAll] to reach a local server. */
        public fun defaultClient(
            policy: AddressPolicy,
            connectTimeout: Duration = DEFAULT_TIMEOUT,
            readTimeout: Duration = DEFAULT_TIMEOUT,
        ): OkHttpClient =
            OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(connectTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .callTimeout((connectTimeout + readTimeout).inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .dns(GuardedDns(Dns.SYSTEM, policy))
                .build()
    }
}
