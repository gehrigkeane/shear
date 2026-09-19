/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.rules

import java.io.InputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BraveRulesTest {
    private fun resource(name: String) =
        requireNotNull(javaClass.getResourceAsStream("/$name")).bufferedReader().readText()

    private fun arraySize(name: String) = Json.parseToJsonElement(resource(name)).jsonArray.size

    @Test
    fun `the bundled snapshot loads completely and without diagnostics`() {
        val rules = BraveRules.load()
        assertEquals(emptyList<String>(), rules.diagnostics)
        assertTrue(rules.isHealthy)
        assertEquals(arraySize("brave/clean-urls.json"), rules.cleanUrls.size)
        assertEquals(arraySize("brave/debounce.json"), rules.debounce.size)
        assertEquals(arraySize("brave/query-filter.json"), rules.queryFilter.size)
        val trackers = Json.parseToJsonElement(resource("brave/conditional-trackers.json")).jsonObject
        assertEquals(trackers.keys, rules.conditionalTrackers.keys)
        assertTrue(rules.conditionalTrackers.getValue("mkt_tok").containsMatchIn("https://x.example/Unsubscribe"))
    }

    @Test
    fun `version is the adblock-lists commit recorded in UPSTREAM`() {
        val expected =
            resource("brave/UPSTREAM")
                .lines()
                .first { it.startsWith("adblock-lists-commit:") }
                .substringAfter(':')
                .trim()
        assertEquals(expected, BraveRules.load().version)
        assertEquals(40, expected.length)
        assertEquals("abc", RulesVersion.parse("x: y\nadblock-lists-commit: abc\nfetched: today"))
        assertEquals("def", RulesVersion.parse("commit: def"))
        assertEquals("unknown", RulesVersion.parse(""))
    }

    @Test
    fun `every rule keeps its file index and compiled shape`() {
        val rules = BraveRules.load()
        rules.cleanUrls.forEachIndexed { i, r ->
            assertEquals(i, r.index)
            assertTrue(r.patterns.include.isNotEmpty())
            assertTrue(r.params.isNotEmpty())
        }
        rules.debounce.forEachIndexed { i, r ->
            assertEquals(i, r.index)
            when (r.action) {
                DebounceAction.REGEX_PATH,
                DebounceAction.REGEX_PATH_TEMPLATE -> {
                    assertNotNull(r.regex)
                    assertTrue(r.groupCount >= 1)
                }
                DebounceAction.REDIRECT,
                DebounceAction.BASE64_REDIRECT -> assertTrue(r.regex == null && r.param.isNotEmpty())
            }
            if (r.action == DebounceAction.REGEX_PATH_TEMPLATE) assertNotNull(r.template)
        }
        assertTrue(rules.debounce.any { it.requiresDeAmp })
        assertTrue(rules.debounce.any { it.prependScheme == "https" })
    }

    @Test
    fun `invalid rules are dropped with a diagnostic instead of failing the load`() {
        val rules =
            BraveRules.compile(
                version = "test",
                cleanUrls =
                    listOf(
                        ParamRuleDto(include = listOf("*://ok.example/*"), params = listOf("a")),
                        ParamRuleDto(include = listOf("bogus"), params = listOf("b")),
                    ),
                queryFilter = listOf(ParamRuleDto(include = listOf("||adblock.example^"), params = listOf("c"))),
                conditionalTrackers = mapOf("k" to "(unclosed"),
                debounce =
                    listOf(
                        DebounceRuleDto(include = listOf("*://a.example/*"), action = "redirect", param = "u"),
                        DebounceRuleDto(include = listOf("*://a.example/*"), action = "teleport", param = "u"),
                        DebounceRuleDto(
                            include = listOf("*://a.example/*"),
                            action = "regex-path",
                            param = "no-groups",
                        ),
                        DebounceRuleDto(
                            include = listOf("*://a.example/*"),
                            action = "regex-path",
                            param = "^/(" + "a".repeat(250) + ")$",
                        ),
                        DebounceRuleDto(
                            include = listOf("*://a.example/*"),
                            action = "regex-path-template",
                            param = "^/(.+)$",
                            redirectUrlTemplate = "https://x.example/$2",
                        ),
                        DebounceRuleDto(
                            include = listOf("*://a.example/*"),
                            action = "regex-path-template",
                            param = "^/(.+)/(.+)$",
                            redirectUrlTemplate = "https://x.example/$1",
                        ),
                        DebounceRuleDto(
                            include = listOf("*://a.example/*"),
                            action = "regex-path",
                            param = "^/(.*)$",
                            pref = "brave.other.pref",
                        ),
                        DebounceRuleDto(
                            include = listOf("*://a.example/*"),
                            action = "regex-path",
                            param = "^/(.*)$",
                            pref = "brave.de_amp.enabled",
                        ),
                    ),
            )
        assertEquals(1, rules.cleanUrls.size)
        assertEquals(0, rules.queryFilter.size)
        assertEquals(0, rules.conditionalTrackers.size)
        assertEquals(listOf(0, 7), rules.debounce.map { it.index })
        assertTrue(rules.debounce[1].requiresDeAmp)
        // An unusable pattern reports itself and, when it was the only include, the rule it took down.
        assertEquals(11, rules.diagnostics.size, rules.diagnostics.joinToString("\n"))
        assertFalse(rules.isHealthy)
    }

    @Test
    fun `a missing file is a diagnostic and an empty rule set`() {
        val source = RulesSource { name ->
            if (name.endsWith("query-filter.json")) null else ClasspathRulesSource.open(name)
        }
        val rules = BraveRules.load(source)
        assertEquals(0, rules.queryFilter.size)
        assertTrue(rules.diagnostics.any { "query-filter.json" in it })
        assertFalse(rules.isHealthy)
    }

    @Test
    fun `sources are plain streams so a future remote bundle only needs a new source`() {
        val captured = mutableListOf<String>()
        val recording = RulesSource { name ->
            captured += name
            ClasspathRulesSource.open(name)
        }
        BraveRules.load(recording)
        assertEquals(
            listOf(
                "brave/UPSTREAM",
                "brave/clean-urls.json",
                "brave/debounce.json",
                "brave/query-filter.json",
                "brave/conditional-trackers.json",
            ),
            captured,
        )
    }

    private fun InputStream.text() = bufferedReader().readText()
}
