## Context

Currently, `search_dependency_sources` and `read_dependency_sources` tools resolve all dependencies within a given scope (project, configuration, source set).
Searching across a large set of dependencies can be slow and result in noisy outputs.
Users want to target a specific dependency to improve focus and performance.
Additionally, users want to quickly inspect a single dependency's update status using `inspect_dependencies` without resolving the entire project graph.

## Goals / Non-Goals

**Goals:**

- Allow users to filter dependency sources by group, name, version, or variant.
- Optimize the source resolution process to only extract/index matching dependencies when a filter is provided.
- Avoid corrupting or unnecessarily regenerating project-level cache directories (`sourcesDir`) when searching a single dependency.
- Provide clear feedback when a filter doesn't match any dependencies.
- Allow filtering in `inspect_dependencies`.

**Non-Goals:**

- Support for complex regex in the dependency filter string (simple GAV matching is enough).
- Filtering across multiple repositories (lookup is still tied to the current project's resolution).

## Decisions

- **Decision 1: Add `dependency` parameter to `SearchDependencySourcesArgs`, `ReadDependencySourcesArgs`, and `InspectDependenciesArgs`**.
    - Rationale: Consistent interface across all dependency tools.
- **Decision 2: Pass dependency filter down to `SourcesService.download*Sources` and `GradleDependencyService.getDependencies` methods**.
    - Rationale: Allows `DefaultSourcesService` to filter the dependency list *before* expensive extraction and indexing operations.
- **Decision 3: Update `dependencies-report.init.gradle.kts` to support `mcp.dependencyFilter`**.
    - Rationale: By filtering at the Gradle task level, we avoid resolving and downloading sources for dependencies the user doesn't care about. This is a critical performance win for large projects.
    - Implementation: `gatherSources` and `gatherLatestVersions` will only process components that match the provided filter.
- **Decision 4: Search single dependencies directly in `globalSourcesDir` without project-level caching**.
    - Rationale: When a `dependency` filter is provided and matches exactly one dependency, we should NOT merge its index into the project-level `sourcesDir`, as that would overwrite the project cache or require a unique cache directory for
      every possible filter. Instead, we resolve the dependency, extract it to `globalSourcesDir`, index it locally in that directory, and run the search directly against that isolated index.
    - **No Project-Level Cache for Single Dependencies**: To minimize complexity and race conditions, we will *not* cache the resolved version of the single dependency at the project level (no `.single_dependency` marker file). The overhead
      of letting Gradle resolve the filtered dependency on every tool call is acceptable (~0.5s - 1s), while the expensive extraction and indexing operations remain cached by the global `.extracted` and `.indexed` markers.
- **Decision 5: Use a simple parsing logic for the `dependency` filter**.
    - The string can be `group:artifact:version:variant`, `group:artifact:version`, `group:artifact`, or just `group`.
    - The matching will be flexible: if a single part is provided, it matches the group name exactly. If multiple parts are provided, it matches the components accordingly.
    - **Multi-Layer Filtering Strategy**: Filtering is performed in two layers:
        1. **Gradle Layer**: The init script performs 3-part (G:A:V) matching for maximum performance, skipping resolution/downloads for non-matching components.
        2. **Service Layer**: The `SourcesService` performs precise 4-part (G:A:V:Variant) matching on the resolved results to safely handle any over-fetching from the Gradle layer.
- **Decision 6: Validation in `DefaultSourcesService`**.
    - If the filtered dependency list is empty, but the original (unfiltered) list was NOT empty, return an informative error.
- **Decision 7: Global Resource Locking**.
    - To prevent cross-project collisions during extraction and indexing of shared dependencies, global locks are named using a `$group-$artifact.lock` convention within the `.locks` directory.
- **Decision 8: Simple Concurrency for Index Merging**.
    - We will lock the project-level `sourcesDir` (`sources.lock`) during merging to prevent conflicting project-level updates. We will *not* attempt to acquire shared locks on all global dependency indices simultaneously during the merge
      to avoid multi-lock complexity and coroutine deadlocks. The risk of a concurrent `forceDownload` modifying a dependency while it is being merged is extremely low; if it occurs, the merge may fail and the user can retry.
- **Decision 9: Document the `updatesChecked` flag and `[UPDATE CHECK SKIPPED]` UX**.
    - Rationale: When filtering in `inspect_dependencies`, update checks are skipped for non-matching dependencies for performance. The tool explicitly reports `[UPDATE CHECK SKIPPED]` for these dependencies to provide clear feedback.

## Risks / Trade-offs

- [Risk] → **Complexity of matching logic.**
- [Mitigation] → Keep it simple: prefer exact matches for group:artifact, and use contains or exact match for single-word queries.
- [Risk] → **User confusion over what "dependency" string to provide.**
- [Mitigation] → Provide examples in the tool description and informative error messages when no match is found.
