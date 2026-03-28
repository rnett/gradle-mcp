# Spec: Update Check Output

## Purpose

Defines requirements for the output format of dependency update checking in `inspect_dependencies`, covering annotation scoping for `[UPDATE CHECK SKIPPED]` and the flat list format for `updatesOnly` mode.

---

## Requirements

### Requirement: UPDATE CHECK SKIPPED Only for Genuine Failures

The `[UPDATE CHECK SKIPPED]` annotation SHALL appear only for dependencies that were in scope for update checking but for which no result was returned (i.e., a genuine check failure). It SHALL NOT appear for dependencies that were
intentionally excluded from the update check scope (e.g., transitive deps when `onlyDirect=true`).

#### Scenario: No annotation for intentionally-excluded transitive deps

- **WHEN** `inspect_dependencies` is called with `onlyDirect=true` (the default) and `checkUpdates=true`
- **THEN** transitive dependencies SHALL NOT display `[UPDATE CHECK SKIPPED]` in the output

#### Scenario: Annotation shown for direct dep with failed update check

- **WHEN** `inspect_dependencies` is called with `checkUpdates=true` and a direct dependency's update check fails
- **THEN** that dependency SHALL display `[UPDATE CHECK SKIPPED]` in the output

### Requirement: Updates-Only Summary Shows Flat Dependency List

When `inspect_dependencies` is called with `updatesOnly=true`, the output SHALL present a clean flat list of dependencies with available updates, grouped by dependency ID, showing current version, latest version, and the project paths where
each dependency is used. The output SHALL NOT include per-configuration or per-source-set breakdown.

#### Scenario: Flat upgrade list without configuration noise

- **WHEN** `inspect_dependencies` is called with `updatesOnly=true`
- **THEN** the output SHALL list each upgradeable dependency once, formatted as `<group>:<artifact>: <currentVersion> → <latestVersion>`
- **THEN** the output SHALL list which project paths contain the dependency
- **THEN** the output SHALL NOT include configuration names or source set names in the update summary

#### Scenario: Empty result when all dependencies are up to date

- **WHEN** `inspect_dependencies` is called with `updatesOnly=true` and no updates are available
- **THEN** the output SHALL return `"No dependency updates found."`

#### Scenario: No annotation when update checking is disabled

- **WHEN** `inspect_dependencies` is called with `checkUpdates=false`
- **THEN** no dependency SHALL display `[UPDATE CHECK SKIPPED]` regardless of resolved state

#### Scenario: Annotation shown for transitive dep with failed check when onlyDirect=false

- **WHEN** `inspect_dependencies` is called with `onlyDirect=false` and `checkUpdates=true` and a transitive dependency's update check genuinely fails
- **THEN** that transitive dependency SHALL display `[UPDATE CHECK SKIPPED]`

#### Scenario: Annotation absent for deps excluded by dependency filter

- **WHEN** `inspect_dependencies` is called with a `dependency` filter and `checkUpdates=true`
- **THEN** dependencies that do not match the filter SHALL NOT display `[UPDATE CHECK SKIPPED]` (they were intentionally excluded from scope, not a genuine failure)

#### Scenario: updatesOnly with onlyDirect=false includes transitive deps

- **WHEN** `inspect_dependencies` is called with `updatesOnly=true` and `onlyDirect=false`
- **THEN** the flat update summary SHALL include transitive dependencies (they are present in the resolved model when `onlyDirect=false`)
- **THEN** each entry SHALL use `group:artifact` (without version) as the key, grouped across all project paths

---

## Notes

### Output format separator

The `→` character in the flat summary format is U+2192 RIGHTWARDS ARROW. This changed from ASCII `->` in earlier versions. Any downstream parsing of the old ASCII-arrow format will not match the current output.

### updatesOnly implies checkUpdates

Calling `inspect_dependencies` with `updatesOnly=true` forces `checkUpdates=true` regardless of the explicit `checkUpdates` parameter value.

### currentVersion selection across projects

When the same `group:artifact` resolves to different versions across projects, the flat summary shows the first project's `currentVersion`. Use `inspect_dependencies` with a specific `dependency` filter to see per-project version details.
