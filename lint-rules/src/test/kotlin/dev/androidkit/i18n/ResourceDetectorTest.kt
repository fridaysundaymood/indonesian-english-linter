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

    @Test fun pluralQuantitiesCompareArgumentSignatures() {
        lint().issues(MISSING_LOCALE).files(
            xml("res/values/strings.xml", """
                <resources>
                    <plurals name="count">
                        <item quantity="one">%1${'$'}d item for %2${'$'}s</item>
                        <item quantity="other">%1${'$'}d items for %2${'$'}s</item>
                    </plurals>
                    <plurals name="repeated">
                        <item quantity="one">%1${'$'}d item</item>
                        <item quantity="other">%1${'$'}d items (%1${'$'}d)</item>
                    </plurals>
                </resources>
            """),
            xml("res/values-in/strings.xml", """
                <resources>
                    <plurals name="count">
                        <item quantity="other">%2${'$'}s memiliki %1${'$'}x item</item>
                        <item quantity="zero">%2${'$'}s memiliki %1${'$'}d item</item>
                    </plurals>
                    <plurals name="repeated"><item quantity="other">%1${'$'}d item</item></plurals>
                </resources>
            """)
        ).allowMissingSdk().run().expectClean()
    }

    @Test fun pluralIncompatibleItemArgumentsAreReported() {
        lint().issues(MISSING_LOCALE).files(
            xml("res/values/strings.xml", """
                <resources>
                    <plurals name="type"><item quantity="one">%1${'$'}d</item><item quantity="other">%1${'$'}d</item></plurals>
                    <plurals name="missing"><item quantity="other">%1${'$'}d %2${'$'}s</item></plurals>
                    <plurals name="extra_quantity"><item quantity="other">%1${'$'}d</item></plurals>
                </resources>
            """),
            xml("res/values-in/strings.xml", """
                <resources>
                    <plurals name="type"><item quantity="other">%1${'$'}s</item></plurals>
                    <plurals name="missing"><item quantity="other">%1${'$'}d</item></plurals>
                    <plurals name="extra_quantity"><item quantity="other">%1${'$'}d</item><item quantity="zero">%1${'$'}s</item></plurals>
                </resources>
            """)
        ).allowMissingSdk().run().expectWarningCount(3)
    }
}
