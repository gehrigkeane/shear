/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.engine

import dev.gswizz.shear.core.url.UrlParts
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TraceTest {
    @Test
    fun `a sink collects applications redirects and the first failure into a result`() {
        val sink = TraceSink("v1")
        val a = UrlParts.parse("https://a.example/?utm_source=x")!!
        val b = UrlParts.parse("https://a.example/")!!
        sink.iteration = 2
        sink.hop = 1
        sink.application(RuleSource.CLEAN_URLS, 3, a, b, removed = listOf("utm_source=x"))
        sink.redirect(a, b, RedirectKind.NETWORK, status = 301)
        sink.fail(FailureKind.LOOP, b, "first")
        sink.fail(FailureKind.TIMEOUT, b, "second")
        val result = sink.result(a.toUrlString(), b.toUrlString())
        assertEquals("v1", result.rulesVersion)
        assertTrue(result.changed)
        assertEquals(1, result.applications.single().hop)
        assertEquals(2, result.applications.single().iteration)
        assertEquals("utm_source", result.removedParameters.single().name)
        assertEquals(301, result.redirects.single().status)
        assertEquals(FailureKind.LOOP, result.failure?.kind)
    }

    @Test
    fun `results round trip through json for storage`() {
        val result = TraceSink("v").result("https://a.example/", "https://a.example/")
        assertFalse(result.changed)
        val json = Json.encodeToString(UrlCleanResult.serializer(), result)
        assertEquals(result, Json.decodeFromString(UrlCleanResult.serializer(), json))
    }
}
