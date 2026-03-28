## Why

The `inspect_dependencies` update-check output has two UX problems: (1) transitive dependencies are annotated with `[UPDATE CHECK SKIPPED]` even when they were intentionally excluded from update checking (not a failure), creating noise that
misleads users; (2) the `updatesOnly=true` output includes per-configuration and per-source-set columns that obscure the actionable information (which dep needs upgrading and in which project).

## What Changes

- **Suppress `[UPDATE CHECK SKIPPED]` for intentionally-excluded deps**: When `onlyDirect=true` (the default), transitive deps are excluded from update checking by design. The annotation should only appear when a dep was *in scope* for
  update checking but the check genuinely failed. Fix: in the init script, add a private `isUpdateCheckComplete(group, name)` helper that computes the `updatesChecked` value inline — see design.md D1 for the final implementation approach (
  which differs from the originally-described pre-processing mutation).
- **Simplify `updatesOnly` output**: Remove per-configuration and per-source-set columns from `formatUpdatesSummary`. Show a flat list of upgradeable dependencies: `group:artifact: current → latest` with the project paths where each is
  used.

## Capabilities

### New Capabilities

<!-- None -->

### Modified Capabilities

- `inspect_dependencies`: The `updatesOnly=true` output format is simplified. The `[UPDATE CHECK SKIPPED]` annotation is scoped to genuine failures only.

## Impact

- `src/main/resources/init-scripts/dependencies-report.init.gradle.kts`: add `isUpdateCheckComplete` helper to `McpDependencyReportRenderer`; replace duplicated two-liner in both render paths.
- `src/main/kotlin/.../tools/dependencies/GradleDependencyTools.kt`: update `formatUpdatesSummary` to remove configuration/source-set columns.
- `src/main/kotlin/.../tools/dependencies/GradleDependencyTools.kt`: update tool description prose and parameter `@Description` annotations.
- `skills/managing_gradle_dependencies/SKILL.md`: update `[UPDATE CHECK SKIPPED]` troubleshooting entry and update-check workflow description.
- `docs/tools/PROJECT_DEPENDENCY_TOOLS.md`: regenerated from updated annotations via `:updateToolsList`.
