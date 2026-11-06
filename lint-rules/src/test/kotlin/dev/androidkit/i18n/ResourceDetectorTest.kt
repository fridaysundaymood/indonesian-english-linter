package dev.androidkit.i18n

import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class ResourceDetectorTest {
    @Test fun missingTranslationIsReported() {
        lint().issues(MISSING_LOCALE).files(
            xml("res/values/strings.xml", """<resources><string name="greeting">Hello %1${'$'}s</string><string name="missing">Missing</string></resources>"""),
            xml("res/values-in/strings.xml", """<resources><string name="greeting">Halo %1${'$'}d</string></resources>""")
        ).allowMissingSdk().run().expectWarningCount(2)
    }
}
