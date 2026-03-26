## Why

In Gradle composite builds, init scripts are applied to every build in the composite (root + each included build). The `task-out.init.gradle.kts` init script registers an `OutputEventListener` and `BuildOperationListener` on the shared
logging infrastructure once per build, causing every line of task output to be emitted N times where N is the total number of builds (root + included).

## What Changes

- Guard listener registration in `task-out.init.gradle.kts` so listeners are only registered for the root Gradle build (`gradle.parent == null`), preventing duplicate registrations from included builds.

## Capabilities

### New Capabilities

<!-- none -->

### Modified Capabilities

<!-- none — this is a bug fix; expected behavior (each output line appears once) is unchanged -->

## Impact

- `src/main/resources/init-scripts/task-out.init.gradle.kts` — single-line guard added
- No API changes, no new dependencies, no spec-level behavior changes
