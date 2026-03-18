## Why

Currently, `search_dependency_sources` and `read_dependency_sources` can filter by project, configuration, or source set, but not by a specific dependency. In large projects with many dependencies, searching across all of them can be slow
and produce noisy results. Providing a way to target a single dependency (by group, group:name, group:name:version, or group:name:version:variant) allows for more focused and efficient research.

## What Changes

- Add a `dependency` parameter to `search_dependency_sources`, `read_dependency_sources`, and `inspect_dependencies`.
- The parameter will support matching by `group`, `group:name`, `group:name:version`, or `group:name:version:variant`.
- Update the internal `resolveSources` logic to filter the resolved dependencies based on the provided `dependency` filter.
- **Performance & Storage Optimization**: When searching/reading a single dependency, the tool will directly access the dependency's global extracted source directory and its isolated Lucene index (`globalSourcesDir`), bypassing the
  expensive process of creating a project-level merged index and symlink directory.
- **Performance Optimization**: Update the `dependencies-report.init.gradle.kts` init script to support a `mcp.dependencyFilter` property. This allows the Gradle task to skip resolving/downloading sources for non-matching dependencies,
  significantly speeding up detection and indexing.
- Add validation to inform the user if the provided dependency filter does not match any resolved dependencies in the current scope.
- Update tool descriptions to include instructions on how to use the new `dependency` parameter.

## Capabilities

### New Capabilities

- `dependency-filtering`: Support for filtering dependency sources by GAV coordinates and variants during search and read operations.

### Modified Capabilities

<!-- No existing capabilities whose REQUIREMENTS are changing -->

## Impact

- `DependencySourceTools.kt`: Update `ReadDependencySourcesArgs` and `SearchDependencySourcesArgs` data classes and their corresponding tool implementations.
- `SourcesService.kt` and `DefaultSourcesService.kt`: Update methods to accept an optional dependency filter to optimize source resolution.
- `GradleDependencyService.kt`: Might be updated to support more surgical dependency extraction if beneficial.
