/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.data

import dev.gswizz.shear.core.TextResult
import dev.gswizz.shear.core.engine.CleanFailure
import dev.gswizz.shear.core.engine.FailureKind
import dev.gswizz.shear.core.engine.RedirectKind
import dev.gswizz.shear.core.engine.RedirectStep
import dev.gswizz.shear.core.engine.RemovedParameter
import dev.gswizz.shear.core.engine.RuleApplication
import dev.gswizz.shear.core.engine.RuleSource
import dev.gswizz.shear.core.engine.UrlCleanResult

object HistoryFixtures {
    const val ORIGINAL = "see https://go.example/r?u=https%3A%2F%2Fdest.example%2Fa%3Futm_source%3Dx%26id%3D1 ok"
    const val CLEANED = "see https://dest.example/a?id=1 ok"

    val trace =
        UrlCleanResult(
            originalUrl = "https://go.example/r?u=https%3A%2F%2Fdest.example%2Fa%3Futm_source%3Dx%26id%3D1",
            finalUrl = "https://dest.example/a?id=1",
            applications =
                listOf(
                    RuleApplication(
                        RuleSource.DEBOUNCE,
                        3,
                        0,
                        0,
                        "https://go.example/r?u=…",
                        "https://dest.example/a?utm_source=x&id=1",
                        action = "redirect",
                    ),
                    RuleApplication(
                        RuleSource.CLEAN_URLS,
                        44,
                        0,
                        0,
                        "https://dest.example/a?utm_source=x&id=1",
                        "https://dest.example/a?id=1",
                        listOf("utm_source=x"),
                    ),
                ),
            removedParameters = listOf(RemovedParameter("utm_source", "utm_source=x", RuleSource.CLEAN_URLS, 44)),
            redirects =
                listOf(
                    RedirectStep(
                        "https://go.example/r?u=…",
                        "https://dest.example/a?utm_source=x&id=1",
                        RedirectKind.OFFLINE,
                        ruleIndex = 3,
                    )
                ),
            failure = null,
            rulesVersion = "c304773bcacaa22171b93f818321d89657d9f55e",
        )

    val failedTrace =
        trace.copy(failure = CleanFailure(FailureKind.TIMEOUT, "https://dest.example/a?id=1", "exceeded 10s"))

    val textResult = TextResult(ORIGINAL, CLEANED, listOf(trace))
}
