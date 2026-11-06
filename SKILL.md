---
name: localization-display-lint
description: Run Android localization boundary checks and interpret findings.
---

# Localization display lint

1. Check prerequisites with `java -jar cli/build/libs/i18n-lint.jar doctor --json`, or the MCP tool `i18n_doctor`.
2. Run `java -jar cli/build/libs/i18n-lint.jar lint --path <android-project> --json`, or MCP `i18n_lint` with the equivalent project path. Select an explicit configuration when both configuration files exist.
3. Read the findings and report artifacts; fix the relevant source or resources. Repeat the same CLI command or MCP tool to check the change. Treat `blocked`, `unsupported`, and `error` as incomplete analysis, not a pass.
4. Use `--baseline-create` or `--baseline-update` only when explicitly requested; the equivalent MCP `i18n_lint` invocation forwards the baseline option.

Agent instructions: run the CLI or equivalent MCP tool before claiming a localization scan passed; report the issue ID and location for each finding. See README for setup, supported arguments and scope.
