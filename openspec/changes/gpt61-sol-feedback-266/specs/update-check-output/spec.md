## ADDED Requirements

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
