plugins {
    application
    alias(libs.plugins.kotlin.jvm)
}
kotlin { jvmToolchain(21) }
application { mainClass = "com.jinatra.wend.eval.MainKt" }
dependencies {
    implementation(project(":core:engine"))
    implementation(libs.jackson.core)
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotest.property)
    testRuntimeOnly(libs.junit.launcher)
}
tasks.test { useJUnitPlatform() }
