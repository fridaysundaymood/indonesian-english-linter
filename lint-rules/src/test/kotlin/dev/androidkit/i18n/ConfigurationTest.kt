package dev.androidkit.i18n

import org.junit.Assert.assertEquals
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
}
