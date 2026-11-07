# Changelog

## Unreleased

- Add Android Lint rules, a Gradle plugin, CLI and stdio MCP adapter.
- Verify the synthetic fixture integration and AGP 9.0 and 9.5 compatibility.
- Export the i18n MCP tools, configuration schema, and doctor as a side-effect-free package API.
- Isolate the CLI's injected lint checks, write run artifacts to the analyzed project, and discover the SDK from `local.properties`.
- Reduce false positives in plural format checks and identifier and localized-data flow analysis.
