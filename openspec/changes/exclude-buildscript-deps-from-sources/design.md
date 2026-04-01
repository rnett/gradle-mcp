## Context

The current implementation of dependency source management (searching and reading) always includes buildscript (plugin) dependencies. This is often undesired as it clutters results with plugin implementation details.

## Goals / Non-Goals

**Goals:**
- Exclude buildscript dependencies from default search and read operations.
- Provide a clean, idiomatic way to search or read buildscript dependencies when desired.
- Use the existing `sourceSetPath` paradigm by introducing a virtual `buildscript` source set.
- Maintain full visibility of buildscript dependencies in `inspect_dependencies`.

**Non-Goals:**
- Removing buildscript support entirely.
- Adding new parameters to the MCP tools.

## Decisions

### 1. Default Exclusion for Source Operations
The `search_dependency_sources` and `read_dependency_sources` tools will exclude buildscript dependencies by default. This is achieved by passing the Gradle property `-Pmcp.excludeBuildscript=true` during the dependency resolution phase of these tools.

**Rationale:** Searching and reading sources are most commonly used for project-specific dependencies. Plugins often contain vast amounts of library code (e.g., Kotlin compiler, Gradle internal libraries) that users rarely want to search through unless they are specifically debugging a plugin.

### 2. Virtual `buildscript` Source Set
We will introduce a virtual source set named `buildscript`. It will be available for every project in the build.
- **Root Project**: `:buildscript`
- **Subprojects**: `:app:buildscript`, etc.

This source set will aggregate all configurations from the `buildscript { ... }` block (primarily `classpath`).

**Rationale:** Reuses the existing `sourceSetPath` parameter, making it intuitive for users to "scope" their search to plugin dependencies.

### 3. Init Script Implementation
The `dependencies-report.init.gradle.kts` will be updated with the following logic:
- **Exclusion**: If `-Pmcp.excludeBuildscript=true` is provided, `buildscriptConfigs` will be emptied **unless**:
    - `mcp.sourceSet` is `"buildscript"`
    - OR `mcp.configuration` explicitly starts with `"buildscript:"`
- **Synthesis**: The `outputSourceSets` function will always emit a `SOURCESET: buildscript | <configs>` line for each project. This ensures the virtual source set is visible to the MCP server's parser and can be targeted by name.

### 4. Service Layer Propagation
- `GradleDependencyService`: `downloadAllSources` and `downloadProjectSources` will explicitly pass `mcp.excludeBuildscript=true`.
- `GradleDependencyService`: `getSourceSetDependencies` will be updated to handle the `buildscript` name without attempting to look it up in the real Gradle `SourceSetContainer`.

## Migration Plan

Existing users who rely on buildscript dependencies being included in global searches must update their tool calls:
- **Before**: `search_dependency_sources(query: "PluginClass")`
- **After**: `search_dependency_sources(query: "PluginClass", sourceSetPath: ":buildscript")`

## Risks / Trade-offs

- **Risk**: Confusion over why `inspect_dependencies` shows buildscript deps but `search_dependency_sources` doesn't find them by default.
- **Mitigation**: Documentation in the tool descriptions and skills will explicitly mention that `sourceSetPath: ":buildscript"` is required to search plugin sources.
- **Risk**: Potential name collision if a user has a real source set named `buildscript`.
- **Mitigation**: This is extremely unlikely in idiomatic Gradle projects, but the virtual source set will take precedence in our reporting.
