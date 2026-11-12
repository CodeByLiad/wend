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
        target("core/*/src/**/*.kt", "tools/*/src/**/*.kt", "feature/*/src/**/*.kt", "app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(mapOf("ktlint_code_style" to "intellij_idea"))
    }
    kotlinGradle {
        target("*.gradle.kts", "core/*/build.gradle.kts", "tools/*/build.gradle.kts", "feature/*/build.gradle.kts", "app/build.gradle.kts")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(mapOf("ktlint_code_style" to "intellij_idea"))
    }
}

tasks.named("assemble") { dependsOn(":core:engine:assemble", ":core:ai:assemble", ":tools:benchmark:assemble", ":tools:eval:assemble", ":core:designsystem:assemble", ":feature:today:assemble", ":app:assemble") }
tasks.named("check") { dependsOn(":core:engine:check", ":core:ai:check", ":tools:benchmark:check", ":tools:eval:check", ":core:designsystem:check", ":feature:today:check", ":app:check", "spotlessCheck") }
tasks.register("lint") { dependsOn(":core:ai:lint", ":tools:benchmark:lint", ":core:designsystem:lint", ":feature:today:lint", ":app:lint") }
