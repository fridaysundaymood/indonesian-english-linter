# Localization Display Linter: implementation plan

## Status and goal

The localization display linter is implemented for Kotlin, Java and Android resources. The repository is English/Indonesian-first; locale pairs and additional shipped locales are configurable. Machine translation, layout/overflow checks and a detekt implementation are out of scope.

Stable identifiers may remain canonical in storage, sync, CSV, backups and comparisons, but built-in identifiers must be localized for display. User-defined names may pass through a localizer unchanged. Localized text must not replace canonical identifiers in data paths. Technical exception messages must not be exposed to users, while approved localized user-facing messages must not be discarded.

## Rules

These are stable Android Lint issue IDs; severity defaults can be overridden through standard lint configuration. Each rule ships documentation with examples, limitations and remediation. Support Android Lint suppressions by issue ID, including Kotlin/Java `@Suppress` or `@SuppressLint` and `//noinspection` at the applicable declaration or line, and XML `tools:ignore` on the applicable element. Follow lint's supported suppression scope rather than inventing a separate mechanism.

| ID | Default severity | Detects |
|---|---|---|
| `RawIdDisplay` | Warning | A declared identifier-domain value reaches a UI sink without passing its localizer. |
| `IdInTranslation` | Warning | A translated resource contains a canonical identifier where a localized term is required. |
| `ExceptionToUi` | Warning | A Throwable's message or `toString()` reaches a UI sink without an approved user-facing exception boundary. |
| `LocalizedExceptionSwallowed` | Warning | A throw site constructs a localized message, but the UI error mapper discards it. |
| `MissingLocaleKey` | Warning | A default resource is missing in a shipped locale or its format arguments are incompatible. |
| `LocalizedDataLeak` | Warning | A localizer's output reaches a storage, sync, export or identifier-comparison sink. |

## Configuration

The source of truth in a kit project is the `i18n` section of `android-kit.yaml`; a project outside the kit may instead use `i18n-lint.yaml`, containing the same section value. Both forms use the same published JSON Schema for the section; the kit schema references it. Unknown keys are errors, and no other tool's section is required. Select one file explicitly when both exist; do not merge or silently choose between competing configurations. The following is illustrative, not a shipped configuration:

```yaml
i18n:
  domains:
    category:
      values_from: "IdentifierCatalog.kt:categories"
      localizer: "localizedCategory"
    paymentMethod:
      values: [Cash, Card]
      localizer: "localizedPaymentMethod"
  ui_sinks: [Text, contentDescription, Snackbar.showSnackbar]
  data_sinks: [JSONObject.put, Dao, exportCsv, encode]
  user_facing_exceptions: [UserFacingException]
  locales: [values, values-in]
```

Treat the default locale as the canonical resource set and make the shipped locale list configurable. Recognize Android locale resource qualifiers, including Indonesian's legacy `values-in` form. Domain configuration must distinguish built-in identifiers from user-editable names; a configured localizer is the display boundary even when it returns a custom name unchanged.

## Implementation and interfaces

1. Implement Android Lint custom checks in a Kotlin/JVM core library using UAST for Kotlin and Java and lint's resource XML APIs for strings, plurals and arrays. Use lint's Gradle integration, baseline, SARIF reporting and IDE highlighting rather than maintaining separate implementations. The standalone CLI jar wraps the lint engine with an explicit project/source and resource input model for non-Gradle use; document required Android SDK, classpath and project metadata and report unavailable analysis as `unsupported`, not a clean scan.
2. Model domain-typed properties, configured value sources and annotated returns as sources; localizer calls as display sanitizers; and configured display and data sinks. Start with intraprocedural flows plus one level of call inlining. Support `@DisplayText` and `@IdentifierDomain` annotations for precision. Analyze exception message flows and localized-message construction and mapping; only declared user-facing exception types are approved boundaries. Report analysis limitations and avoid claiming whole-program coverage.
3. Publish the checks and Gradle integration through Maven, plus a standalone CLI jar. Provide `i18n-lint lint` and `i18n-lint doctor`, each supporting `--json`; `lint` accepts configuration, paths, rule selection, baseline and report options. Findings include stable ID, severity, file and line, snippet and suggested fix. Markdown and SARIF are artifacts; baseline creation and update are explicit actions, not automatic on failures.
4. Add a thin, client-independent MCP adapter using stdio and tools only: `i18n_lint` maps to `i18n-lint lint`, and `i18n_doctor` maps to `i18n-lint doctor`. Every MCP operation has CLI parity; optional MCP progress or elicitation must degrade gracefully. Ship plain Markdown `SKILL.md` files with frontmatter and an `AGENTS.md` snippet; every skill step names the CLI command and equivalent MCP tool, without assuming a particular agent product. Plain CI needs neither MCP nor an agent.

Each CLI `--json` response and MCP result uses the published `kit-schema` v1 JSON Schema envelope:

```json
{"schema_version":"1","tool":"i18n","operation":"lint","run_id":"<run-id>","status":"pass","summary":"No findings","evidence":[],"artifacts":[],"started_at":"<timestamp>","duration_ms":0}
```

The envelope also allows `classification` of `app`, `test`, `environment` or `config`. Status is one of `pass`, `fail`, `flaky`, `blocked`, `unsupported` or `error`: `fail` means the thing under test failed, `error` means the tool itself failed, `blocked` means a precondition or policy refused the operation, and `unsupported` means the capability is unavailable and never silently skipped. CLI exit codes are 0 for pass, 1 for fail or flaky, and 2 for error, blocked, or unsupported. Store run artifacts under `.android-kit/runs/<run-id>/i18n/`.

## Production readiness

- Support Windows, macOS and Linux. Document shell quoting and path separators on Windows, executable permissions on macOS/Linux, and Android SDK installation and environment setup on each OS.
- Declare and pin the JDK, Android SDK tools, Gradle, Android Gradle Plugin (AGP), Kotlin and Android Lint dependencies in builds and release documentation. `i18n-lint doctor` reports missing or incompatible prerequisites and concrete fixes, including SDK, Java, Gradle and lint compatibility.
- Compatibility has been verified with AGP 9.0 and 9.5 and their corresponding Android Lint 32.x APIs, including the synthetic shared fixture on AGP 9.5. Revisit the matrix when upstream lint APIs change.
- Version releases using semver and maintain a CHANGELOG, including rule and compatibility changes. CI runs unit tests on all three OSes and integration tests where feasible, including the compatibility matrix and baseline/SARIF/IDE-facing lint integration.
- Emit structured logs to stderr so JSON stdout remains machine-readable. Make interrupted runs recoverable and reruns idempotent: use distinct run directories, write reports atomically, and never mutate a baseline unless explicitly requested.
- Plan `SECURITY.md` and `CONTRIBUTING.md` for M1. Collect no telemetry.

## Milestones and acceptance

1. **M1 - resources: done.** The lint engine, schema, doctor, guidance and resource checks are implemented.
2. **M2 - exceptions: done.** Exception display and swallowed localized-message checks are implemented.
3. **M3 - domain flows: done.** Identifier display and localized data leak checks are implemented.
4. **M4 - integrations: done.** Gradle, CLI, reports and MCP integrations are implemented; synthetic fixture integration passed.

## Testing and risks

Use a synthetic fixture app, never private application history, with positive and negative cases for each rule, locale variants and format arguments. Exercise Kotlin and Java, direct flows, one-level calls, lambdas and composables, suppression scopes, output locations, severity overrides and baseline behavior. Verify standalone inputs and missing metadata produce explicit outcomes. Flow analysis can produce false positives in lambdas and composables: begin with direct flows, require annotations for wider reach, and retain baseline support rather than claiming complete program analysis.

## Kit compatibility

This tool is also a component of [android-kit](https://github.com/fridaysundaymood/android-kit/blob/main/IMPLEMENTATION_PLAN.md). The kit invokes the JVM CLI using its `--json` contract as a subprocess; it does not need a JVM embedding layer. Use the shared result schema, independent `i18n` configuration, run artifacts, prefixed MCP tools and tests against the shared synthetic `kit-fixture-app`.
