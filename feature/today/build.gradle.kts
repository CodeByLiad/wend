plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jinatra.wend.today"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions.unitTests.all { it.useJUnitPlatform() }
}
dependencies {
    implementation(project(":core:engine"))
    implementation(project(":core:designsystem"))
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.tooling)
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
    testRuntimeOnly(libs.junit.launcher)
}
