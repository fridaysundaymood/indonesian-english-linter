package dev.androidkit.i18n

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.source
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class FlowDetectorTest {
    private val config = source("i18n-lint.yaml", """
        domains:
          Status:
            values: [todo, done]
            localizer: localized
        ui_sinks: [Text, showSnackbar]
        data_sinks: [encode]
        user_facing_exceptions: [UserFacingException]
        locales: [values, values-in]
    """.trimIndent())

    @Test fun kotlinFlows() {
        lint().issues(RAW_ID, EXCEPTION_UI, DATA_LEAK).files(config, kotlin("""
            enum class Status { todo, done; fun localized(): String = name }
            fun Text(s: String) {}
            fun showSnackbar(s: String) {}
            fun encode(s: String) {}
            fun test(status: Status, error: Exception) {
                Text(status.name)
                Text(status.localized())
                showSnackbar(error.message ?: "error")
                encode(status.localized())
            }
        """)).allowMissingSdk().run().expectWarningCount(3)
    }

    @Test fun javaFlows() {
        lint().issues(RAW_ID, EXCEPTION_UI).files(config, java("""
            class Example {
              enum Status { TODO }
              static void Text(String text) {}
              void test(Status status, Exception error) {
                Text(status.name());
                Text(error.getMessage());
              }
            }
        """)).allowMissingSdk().run().expectWarningCount(2)
    }

    @Test fun suppressionAndSanitizationAreScoped() {
        lint().issues(RAW_ID).files(config, kotlin("""
            enum class Status { todo; fun localized(): String = name }
            fun Text(s: String) {}
            @Suppress("RawIdDisplay")
            fun suppressed(status: Status) { Text(status.name) }
            fun mixed(status: Status, flag: Boolean) {
                Text(if (flag) status.name else status.localized())
            }
        """)).allowMissingSdk().run().expectWarningCount(1)
    }

    @Test fun localizedExceptionMustReachUi() {
        lint().issues(SWALLOWED).files(config, kotlin("""
            class UserFacingException(message: String) : Exception(message)
            fun getString(): String = "localized"
            fun showSnackbar(message: String) {}
            fun fail(): Nothing { throw UserFacingException(getString()) }
            fun dropped() {
                try { fail() } catch (e: UserFacingException) { showSnackbar("generic") }
            }
            fun preserved() {
                try { fail() } catch (e: UserFacingException) { showSnackbar(e.message ?: "generic") }
            }
            fun technical() {
                try { throw IllegalStateException("technical") } catch (e: Exception) { showSnackbar("generic") }
            }
        """)).allowMissingSdk().run().expectWarningCount(1)
    }

    @Test fun crossFileSanitizedDisplayAndCanonicalDataAreClean() {
        lint().issues(RAW_ID, DATA_LEAK).files(config,
            kotlin("src/example/Status.kt", """
                package example
                enum class Status { TODO; val id get() = name; fun localized() = name }
            """),
            kotlin("src/example/Behavior.kt", """
                package example
                object Behavior {
                    fun display(status: Status) = status.localized()
                    fun data(status: Status) = status.id
                }
            """),
            kotlin("src/example/Screen.kt", """
                package example
                fun Text(value: String) {}
                fun encode(value: () -> String) {}
                fun screen(status: Status) {
                    Text(Behavior.display(status))
                    encode { Behavior.data(status) }
                }
            """)
        ).allowMissingSdk().run().expectClean()
    }

    @Test fun crossFileRawDisplayAndLocalizedDataAreReported() {
        lint().issues(RAW_ID, DATA_LEAK).files(config,
            kotlin("src/example/Status.kt", """
                package example
                enum class Status { TODO; val id get() = name; fun localized() = name }
            """),
            kotlin("src/example/Behavior.kt", """
                package example
                object Behavior {
                    fun display(status: Status) = status.id
                    fun data(status: Status) = status.localized()
                }
            """),
            kotlin("src/example/Screen.kt", """
                package example
                fun Text(value: String) {}
                fun encode(value: () -> String) {}
                fun screen(status: Status) {
                    Text(Behavior.display(status))
                    encode { Behavior.data(status) }
                }
            """)
        ).allowMissingSdk().run().expectWarningCount(2)
    }
    @Test fun resourceDisplayAndUnknownValuesAreSafe() {
        lint().issues(RAW_ID, DATA_LEAK).files(config, java("""
            package android.content;
            public class Context {
                public String getString(int id, Object... args) { return ""; }
            }
        """), java("""
            package android.content.res;
            public class Resources {
                public String getQuantityString(int id, int quantity, Object... args) { return ""; }
            }
        """), kotlin("""
            import android.content.Context
            import android.content.res.Resources
            enum class Status { todo; fun localized(): String = name }
            enum class SnackbarResult { ActionPerformed, Dismissed }
            fun Text(value: Any) {}
            fun stringResource(id: Int, vararg args: Any): String = ""
            class Preferences { fun getString(key: String, fallback: String?): String? = fallback }
            fun encode(value: String?) {}
            fun showSnackbar(message: String): SnackbarResult = SnackbarResult.Dismissed
            fun screen(status: Status, text: String, count: Int, context: Context, resources: Resources, preferences: Preferences) {
                Text(text)
                Text(count)
                Text(stringResource(1, status.name))
                Text(context.getString(1, status.name))
                Text(resources.getQuantityString(1, count, status.name))
                encode(preferences.getString("refresh_token", null))
                encode(context.getString(1))
                if (showSnackbar(stringResource(1)) == SnackbarResult.ActionPerformed) Text(text)
                if (status.localized() == text) Text(text)
            }
        """)).allowMissingSdk().run().expectWarningCount(1)
    }

    @Test fun domainDisplayAndLocalizedDataPathsAreReported() {
        lint().issues(RAW_ID, DATA_LEAK).files(config, kotlin("""
            enum class Status { todo; fun localized(): String = name }
            fun Text(value: String) {}
            fun encode(value: String) {}
            fun stringResource(id: Int): String = ""
            fun screen(status: Status) {
                Text(status.name)
                encode(status.localized())
                encode(stringResource(1))
                if (status.localized() == status.name) Text("match")
            }
        """)).allowMissingSdk().run().expectWarningCount(4)
    }

    @Test fun formattingTemplatesAndWhenConditionsAreNotIdentifiers() {
        lint().issues(RAW_ID).files(config, kotlin("""
            enum class Status { todo, done }
            fun Text(value: String) {}
            fun screen(count: Int, date: Long, code: String) {
                Text(count.toString())
                Text("%02d".format(count))
                Text("$" + count)
                Text("date: $" + date)
                Text(when (code) { "todo" -> "To do"; "done" -> "Done"; else -> "Other" })
                Text(if (code == "todo") "To do" else "Other")
            }
        """)).allowMissingSdk().run().expectClean()
    }

    @Test fun declaredValuesTypesAndAliasesAreReported() {
        lint().issues(RAW_ID).files(config, kotlin("""
            enum class Status { todo, done }
            fun Text(value: String) {}
            fun screen(status: Status) {
                val alias = status.name
                Text(alias)
                Text("todo")
                Text(status.name)
            }
        """)).allowMissingSdk().run().expectWarningCount(3)
    }

    @Test fun valuesFromRequiresAnExactElement() {
        val sourcedConfig = source("i18n-lint.yaml", """
            domains:
              Status:
                values_from: Choices.kt:statuses
                localizer: localized
            ui_sinks: [Text]
        """.trimIndent())
        lint().issues(RAW_ID).files(sourcedConfig, kotlin("src/Choices.kt", """
            val statuses = listOf("todo", "done")
            fun Text(value: String) {}
            fun screen(count: Int, statusesCount: Int) {
                Text(statuses[0])
                Text(count.toString())
                Text(statusesCount.toString())
            }
        """)).allowMissingSdk().run().expectWarningCount(1)
    }

    @Test fun identifierAnnotationAndLocalizerBoundary() {
        lint().issues(RAW_ID).files(config, kotlin("""
            annotation class IdentifierDomain
            @IdentifierDomain fun identifier(): String = "opaque"
            fun localized(value: String): String = value
            fun Text(value: String) {}
            fun screen() {
                Text(identifier())
                Text(localized(identifier()))
            }
        """)).allowMissingSdk().run().expectWarningCount(1)
    }

}
