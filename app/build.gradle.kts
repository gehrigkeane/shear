// AGP 9 compiles Kotlin itself; applying org.jetbrains.kotlin.android here is an error.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.gswizz.shear"
    // Android now ships minor platform releases; compile against the newest one installed by `mise run sdk`.
    compileSdk { version = release(37) { minorApiLevel = 2 } }

    defaultConfig {
        applicationId = "dev.gswizz.shear"
        minSdk = 37
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }

    buildFeatures { compose = true }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // Dependabot owns version currency; lint nagging about newer artifacts would block unrelated changes.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion")
    }
}

// Registered by AGP's built-in Kotlin; compilerOptions lives at top level and kotlinOptions is gone.
kotlin {
    jvmToolchain(25)
    compilerOptions { allWarningsAsErrors = true }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    lintChecks(libs.compose.lint.checks)
}
