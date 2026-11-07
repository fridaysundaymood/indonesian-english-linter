pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    plugins { id("com.android.application") version "9.0.1" }
}
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "standalone-cli-fixture"
