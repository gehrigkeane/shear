plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(25)
    explicitApi()
    compilerOptions { allWarningsAsErrors = true }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

testing {
    suites {
        // useJUnitJupiter wires the JUnit Platform launcher automatically; Gradle 9 demands it explicitly otherwise.
        named<JvmTestSuite>("test") { useJUnitJupiter(libs.versions.junit.get()) }
    }
}
