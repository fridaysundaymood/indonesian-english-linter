package io.github.i18nlint.cli

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertFailsWith

class ConfigurationTest {
    @TempDir lateinit var directory: Path

    @Test fun `validates standalone section against bundled schema`() {
        val config = directory.resolve("i18n-lint.yaml")
        config.writeText("""
            domains:
              category:
                localizer: localizedCategory
                values: [Cash, Card]
            locales: [values, values-in]
        """.trimIndent())
        validateConfig(config.toFile())
        config.writeText("domains:\n  category:\n    localizer: localizedCategory\n    values: [Cash]\n    values_from: catalog\n")
        assertFailsWith<Exception> { validateConfig(config.toFile()) }
        config.writeText("locales: [values]\nunknown: true\n")
        assertFailsWith<Exception> { validateConfig(config.toFile()) }
    }

    @Test fun `validates only the i18n section of kit config`() {
        val config = directory.resolve("android-kit.yaml")
        config.writeText("other_tool: {}\ni18n:\n  locales: [values, values-in]\n")
        validateConfig(config.toFile())
        config.writeText("other_tool: {}\n")
        assertFailsWith<Exception> { validateConfig(config.toFile()) }
    }
}
