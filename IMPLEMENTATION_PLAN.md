# Localization Display Linter: implementation plan

## Status and goal

This is a plan, not an implemented tool. Build a static linter for Kotlin/Compose Android applications that detects localization defects at display and data boundaries. English and Indonesian are the initial locale pair; configuration must support other locale pairs and additional shipped locales. Aim for actionable findings with low false positives. Machine translation and layout/overflow checks are out of scope.

Stable identifiers may remain in their canonical form in storage, sync, CSV, backups and comparisons, but built-in identifiers must be localized for display. User-defined names may pass through a localizer unchanged. Localized text must not replace canonical identifiers in data paths. Technical exception messages must not be exposed to users, while approved localized user-facing messages must not be discarded.

## Rules

| Rule | Detects |
|---|---|
| L1 `raw-id-display` | A declared identifier-domain value reaches a UI sink without passing its localizer. |
| L2 `id-in-translation` | A translated resource contains a canonical identifier from a declared domain where a localized term is required. |
| L3 `exception-to-ui` | A Throwable's message or `toString()` reaches a UI sink without an approved user-facing exception boundary. |
| L4 `localized-exception-swallowed` | A throw site constructs a localized message, but the UI error mapper discards it. |
| L5 `missing-locale-key` | A default resource is missing in a shipped locale or its format arguments are incompatible. |
| L6 `localized-data-leak` | A localizer's output reaches a storage, sync, export or identifier-comparison sink. |

## Configuration

A project-owned `i18n-lint.yaml` declares canonical identifier domains and values or their source declarations, localizer functions, UI and data sinks, approved user-facing exception types, and resource locales. The following is illustrative, not a shipped configuration:

```yaml
domains:
  category:
    values_from: "IdentifierCatalog.kt:categories"
    localizer: "localizedCategory"
  paymentMethod:
    values: [Cash, Card]
    localizer: "localizedPaymentMethod"
ui_sinks: [Text, contentDescription, Snackbar.showSnackbar, NotificationCompat.Builder.setContentText]
data_sinks: [JSONObject.put, Dao, exportCsv, encode]
user_facing_exceptions: [UserFacingException]
locales: [values, values-in]
```

Treat the default locale as the canonical resource set and make the shipped locale list configurable. Recognize Android locale resource qualifiers, including Indonesian's legacy `values-in` form. Domain configuration must distinguish built-in identifiers from user-editable names; a configured localizer is the display boundary even when it returns a custom name unchanged.

## Implementation

1. Parse Android resource XML, including strings, plurals and arrays, for L2 and L5. Compare format arguments as well as key presence.
2. Use Kotlin PSI via the Kotlin compiler embeddable or detekt custom rules for flow analysis. Model domain-typed properties, configured value sources and annotated returns as sources; localizer calls as display sanitizers; and configured display and data sinks. Start with intraprocedural flows plus one level of call inlining. Support `@DisplayText` and `@IdentifierDomain` annotations for precision.
3. Analyze exception message flows and localized-message construction and mapping for L3 and L4. Only declared user-facing exception types are approved boundaries.
4. Provide a CLI and Gradle task, SARIF and Markdown output with rule, file and line, snippet and suggested fix. Support a baseline so the Gradle task can fail on new findings without requiring immediate cleanup of existing ones.
5. Provide an MCP tool, `i18n_lint(path, rules?)`, returning findings as JSON.

## Milestones and acceptance

1. **M1 — resources:** L2 and L5 report seeded identifier-in-translation, missing-key and format-argument defects in a synthetic EN/ID fixture; configurable alternative locale pairs are covered.
2. **M2 — exceptions:** L3 and L4 identify seeded raw exception display and swallowed localized-message flows, while accepting approved user-facing exceptions.
3. **M3 — domain flows:** L1 and L6 identify seeded raw identifier displays and localized values entering data sinks. A corrected synthetic fixture produces no findings for these rules, including custom-name pass-through.
4. **M4 — integrations:** CLI, Gradle plugin, baseline, SARIF, Markdown and MCP wrapper expose the findings consistently.

## Testing and risks

Use a synthetic fixture app and schema, never private application history, with positive and negative cases for each rule, including localizer-to-data-sink flows, locale variants and format arguments. Exercise direct flows, one-level calls, lambdas and composables, and verify output locations and baseline behavior. Flow analysis can produce false positives in lambdas and composables: begin with direct flows, require annotations for wider reach, and retain baseline support rather than claiming complete program analysis.

## Kit compatibility

This tool is also a component of [android-kit](https://github.com/fridaysundaymood/android-kit/blob/main/IMPLEMENTATION_PLAN.md). From its first milestone it follows the kit composability rules: the shared result schema (`kit-schema`), its own section in `android-kit.yaml`, artifacts under `.android-kit/runs/<run-id>/<tool>/`, prefixed MCP tool names, a library-first design with thin CLI and MCP adapters, and tests against the shared synthetic `kit-fixture-app`.
