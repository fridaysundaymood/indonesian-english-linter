# indonesian-english-linter

**Catch the localization bugs that translation files don't: wrong text crossing the line between data and display.**

Translating strings is only half the job. Apps also break when an internal ID like `Cash` reaches the screen untranslated, when translated text gets saved into the database or a CSV export, or when a raw exception message is shown to the user. This linter traces those flows in Kotlin/Compose code and reports them before release. English and Indonesian come first; any locale pair can be configured.

Part of [android-kit](https://github.com/fridaysundaymood/android-kit), a toolkit combining five tools for end-to-end Android development. Works on its own; no other kit tool required.

## Status

> **Planning stage: there is no code to install yet.** This repo holds the design in [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md). Star or watch it to follow progress. First milestone: resource checks: canonical IDs inside translations, missing keys, and mismatched format arguments across locales.

## Who it's for

- Android teams shipping in more than one language, especially English and Indonesian.
- Apps that store stable identifiers (categories, payment types, statuses) and display them translated.

## Planned rules

| Rule | Catches |
|---|---|
| L1 `raw-id-display` | An internal identifier shown in UI without going through its translator |
| L2 `id-in-translation` | A translation that contains an internal identifier instead of the translated word |
| L3 `exception-to-ui` | An exception message shown directly to the user |
| L4 `localized-exception-swallowed` | A translated error message that the error handler throws away |
| L5 `missing-locale-key` | A string missing in a shipped language, or with incompatible format arguments |
| L6 `localized-data-leak` | Translated text written to storage, sync, export, or used in ID comparisons |

## Planned integrations

CLI, Gradle task with a baseline (fail only on new findings), SARIF and Markdown reports, and an MCP tool for AI agents. Configuration lives in a project `i18n-lint.yaml`.

## Out of scope

Machine translation and layout/overflow checks.

## Contributing

The design is open for feedback. Open an issue to question scope, suggest a feature, or discuss an approach before sending a pull request. Use synthetic examples only: no private app code, production data, or credentials.

## License

[MIT](LICENSE)
