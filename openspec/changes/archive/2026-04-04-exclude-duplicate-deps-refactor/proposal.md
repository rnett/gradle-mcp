## Why

Three related improvements to the dependency sources subsystem are needed:

1. **Path layout**: The current `deps/$name/$v` format collides when two different groups share the same `name:version` (e.g., `group1:lib:1.0` and `group2:lib:1.0` both land at `deps/lib/1.0`).
2. **Remove "all sources" scope**: The "all sources" scope is inherently ambiguous in multi-project builds because version resolution is not uniquely determined across subprojects.
3. **KMP Source Set Deduplication**: In Kotlin Multiplatform projects, platform-specific artifacts (e.g., `-jvm`) are often supersets of the "common" artifact. Including both leads to duplicate sources and results in session views.

## What Changes

- Change dependency source path format from `deps/$name/$version` to `$group/$name`.
- Remove the `version` from the path as Gradle conflict resolution guarantees one resolved version per `group:name` within a project.
- **BREAKING**: Remove "all sources" scope (`resolveAndProcessAllSources` and `SourceScope.All`). Require a specific project path when resolving sources.
- **KMP Sibling Deduplication**:
    - Exclude `*DependenciesMetadata` configurations.
    - Implement a "target" source view in the CAS for KMP platform artifacts.
    - When both a common artifact and its platform sibling are resolved, the session view will link the platform artifact to its "target" sources (containing only platform-specific files), while the common artifact provides the shared
      source sets.

## Capabilities

### New Capabilities

- `dependency-source-path-layout`: Modifies the structure of dependency source directories in session views to `$group/$name`.
- `kmp-dependency-deduplication`: Prevents redundant common code in KMP projects by using target source views for platform-specific artifacts.

### Modified Capabilities

- `dependency-source-search`: Updating paths and project scope parameters to require deterministic project path inputs and reflect new result path formats.

## Impact

- **Session View Layout**: Paths change to `$group/$name`.
- **Search Efficiency**: Duplicate results for common code in KMP projects are eliminated by isolating platform-specific target sources.
- **MCP Tools**: `search_dependency_sources` and `read_dependency_sources` require `projectPath`.