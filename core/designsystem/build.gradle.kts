plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jinatra.wend.designsystem"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.material3)
    api(libs.compose.preview)
    debugImplementation(libs.compose.tooling)
}
