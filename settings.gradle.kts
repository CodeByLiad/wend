pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "wend"
include(":core:engine")
include(":core:ai", ":tools:benchmark")
include(":tools:eval")
include(":core:designsystem", ":feature:today", ":app")
