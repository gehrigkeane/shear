/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.core.engine

import dev.gswizz.shear.core.rules.BraveRules
import dev.gswizz.shear.core.rules.ParamRule
import dev.gswizz.shear.core.url.UrlParts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** Every clean-urls and query-filter rule in the vendored snapshot, exercised alone and together. */
class StageGoldenTest {
    private val rules = BraveRules.load()

    private fun sink() = TraceSink(rules.version)

    private fun goldens(
        name: String,
        all: List<ParamRule>,
        stageOf: (List<ParamRule>) -> (UrlParts, TraceSink) -> UrlParts,
    ): List<DynamicTest> {
        val tests = mutableListOf<DynamicTest>()
        var dead = 0
        for (rule in all) {
            val includes = rule.patterns.include.map { it.toString() }
            for ((pi, pattern) in includes.withIndex()) {
                for (bare in listOf(false, true)) {
                    val golden = GoldenSupport.synthesize(pattern, rule.params.toList(), bareHost = bare)
                    if (golden == null) {
                        dead++
                        continue
                    }
                    val label = "$name[${rule.index}] include[$pi]${if (bare) " apex" else ""}: ${golden.url.take(70)}"
                    tests +=
                        DynamicTest.dynamicTest(label) {
                            val alone = stageOf(listOf(rule))(UrlParts.parse(golden.url)!!, sink()).toUrlString()
                            assertEquals(golden.expected, alone, "single rule")
                            val together = stageOf(all)(UrlParts.parse(golden.url)!!, sink()).toUrlString()
                            assertEquals(golden.expected, together, "all rules")
                        }
                    if (!pattern.startsWith("*://*.")) break
                }
            }
            for ((ei, excluded) in rule.patterns.exclude.withIndex()) {
                val golden = GoldenSupport.synthesize(excluded.toString(), rule.params.toList()) ?: continue
                tests +=
                    DynamicTest.dynamicTest("$name[${rule.index}] exclude[$ei] untouched: ${golden.url.take(70)}") {
                        val out = stageOf(listOf(rule))(UrlParts.parse(golden.url)!!, sink()).toUrlString()
                        assertEquals(golden.url, out)
                    }
            }
        }
        assertTrue(tests.size > all.size, "expected at least one golden per rule")
        return tests
    }

    @TestFactory
    fun `clean-urls rules strip exactly their parameters`(): List<DynamicTest> =
        goldens("clean-urls", rules.cleanUrls) { set -> { url, sink -> QuerySanitizer(set).apply(url, sink) } }

    @TestFactory
    fun `query-filter rules strip exactly their parameters`(): List<DynamicTest> =
        goldens("query-filter", rules.queryFilter) { set ->
            { url, sink -> QueryFilter(set, rules.conditionalTrackers).apply(url, sink) }
        }
}
