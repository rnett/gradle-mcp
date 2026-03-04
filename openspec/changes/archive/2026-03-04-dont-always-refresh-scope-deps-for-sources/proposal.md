## Why

Currently, `read_dependency_sources` and `search_dependency_sources` always run a Gradle build to resolve dependencies and their sources for the requested scope (project, configuration, or source set). This is done to calculate a dependency
hash and ensure the sources are up-to-date. However, this process is expensive and often unnecessary if the dependencies haven't changed.

Providing a way to skip this refresh when a cached version exists will significantly improve performance for subsequent tool calls in the same scope.

## What Changes

- Add a new `fresh` boolean argument to `read_dependency_sources` and `search_dependency_sources` tools.
- When `fresh` is `false` (the default), the tools will check if a merged source directory and index already exist for the requested scope. If they do, those cached results will be used directly, skipping the Gradle dependency resolution
  build.
- If no cached results exist for the scope, the tools will fall back to a full refresh regardless of the `fresh` argument's value.
- The tool descriptions will strongly recommend setting `fresh = true` unless the user is certain the dependency graph hasn't changed significantly since the last refresh.
- The documentation will also suggest reading the source root directory first to inspect the current library set before deciding whether a refresh is necessary.
- Both `read_dependency_sources` and `search_dependency_sources` will prepend their output with a nicely formatted local timestamp of when the source directory for that scope was last refreshed, including how much time has passed since
  then.

## Capabilities

### New Capabilities

- `cached-source-retrieval`: Ability to skip Gradle dependency resolution when cached sources for a scope are available.

### Modified Capabilities

- (None - this is a new feature extending existing tools)

## Impact

- `DependencySourceTools`: Update `ReadDependencySourcesArgs` and `SearchDependencySourcesArgs` to include the `fresh` parameter.
- `SourcesService`: Update interface methods to accept the `fresh` parameter.
- `DefaultSourcesService`: Implement logic to skip dependency resolution and hash calculation when `fresh` is `false` and a cache is available.
- Tool performance: Significant reduction in execution time for repeated calls to dependency source tools within the same project/configuration/source set scope.
