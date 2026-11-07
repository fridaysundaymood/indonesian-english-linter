package io.github.i18nlint.cli

import org.junit.jupiter.api.Test
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PackagingTest {
    @Test fun `bundled lint checks are isolated from the CLI runtime`() {
        JarFile(System.getProperty("cliJar")).use { cli ->
            val bundled = assertNotNull(cli.getJarEntry("indonesian-english-lint-checks.jar"))
            JarInputStream(cli.getInputStream(bundled)).use { checks ->
                assertEquals(
                    "dev.androidkit.i18n.I18nIssueRegistry",
                    checks.manifest.mainAttributes.getValue("Lint-Registry-v2"),
                )
                val entries = mutableSetOf<String>()
                while (true) {
                    val entry = checks.nextJarEntry ?: break
                    entries.add(entry.name)
                }
                assertTrue("dev/androidkit/i18n/I18nIssueRegistry.class" in entries)
                assertTrue("org/yaml/snakeyaml/Yaml.class" in entries)
                val forbidden = listOf("com/intellij/", "org/jetbrains/kotlin/", "org/jetbrains/uast/", "com/android/tools/lint/")
                assertTrue(entries.none { entry -> forbidden.any(entry::startsWith) })
            }
        }
    }
}
