# Changelog

## Unreleased

- Add Android Lint rules, a Gradle plugin, CLI and stdio MCP adapter.
- Exercise the synthetic fixture integration and AGP 9.0 and 9.5 in CI; compatibility is not yet claimed as verified.
- Export the i18n MCP tools, configuration schema, and doctor as a side-effect-free package API.
- Isolate the CLI's injected lint checks, write run artifacts to the analyzed project, and discover the SDK from `local.properties`.
- Reduce false positives in plural format checks and identifier and localized-data flow analysis.
- Restrict raw identifier findings to declared domain provenance and expose the matched domain and provenance chain in CLI evidence.
- Restrict baseline and configuration paths, validate Gradle configuration against the schema, and document that linting executes project Gradle scripts.
- Prepare the MCP adapter for public npm distribution with its CLI jar bundled.
