package dev.androidkit.i18n

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity

class I18nIssueRegistry : IssueRegistry() {
    override val api = CURRENT_API
    override val minApi = CURRENT_API
    override val issues = listOf(RAW_ID, ID_TRANSLATION, EXCEPTION_UI, SWALLOWED, MISSING_LOCALE, DATA_LEAK)
}

private fun issue(id: String, summary: String, explanation: String, detector: Class<out com.android.tools.lint.detector.api.Detector>, scope: java.util.EnumSet<Scope>) =
    Issue.create(id, summary, explanation, Category.CORRECTNESS, 6, Severity.WARNING, Implementation(detector, scope))

internal val RAW_ID = issue("RawIdDisplay", "Canonical identifier displayed", "Localize built-in identifiers before displaying them.", FlowDetector::class.java, Scope.JAVA_FILE_SCOPE)
internal val EXCEPTION_UI = issue("ExceptionToUi", "Technical exception displayed", "Use an approved user-facing exception or a localized generic message.", FlowDetector::class.java, Scope.JAVA_FILE_SCOPE)
internal val SWALLOWED = issue("LocalizedExceptionSwallowed", "Localized exception message discarded", "Preserve approved localized messages at the UI error boundary.", FlowDetector::class.java, Scope.JAVA_FILE_SCOPE)
internal val DATA_LEAK = issue("LocalizedDataLeak", "Localized text enters a data path", "Store and transmit canonical identifiers rather than localized labels.", FlowDetector::class.java, Scope.JAVA_FILE_SCOPE)
internal val ID_TRANSLATION = issue("IdInTranslation", "Canonical identifier in translation", "Translate built-in identifier values in localized resources.", ResourceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
internal val MISSING_LOCALE = issue("MissingLocaleKey", "Missing or incompatible locale resource", "Provide each default resource with compatible format arguments in each shipped locale.", ResourceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
