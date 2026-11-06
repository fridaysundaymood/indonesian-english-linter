# indonesian-english-linter

A planned Android localization linter for text crossing data and display boundaries. The repository is English/Indonesian-first, but locale pairs are configurable. The primary engine will be Android Lint custom checks using UAST for Kotlin and Java.

Part of [android-kit](https://github.com/fridaysundaymood/android-kit), but usable independently through Gradle, a standalone CLI jar, or an MCP client; no particular agent or other kit tool is required.

## Status

**Planning stage: there is no code to install yet.** See [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md). The first milestone covers resource checks for canonical identifiers, missing keys, and incompatible format arguments.

## Planned rules

| Stable Android Lint ID | Detects |
|---|---|
| `RawIdDisplay` | An internal identifier shown without its localizer |
| `IdInTranslation` | A translation containing a canonical identifier where a localized term is required |
| `ExceptionToUi` | A raw exception message shown to a user |
| `LocalizedExceptionSwallowed` | A localized error message discarded by an error mapper |
| `MissingLocaleKey` | A missing locale resource or incompatible format arguments |
| `LocalizedDataLeak` | Localized text entering data storage, export, sync, or identifier comparisons |

## Planned integrations

A Kotlin/JVM library, Maven/Gradle plugin and standalone CLI jar will provide Android Lint checks, baseline, SARIF and Markdown reports. A thin stdio MCP adapter will expose `i18n_lint` and `i18n_doctor`; all operations will also be available through CLI commands with `--json`. Configuration uses the `i18n` section of `android-kit.yaml`, or a standalone `i18n-lint.yaml` with the same schema when not using the kit.

## Out of scope

Machine translation, layout/overflow checks, and a detekt implementation.

## Contributing

The design is open for feedback. Use synthetic examples only: no private app code, production data, or credentials.

## License

[MIT](LICENSE)
