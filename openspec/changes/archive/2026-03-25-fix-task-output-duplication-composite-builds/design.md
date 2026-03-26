## Context

`task-out.init.gradle.kts` is a Gradle init script that intercepts task output by registering an `OutputEventListener` and a `BuildOperationListener` on the build's internal service registry. Gradle applies init scripts to every build in a
composite (the root build plus each included build), so these listeners are registered N times — once per build — all against the same shared logging infrastructure. Each output event therefore fires all N listeners, duplicating every
output line N times.

The `gradle.parent` property is `null` only for the root build and non-null for each included build, providing an exact guard condition.

## Goals / Non-Goals

**Goals:**

- Ensure listeners are registered exactly once per composite build, regardless of how many included builds are present.
- No change to behavior for single-build projects.

**Non-Goals:**

- Changes to how `BuildExecutionService`, `GradleProvider`, or `RunningBuild` parse the captured output — the output format (`[gradle-mcp] [taskPath] [category]: line`) is unchanged.
- Changes to any other init script (`dependencies-report`, `repl-env`, `scans`).

## Decisions

### Decision: Guard with `gradle.parent == null`

Wrap the entire listener-registration block with `if (gradle.parent == null)`. This is the canonical Gradle idiom for targeting only the root build in a composite. Alternative considered: a system property or project property flag set from
the Tooling API side — rejected because it adds coordination complexity for a problem that has a clean, zero-overhead structural solution.

## Risks / Trade-offs

- **[Risk]** If a future Gradle version changes composite build semantics for init scripts → the `gradle.parent` API is part of the public `Gradle` interface and is highly stable; no mitigation needed beyond monitoring.
- **[Trade-off]** Listeners are now only on the root build's logging manager. Output emitted solely within the scope of an included build's configuration phase (not task execution) will still be captured because all builds share the same
  logging sink. No behaviour regression expected.
