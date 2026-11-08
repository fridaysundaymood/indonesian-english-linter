package dev.androidkit.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import java.nio.file.Files
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConfigurationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun readsValuesFromCatalog() {
        val root = folder.newFolder("project")
        root.resolve("Catalog.kt").writeText("val statuses = listOf(\"todo\", \"done\")")
        root.resolve("i18n-lint.yaml").writeText("""
            domains:
              status:
                values_from: Catalog.kt:statuses
                localizer: localizedStatus
        """.trimIndent())
        assertEquals(setOf("todo", "done"), Configuration.load(root).domains.getValue("status").values)
    }

    @Test fun rejectsUnsafeYamlAndInvalidSchema() {
        val root = folder.newFolder("invalid")
        val config = root.resolve("i18n-lint.yaml")
        config.writeText("!!java.net.URL ['https://example.invalid']")
        assertThrows(Exception::class.java) { Configuration.load(root) }
        config.writeText("unknown: true")
        assertThrows(IllegalArgumentException::class.java) { Configuration.load(root) }
        config.writeText("domains:\n  status:\n    values: [todo]")
        assertThrows(IllegalArgumentException::class.java) { Configuration.load(root) }
    }

    @Test fun rejectsEscapingValuesFromAndDoesNotMatchPathByBasename() {
        val root = folder.newFolder("contained")
        val config = root.resolve("i18n-lint.yaml")
        root.resolve("Catalog.kt").writeText("val statuses = listOf(\"wrong\")")
        config.writeText("domains:\n  status:\n    localizer: localize\n    values_from: ../Catalog.kt:statuses")
        assertThrows(IllegalArgumentException::class.java) { Configuration.load(root) }
        config.writeText("domains:\n  status:\n    localizer: localize\n    values_from: missing/Catalog.kt:statuses")
        assertTrue(Configuration.load(root).domains.getValue("status").values.isEmpty())
    }

    @Test fun discoveryStopsAtProjectBoundary() {
        val parent = folder.newFolder("outer")
        parent.resolve("i18n-lint.yaml").writeText("unknown: true")
        val root = parent.resolve("inner").apply { mkdir() }
        root.resolve("settings.gradle.kts").writeText("")
        assertTrue(Configuration.load(root).domains.isEmpty())
    }

    @Test fun rejectsSymlinkOutsideConfigurationDirectory() {
        val root = folder.newFolder("symlink-project")
        val outside = folder.newFile("external.kt")
        outside.writeText("val statuses = listOf(\"secret\")")
        try {
            Files.createSymbolicLink(root.resolve("Catalog.kt").toPath(), outside.toPath())
        } catch (e: Exception) {
            assumeNoException(e)
        }
        root.resolve("i18n-lint.yaml").writeText("domains:\n  status:\n    localizer: localize\n    values_from: Catalog.kt:statuses")
        assertThrows(IllegalArgumentException::class.java) { Configuration.load(root) }
    }
}
