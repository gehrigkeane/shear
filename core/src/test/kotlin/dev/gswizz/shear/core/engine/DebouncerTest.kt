/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.engine

import dev.gswizz.shear.core.psl.PublicSuffixList
import dev.gswizz.shear.core.rules.BraveRules
import dev.gswizz.shear.core.rules.DebounceRuleDto
import dev.gswizz.shear.core.url.UrlParts
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DebouncerTest {
    private val psl =
        PublicSuffixList.parse(
            """
            // ===BEGIN ICANN DOMAINS===
            example
            // ===END ICANN DOMAINS===
            """
                .trimIndent()
        )

    private fun rule(
        include: String,
        action: String,
        param: String,
        prepend: String? = null,
        template: String? = null,
        pref: String? = null,
    ) =
        DebounceRuleDto(
            include = listOf(include),
            action = action,
            param = param,
            prependScheme = prepend,
            redirectUrlTemplate = template,
            pref = pref,
        )

    private val rules =
        BraveRules.compile(
            version = "t",
            cleanUrls = emptyList(),
            queryFilter = emptyList(),
            conditionalTrackers = emptyMap(),
            debounce =
                listOf(
                    rule("*://go.example/*", "redirect", "u"),
                    rule("*://b64.example/*", "base64,redirect", "b"),
                    rule("*://rx.example/*", "regex-path", "^/out/(.*)$"),
                    rule("*://amp.example/*", "regex-path", "^/amp/(.*)$", prepend = "https"),
                    rule(
                        "*://tpl.example/*",
                        "regex-path-template",
                        "^/v/([^/]+)/([^/]+)$",
                        template = "https://video.example/$1?t=$2",
                    ),
                    rule("*://first.example/*", "redirect", "u"),
                    rule("*://first.example/*", "redirect", "v"),
                    rule("*://pre.example/*", "redirect", "u", prepend = "https"),
                    rule("*://pref.example/*", "regex-path", "^/p/(.*)$", pref = "brave.de_amp.enabled"),
                    rule("*://multi.example/*", "regex-path", "^/m/([^/]*)/x/(.*)$"),
                ),
        )

    private fun debounce(url: String, sink: TraceSink = TraceSink("t"), deAmp: Boolean = true): String =
        Debouncer(rules.debounce, psl, deAmpEnabled = deAmp).apply(UrlParts.parse(url)!!, sink).toUrlString()

    @Test
    fun `unwraps a form-encoded destination and canonicalizes what gurl would`() {
        assertEquals(
            "https://dest.example/a?b=1%202",
            debounce("https://go.example/?u=https%3A%2F%2Fdest.example%2Fa%3Fb%3D1+2"),
        )
        // %2B decodes to a literal plus, which is a legal path character and is kept as GURL keeps it.
        assertEquals("https://dest.example/a+b", debounce("https://go.example/?u=https%3A%2F%2Fdest.example%2Fa%2Bb"))
        assertEquals("https://dest.example/", debounce("https://go.example/r?x=1&u=https://dest.example/&y=2"))
    }

    @Test
    fun `leaves the url alone when the parameter is missing empty or not a usable url`() {
        assertEquals("https://go.example/?v=1", debounce("https://go.example/?v=1"))
        assertEquals("https://go.example/?u=", debounce("https://go.example/?u="))
        assertEquals("https://go.example/?u=not%20a%20url", debounce("https://go.example/?u=not%20a%20url"))
        assertEquals(
            "https://go.example/?u=javascript%3Aalert(1)",
            debounce("https://go.example/?u=javascript%3Aalert(1)"),
        )
        assertEquals(
            "https://go.example/?u=mailto%3Aa%40b.example",
            debounce("https://go.example/?u=mailto%3Aa%40b.example"),
        )
    }

    @Test
    fun `refuses same-site destinations userinfo tricks and hosts without a registrable domain`() {
        assertEquals(
            "https://go.example/?u=https%3A%2F%2Fsub.go.example%2Fx",
            debounce("https://go.example/?u=https%3A%2F%2Fsub.go.example%2Fx"),
        )
        assertEquals(
            "https://go.example/?u=https%3A%2F%2Fgood.example%40evil.example%2F",
            debounce("https://go.example/?u=https%3A%2F%2Fgood.example%40evil.example%2F"),
        )
        assertEquals(
            "https://go.example/?u=https%3A%2F%2Flocalhost%2F",
            debounce("https://go.example/?u=https%3A%2F%2Flocalhost%2F"),
        )
        assertEquals(
            "https://go.example/?u=https%3A%2F%2F8.8.8.8%2F",
            debounce("https://go.example/?u=https%3A%2F%2F8.8.8.8%2F"),
        )
    }

    @Test
    fun `base64url with or without padding but not the standard alphabet`() {
        val target = "https://dest.example/z"
        val padded = Base64.getUrlEncoder().encodeToString(target.toByteArray())
        val bare = Base64.getUrlEncoder().withoutPadding().encodeToString(target.toByteArray())
        assertEquals(target, debounce("https://b64.example/?b=$padded"))
        assertEquals(target, debounce("https://b64.example/?b=$bare"))
        val standard = Base64.getEncoder().encodeToString("https://dest.example/??>".toByteArray())
        check('+' in standard || '/' in standard)
        val encoded = standard.replace("+", "%2B").replace("/", "%2F")
        assertEquals("https://b64.example/?b=$encoded", debounce("https://b64.example/?b=$encoded"))
    }

    @Test
    fun `regex-path concatenates captures and unescapes them`() {
        assertEquals(
            "https://dest.example/a?q=1",
            debounce("https://rx.example/out/https%3A%2F%2Fdest.example%2Fa%3Fq%3D1"),
        )
        assertEquals("https://dest.example/a", debounce("https://rx.example/out/https://dest.example/a?tracker=1"))
        assertEquals(
            "https://dest.example/tail",
            debounce("https://multi.example/m/https%3A%2F%2Fdest.example%2F/x/tail"),
        )
        assertEquals("https://rx.example/other/x", debounce("https://rx.example/other/x"))
    }

    @Test
    fun `prepend_scheme applies only when the capture is not already a url`() {
        assertEquals("https://dest.example/a", debounce("https://amp.example/amp/dest.example/a"))
        assertEquals(
            "https://amp.example/amp/https://dest.example/a",
            debounce("https://amp.example/amp/https://dest.example/a"),
        )
        assertEquals("https://dest.example/a", debounce("https://pre.example/?u=dest.example%2Fa"))
        assertEquals(
            "https://pre.example/?u=https%3A%2F%2Fdest.example",
            debounce("https://pre.example/?u=https%3A%2F%2Fdest.example"),
        )
    }

    @Test
    fun `templates substitute every capture group`() {
        assertEquals("https://video.example/abc?t=42", debounce("https://tpl.example/v/abc/42"))
        assertEquals("https://tpl.example/v/abc", debounce("https://tpl.example/v/abc"))
    }

    @Test
    fun `the first rule that produces a different url wins`() {
        assertEquals(
            "https://one.example/",
            debounce("https://first.example/?v=https%3A%2F%2Ftwo.example%2F&u=https%3A%2F%2Fone.example%2F"),
        )
        assertEquals("https://two.example/", debounce("https://first.example/?v=https%3A%2F%2Ftwo.example%2F"))
    }

    @Test
    fun `pref-gated rules follow the de-amp option`() {
        assertEquals("https://dest.example/", debounce("https://pref.example/p/https://dest.example/"))
        assertEquals(
            "https://pref.example/p/https://dest.example/",
            debounce("https://pref.example/p/https://dest.example/", deAmp = false),
        )
    }

    @Test
    fun `records the application and an offline redirect step`() {
        val sink = TraceSink("t")
        debounce("https://go.example/?u=https%3A%2F%2Fdest.example%2F", sink)
        val app = sink.applications.single()
        assertEquals(RuleSource.DEBOUNCE, app.source)
        assertEquals(0, app.ruleIndex)
        assertEquals("redirect", app.action)
        assertEquals("https://dest.example/", app.outputUrl)
        val step = sink.redirects.single()
        assertEquals(RedirectKind.OFFLINE, step.kind)
        assertEquals(0, step.ruleIndex)
        assertEquals("https://go.example/?u=https%3A%2F%2Fdest.example%2F", step.fromUrl)
    }
}
