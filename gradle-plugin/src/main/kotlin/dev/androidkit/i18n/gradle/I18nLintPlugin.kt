package dev.androidkit.i18n.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class I18nLintPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val addRules = {
            project.dependencies.add(
                "lintChecks",
                "dev.androidkit.i18n:lint-rules:${I18nLintPlugin::class.java.`package`.implementationVersion ?: "0.1.0-SNAPSHOT"}"
            )
            Unit
        }
        project.pluginManager.withPlugin("com.android.application") { addRules() }
        project.pluginManager.withPlugin("com.android.library") { addRules() }
        project.pluginManager.withPlugin("com.android.test") { addRules() }
        project.pluginManager.withPlugin("com.android.dynamic-feature") { addRules() }
    }
}
