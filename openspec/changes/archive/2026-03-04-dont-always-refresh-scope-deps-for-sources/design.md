## Context

Currently, the `DependencySourceTools` (`read_dependency_sources` and `search_dependency_sources`) always trigger a Gradle build via `GradleDependencyService` to resolve dependencies for the specified scope (project, configuration, or
source set). This happens every time the tool is called, even if the sources have already been downloaded, extracted, and indexed. While the extraction and indexing are cached, the initial dependency resolution is not.

## Goals / Non-Goals

**Goals:**

- Allow skipping the expensive Gradle dependency resolution when a cached merged source directory exists for the requested scope.
- Introduce a `fresh` parameter to allow users/agents to explicitly control whether to refresh the dependency list.
- Improve response time for repeated calls to dependency source tools.

**Non-Goals:**

- Intelligent cache invalidation based on project file changes (e.g., watching `build.gradle.kts`).
- Persistent caching of the `GradleDependencyReport` object itself across sessions (outside of the existing file-based cache in `SourcesDir`).

## Decisions

### 1. Add `fresh` parameter to tool arguments

We will add `fresh: Boolean = false` to `ReadDependencySourcesArgs` and `SearchDependencySourcesArgs`.

- **Rationale**: Defaulting to `false` optimizes for performance on repeated calls within the same session. Since most tool usage happens when dependencies aren't changing (during a single coding session), this provides a significant
  speedup.
- **Guidance**: The tool description will include a strong recommendation to set `fresh = true` if the project's dependency configuration has recently changed. It will also advise users to check the currently indexed libraries (by reading
  the source root) before deciding to force a refresh.

### 2. Update `SourcesService` interface

The `SourcesService` methods (`downloadAllSources`, `downloadProjectSources`, etc.) will be updated to include a `fresh` parameter.

- **Rationale**: The service needs to know if it's allowed to skip the dependency resolution step.

### 3. Conditional execution in `DefaultSourcesService`

In `DefaultSourcesService`, before calling `depService`, we will check if `fresh` is `false` and if the `SourcesDir.sources` directory already exists.

- **Rationale**: If we have the sources already and the user doesn't require a fresh list, we can return the `SourcesDir` immediately.

### 4. Fallback to fresh refresh

If `fresh` is `false` but the cache directory does not exist, the service MUST perform a full refresh.

- **Rationale**: Ensures the tool works correctly even on first call or after cache clearance.

### 5. Persistent refresh timestamp

A `.last_refresh` file will be stored in the `SourcesDir.path` containing the ISO-8601 timestamp of the last successful resolution/refresh.

- **Rationale**: This allows tools to inform the user about the age of the cached data, which is critical when the default behavior is to use potentially stale caches (`fresh=false`).
- **Implementation**: `DefaultSourcesService` will write this file upon successful completion of a refresh and expose it via the `SourcesDir` or a service method.

## Risks / Trade-offs

- **Risk**: Stale results if the user updates build files and calls the tool with the default `fresh=false`.
    - **Mitigation**: Documentation and tool descriptions will emphasize when a refresh is necessary (e.g., after `build.gradle.kts` changes). The guidance will recommend a quick directory read of the source root to see what's currently
      available.
- **Risk**: Cache inconsistency if partial data exists.
    - **Mitigation**: We already have a `.dependencies.hash` and a `.extracted` marker in the global cache. The scope-specific cache is also verified by `checkCached` (which will still be called if we do a refresh).
