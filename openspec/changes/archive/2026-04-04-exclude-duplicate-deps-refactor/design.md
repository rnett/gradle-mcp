## Context

Three related improvements to the dependency sources subsystem are needed:

1. **Path layout**: The current `deps/$name/$v` format collides when two different groups share the same `name:version` (e.g., `group1:lib:1.0` and `group2:lib:1.0` both land at `deps/lib/1.0`).
2. **Remove "all sources" scope**: Ambiguity in multi-project builds requires a specific project path (or root `:`) when resolving sources.
3. **KMP Deduplication**: KMP platform artifacts (e.g., `-jvm`, `-js`) are supersets of their "common" artifact. This leads to redundant search results and wasted space/indexing.

## Goals / Non-Goals

**Goals:**

- Eliminate path collisions using `$group/$name` format.
- Ensure deterministic resolution by requiring a project path.
- **Prevent duplicate sources in KMP projects while ensuring all platform-specific code is present once.**
- Make directory structures easier for agents to navigate.

## Decisions

### 1. Path Layout Refactor

- **Change `relativePrefix` to `"$group/$name"`**: This scopes libraries under their group.
- **Collision Safety**: Fixes collisions between different groups with same library name.
- **Deduplication Logic**: Maintain `distinctBy { it.relativePrefix }`. Safe because variants of the same artifact point to identical sources.
- **Version Omission**: Omitted as Gradle conflict resolution guarantees one version per project.

### 2. Remove "All Sources" Scope

- **Delete `SourceScope.All` and `resolveAndProcessAllSources`**: Replaced by project-specific calls.
- **Enforce `projectPath` requirement**: Tool arguments will throw `IllegalArgumentException` if `projectPath` is null.

### 3. KMP Target Sources (CAS & View Logic)

- **Identify KMP platform siblings**:
    - *Relationship Detection*: We will use Gradle's **"Available-At"** metadata found in the `ResolutionResult` graph.
    - The "common" artifact (root module) contains variants that point to **external variants** owned by the platform-specific components (e.g., `kotlinx-coroutines-core` has a `jvmRuntimeElements` variant whose `externalVariant.owner` is
      `kotlinx-coroutines-core-jvm`).
    - We will build a map of `platformComponentId -> commonComponentId` by scanning all components and their variants for these external pointers.
- **CAS `normalized-target` Directory**:
    - During normalization, if a common sibling is identified via the metadata map, create a `normalized-target` directory in the CAS.
    - `normalized-target` only contains files from source sets that are NOT present in the common artifact (determined by source set name subtraction).
- **Session View Junction Selection**:
    - If `commonDep` is present in the session view, link the platform dependency to its `normalized-target` CAS directory.
    - If `commonDep` is NOT present, link the platform dependency to its full `normalized` directory (this ensures all code is available even when platform artifacts are used in isolation).
- **Init Script & Dependency Resolution**: Include `*DependenciesMetadata` configurations but filter them out from standard user-facing reports as internal (`isInternal`). `SourcesService` requests these explicitly to properly detect common
  dependencies.

## Resolutions to Review Findings (2026-04-03)

Following the faceted review, several critical flaws in the original design were identified and resolved:

1. **Missing E2E Search Validation (Finding 1)**: Added `KmpSearchIntegrationTest` to verify that common symbols (e.g., `kotlinx.serialization.json.Json`) are deduplicated and returned exactly once from the common artifact.
2. **KMP Deduplication Logic Conflict (Finding 2)**: Removed the logic in `dependencies-report.init.gradle.kts` that skipped downloading sources for common artifacts. Common artifacts are now correctly downloaded and processed to enable
   target isolation diffing.
3. **Ghost Dependencies & isInternal Leak (Findings 3 & 10)**: The init script now always outputs the `CONFIGURATION:` header, including the `isInternal` flag. Internal configurations are filtered out in `GradleDependencyService` unless
   explicitly requested via `includeInternal = true`. This prevents the ghost dependency leak and ensures `SourcesService` can access metadata configurations for KMP common dependencies.
4. **Search Index Target Isolation Failure (Finding 5)**: Updated `SourcesService` to index from the `normalized-target` directory instead of the full `normalized` superset when a common sibling is present.
5. **Concurrency Bug & Deadlock Risk (Finding 6)**: Modified `SourcesService` to sort dependencies so that those *without* a common sibling (the common artifacts themselves) are processed first, preventing worker starvation and deadlocks
   where platform artifacts were waiting on unstarted common artifacts.

## Risks / Trade-offs

- **Risk: Breaking existing path assumptions in tests.**
    - *Mitigation*: Update all integration tests to use the new path format and `resolveAndProcessProjectSources`.
- **Risk: Dependency resolution order.**
    - *Mitigation*: `processCasDependency` will wait for `commonDep` to complete its normalization before calculating the target sources.
- **Risk: Reliable metadata availability.**
    - *Mitigation*: The "Available-At" metadata is a standard part of Gradle's module metadata for KMP. Our investigation confirmed it is explicitly exposed in the Tooling API's `ResolutionResult`.
