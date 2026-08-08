# Capability: build-monitoring-progress

## Purpose

Expose the phase state already tracked during a build as an immutable completed-build snapshot suitable for post-build explanation.

## ADDED Requirements

### Requirement: Completed builds retain frozen phase counts

A completed build result SHALL include frozen `phaseCounts` with exactly three buckets: `configuration`, `dependency-resolution`, and `task-execution`. Source `PhaseState` values SHALL be retained in `completedPhaseHistory` when each phase finishes and before that state is removed from the private active stack; at completion, `RunningBuild` SHALL freeze that retained history into the `Build` rather than read live progress state. Classification SHALL trim each retained phase name, match case-insensitively, and apply one top-down first-match precedence: `configuration` for `^(CONFIGURATION|configure\b.*|configuration\b.*|project configuration\b.*)$`, then `dependency-resolution` for `^(.*dependency.*resolution.*|.*resolve.*dependenc.*|resolve dependencies\b.*)$`, then `task-execution` for `^(.*task.*execution.*|.*execute.*tasks?.*|.*run.*tasks?.*|task execution\b.*)$`. Overlap SHALL resolve to the first matching bucket. Each classified retained state SHALL add its `totalItems` and `completedItems` to that bucket; unmatched names SHALL be ignored. Every bucket SHALL be emitted, and an absent bucket SHALL be `{totalItems:0, completedItems:0}`, including `dependency-resolution` when no distinct phase was observed.

#### Scenario: Build completion freezes phase state

- **WHEN** build execution reaches a completed state
- **THEN** `RunningBuild` freezes configuration, dependency-resolution, and task-execution counts from retained completed-phase history into the `Build`
- **AND** each bucket contains aggregated total and completed items
- **AND** later progress-tracker mutation cannot change the completed result

#### Scenario: Completed phases remain available after active state is dropped

- **WHEN** a phase finishes and its `PhaseState` is removed from the active stack
- **THEN** its name, total items, and completed items remain in retained completed-phase history
- **AND** build completion freezes that retained history into `phaseCounts`

#### Scenario: Multiple retained phases aggregate into one bucket

- **WHEN** multiple retained phase states classify to the same bucket
- **THEN** that bucket's `totalItems` is the sum of their total items
- **AND** that bucket's `completedItems` is the sum of their completed items

#### Scenario: Unmatched and absent phases retain the fixed output shape

- **WHEN** a retained phase name matches no bucket and no distinct dependency-resolution phase was observed
- **THEN** the unmatched phase is ignored
- **AND** `phaseCounts` still emits exactly configuration, dependency-resolution, and task-execution
- **AND** dependency-resolution is `{totalItems:0, completedItems:0}`

#### Scenario: Agent reads completed phase costs

- **WHEN** an agent inspects a completed build result
- **THEN** it can compare total and completed counts across configuration, dependency resolution, and task execution
- **AND** the counts are a frozen snapshot of completed phases at build completion
- **AND** the counts do not reflect later live progress state
