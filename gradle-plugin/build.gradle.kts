plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}

kotlin { jvmToolchain(17) }

tasks.jar {
    manifest.attributes["Implementation-Version"] = project.version
}

gradlePlugin {
    plugins {
        create("i18nLint") {
            id = "dev.androidkit.i18n-lint"
            implementationClass = "dev.androidkit.i18n.gradle.I18nLintPlugin"
        }
    }
}

dependencies {
    testImplementation(kotlin("test"))
}
