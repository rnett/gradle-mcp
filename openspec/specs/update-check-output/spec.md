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

### Requirement: checkUpdates is Opt-In

`InspectDependenciesArgs.checkUpdates` SHALL default to `false`, so `inspect_dependencies` performs no network-bound newer-version check unless explicitly requested. `updatesOnly=true` SHALL force update checking to `true` regardless of the explicit `checkUpdates` value. When update checking is disabled, the output SHALL contain no `[UPDATE CHECK SKIPPED]` annotation.

#### Scenario: Default omits update checking

- **WHEN** `inspect_dependencies` is called without `checkUpdates`
- **THEN** update checking SHALL be disabled (`checkUpdates` resolves to `false`)
- **AND** the output SHALL NOT contain `[UPDATE CHECK SKIPPED]`

#### Scenario: updatesOnly forces update checking

- **WHEN** `inspect_dependencies` is called with `updatesOnly=true` and `checkUpdates=false`
- **THEN** update checking SHALL run as if `checkUpdates=true`

#### Scenario: Explicit opt-in runs update checking

- **WHEN** `inspect_dependencies` is called with `checkUpdates=true`
- **THEN** update checking SHALL run

### Requirement: Canceled Dependency-Report Build Fails Explicitly

When a dependency-report build finishes with `BuildOutcome.Canceled`, the resolution SHALL fail with an explicit cancellation error rather than parsing the build's console output. The failure SHALL be unconditional: it applies regardless of whether the canceled build emitted partial structured data, because a partially emitted report is silently truncated and cannot be trusted. The guard SHALL live once in `GradleDependencyService.getDependencies`, the single dependency-report resolution funnel, so every entry point that resolves through it — `inspect_dependencies`, source-set and configuration dependency inspection, and the `download*Sources` family — fails with the same cancellation error rather than a misleading downstream error. The error SHALL state that the dependency-report build was canceled and its output cannot be trusted, and SHALL suggest re-running, mentioning the `checkUpdates` opt-in when relevant. It SHALL NOT render "No projects found." or return a partial report.

#### Scenario: Canceled dependency-report build

- **WHEN** a dependency-report build finishes with `BuildOutcome.Canceled` and emitted no structured data
- **THEN** the resolution SHALL throw an error stating the build was canceled and its output cannot be trusted
- **AND** it SHALL suggest re-running, mentioning the `checkUpdates` opt-in when relevant
- **AND** it SHALL NOT return "No projects found."

#### Scenario: Canceled build with partial structured output

- **WHEN** a dependency-report build finishes with `BuildOutcome.Canceled` after emitting some structured data
- **THEN** the resolution SHALL still throw the same explicit cancellation error
- **AND** the partially emitted report SHALL NOT be returned

#### Scenario: Sibling entry points inherit the cancellation failure

- **WHEN** a dependency-report build initiated through a `download*Sources`, source-set, or configuration entry point finishes `Canceled`
- **THEN** that entry point SHALL fail with the same explicit cancellation error rather than an empty report or a "Project not found" error

## Notes

### Output format separator

The `→` character in the flat summary format is U+2192 RIGHTWARDS ARROW. This changed from ASCII `->` in earlier versions. Any downstream parsing of the old ASCII-arrow format will not match the current output.

### updatesOnly implies checkUpdates

Calling `inspect_dependencies` with `updatesOnly=true` forces `checkUpdates=true` regardless of the explicit `checkUpdates` parameter value.

### currentVersion selection across projects

When the same `group:artifact` resolves to different versions across projects, the flat summary shows the first project's `currentVersion`. Use `inspect_dependencies` with a specific `dependency` filter to see per-project version details.
