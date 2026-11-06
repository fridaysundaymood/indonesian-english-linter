package dev.androidkit.i18n

import com.android.tools.lint.checks.infrastructure.TestFiles.source
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class TranslationDetectorTest {
    @Test fun translatedCanonicalIdentifierIsReported() {
        lint().issues(ID_TRANSLATION).files(
            source("i18n-lint.yaml", """
                domains:
                  status:
                    values: [todo]
                    localizer: localizedStatus
                locales: [values, values-in]
            """.trimIndent()),
            xml("res/values/strings.xml", """<resources><string name="status">To do</string></resources>"""),
            xml("res/values-in/strings.xml", """<resources><string name="status">Status todo</string></resources>""")
        ).allowMissingSdk().run().expectWarningCount(1)
    }

    @Test fun xmlSuppressionIsHonored() {
        lint().issues(ID_TRANSLATION).files(
            source("i18n-lint.yaml", """
                domains:
                  status:
                    values: [todo]
                    localizer: localizedStatus
                locales: [values, values-in]
            """.trimIndent()),
            xml("res/values-in/strings.xml", """<resources xmlns:tools="http://schemas.android.com/tools"><string name="status" tools:ignore="IdInTranslation">todo</string></resources>""")
        ).allowMissingSdk().run().expectClean()
    }
}
