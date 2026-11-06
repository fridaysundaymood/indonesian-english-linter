pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    val agpVersion = providers.gradleProperty("agpVersion").orElse("9.5.0").get()
    plugins { id("com.android.application") version agpVersion }
}

dependencyResolutionManagement {
    repositories { mavenLocal(); google(); mavenCentral() }
}
rootProject.name = "compatibility-fixture"
