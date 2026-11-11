plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("core/engine/src/**/*.kt")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(mapOf("ktlint_code_style" to "kotlin_official"))
    }
    kotlinGradle {
        target("*.gradle.kts", "core/engine/build.gradle.kts")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(mapOf("ktlint_code_style" to "kotlin_official"))
    }
}

tasks.named("check") { dependsOn(":core:engine:check", "spotlessCheck") }
