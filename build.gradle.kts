plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
}

// Every Kotlin source file carries the MPL-2.0 notice. Spotless inserts it on `fmt` and fails `fmt-check` without it.
val mplHeader =
    """
    /*
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     */
    """
        .trimIndent()

spotless {
    kotlin {
        // Android source sets are not auto-detected; name them and keep build output out.
        target("*/src/*/kotlin/**/*.kt")
        targetExclude("**/build/**")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle().configure { it.setMaxWidth(120) }
        licenseHeader(mplHeader)
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle().configure { it.setMaxWidth(120) }
    }
    format("xml") {
        target("*/src/**/*.xml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
