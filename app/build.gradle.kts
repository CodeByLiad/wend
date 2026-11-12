plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jinatra.wend"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.jinatra.wend"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1-prototype"
    }
    flavorDimensions += "distribution"
    productFlavors {
        // Builds and runs without Google Play services.
        create("foss") { dimension = "distribution" }
        // The only flavour that may add Google Play services, when a phase needs them.
        create("gms") { dimension = "distribution" }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
dependencies {
    implementation(project(":core:engine"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:today"))
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.compose)
}
