## MODIFIED Requirements

### Requirement: Intelligent Auto-Expansion

The `query_build` tool SHALL automatically expand to a detailed view if exactly one component (task, test, failure, problem) matches the provided query. For task and test queries with a non-empty `query`, an exact match within the filtered candidate set SHALL take precedence over longer prefix matches: `path == query` for TASKS and `fullName == query` for TESTS. When an exact match exists, auto-expansion SHALL operate only on the exact-match subset; when it does not, the current prefix behavior applies unchanged. TESTS may contain multiple executions of the same exact test, so the existing `testIndex` selection still applies within the exact-match subset. Listing paths, no-match diagnostics, `taskPath` prefix filtering, and exact-ID FAILURES/PROBLEMS handling SHALL be unchanged.

#### Scenario: Unique task path match

- **WHEN** user calls `query_build(kind="TASKS", query=":app:assemble")`
- **THEN** system returns full task details, including duration and output status, instead of a summary list

#### Scenario: Multiple matches

- **WHEN** user calls `query_build(kind="TASKS", query=":app:")` and multiple tasks exist
- **THEN** system returns a summary list with a hint to refine the query for details

#### Scenario: Exact task match expands despite longer-prefix siblings

- **WHEN** user calls `query_build(kind="TASKS", query=":app:assemble")` and both `:app:assemble` and a longer path beginning with it (e.g. `:app:assembleRelease`) exist
- **THEN** system returns full task details for the exact match `:app:assemble`
- **AND** it returns no summary list and no "Note:" line for the longer-prefix sibling

#### Scenario: Exact test name expands despite longer-prefix siblings

- **WHEN** user calls `query_build(kind="TESTS", query="com.example.FooTest.testBar")` and both that full name and a longer name beginning with it exist
- **THEN** auto-expansion operates only on the exact-match subset
- **AND** the existing `testIndex` selection applies when the exact test has multiple executions

#### Scenario: Ambiguous prefix still lists when no exact match exists

- **WHEN** user calls `query_build(kind="TASKS", query=":app:")` with no exact match and multiple prefix matches
- **THEN** system returns a summary list with a hint to refine the query for details

### Requirement: Completed build queries expose phase counts

Completed build output SHALL include `phaseCounts` with exactly three buckets: `configuration`, `dependency-resolution`, and `task-execution`. Classification SHALL trim each retained phase name, match case-insensitively, and apply one top-down first-match precedence: `configuration` for `^(CONFIGURATION|configure\b.*|configuration\b.*|project configuration\b.*)$`, then `dependency-resolution` for `^(.*dependency.*resolution.*|.*resolve.*dependenc.*|resolve dependencies\b.*)$`, then `task-execution` for `^(.*task.*execution.*|.*execute.*tasks?.*|.*run.*tasks?.*|task execution\b.*)$`. Overlap SHALL resolve to the first matching bucket. Each classified retained `PhaseState` SHALL add its `totalItems` and `completedItems` to that bucket, so repeated phases sum; unmatched names SHALL be ignored. Every bucket SHALL be emitted, and an absent bucket SHALL be `{totalItems:0, completedItems:0}`. In particular, `dependency-resolution` SHALL be 0/0 when no distinct phase was observed. These values MUST be detached from live mutable progress state.

Rendering SHALL emit every bucket: a bucket with `totalItems == 0` SHALL render as `"  <bucket>: <completedItems> completed"` — a bare count, never a `/0` ratio — while a bucket with `totalItems > 0` SHALL keep the `"<completedItems>/<totalItems> completed"` ratio. A raw `completed > total` count with `total > 0` continues to render the ratio unchanged.

#### Scenario: Dashboard describes completed build work

- **WHEN** an agent queries DASHBOARD output for a completed build
- **THEN** the output includes total and completed item counts for configuration, dependency resolution, and task execution
- **AND** the counts are a frozen snapshot of the completed build

#### Scenario: Console output describes completed build work

- **WHEN** an agent inspects console-oriented output for a completed build
- **THEN** the output includes the frozen phase counts

#### Scenario: Zero-total bucket renders a count, not a ratio

- **WHEN** a completed build has a `configuration` bucket with `totalItems=0` and `completedItems=3`
- **THEN** the output SHALL contain `configuration: 3 completed`
- **AND** it SHALL NOT contain `3/0`

#### Scenario: Absent bucket renders a zero count

- **WHEN** a completed build has no `dependency-resolution` phase and the bucket is `{totalItems:0, completedItems:0}`
- **THEN** the output SHALL contain `dependency-resolution: 0 completed`
