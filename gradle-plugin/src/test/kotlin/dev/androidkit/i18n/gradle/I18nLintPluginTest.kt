package dev.androidkit.i18n.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeAndroidPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.configurations.create("lintChecks")
    }
}

class I18nLintPluginTest {
    @Test
    fun addsChecksWhenAndroidIsAppliedAfterPlugin() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(I18nLintPlugin::class.java)
        project.pluginManager.apply("com.android.application")
        val dependency = project.configurations.getByName("lintChecks").dependencies.single()
        assertEquals("dev.androidkit.i18n", dependency.group)
        assertEquals("lint-rules", dependency.name)
    }
}
