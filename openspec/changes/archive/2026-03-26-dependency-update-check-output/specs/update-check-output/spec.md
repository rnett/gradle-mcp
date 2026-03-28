## MODIFIED Requirements

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
