plugins {
    id("com.android.application")
    id("dev.androidkit.i18n-lint") version "0.1.0-SNAPSHOT"
}

android {
    namespace = "dev.androidkit.i18n.fixture"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.androidkit.i18n.fixture"
        minSdk = 23
        targetSdk = 36
    }
    lint {
        abortOnError = false
        xmlReport = true
        sarifReport = true
        checkOnly += setOf("IdInTranslation", "MissingLocaleKey")
    }
}

tasks.register("verifyLintReports") {
    dependsOn("lintDebug")
    doLast {
        val reports = layout.buildDirectory.dir("reports").get().asFile
        val xml = reports.resolve("lint-results-debug.xml")
        val sarif = reports.resolve("lint-results-debug.sarif")
        check(xml.isFile && sarif.isFile) { "Expected lint XML and SARIF reports in $reports" }
        val contents = xml.readText()
        check("IdInTranslation" in contents && "MissingLocaleKey" in contents) {
            "Expected both custom resource findings in $xml"
        }
    }
}
