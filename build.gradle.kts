plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

spotless {
    kotlin {
        target("core/*/src/**/*.kt", "tools/*/src/**/*.kt")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(mapOf("ktlint_code_style" to "intellij_idea"))
    }
    kotlinGradle {
        target("*.gradle.kts", "core/*/build.gradle.kts", "tools/*/build.gradle.kts")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(mapOf("ktlint_code_style" to "intellij_idea"))
    }
}

tasks.named("assemble") { dependsOn(":core:engine:assemble", ":core:ai:assemble", ":tools:benchmark:assemble") }
tasks.named("check") { dependsOn(":core:engine:check", ":core:ai:check", ":tools:benchmark:check", "spotlessCheck") }
tasks.register("lint") { dependsOn(":core:ai:lint", ":tools:benchmark:lint") }
