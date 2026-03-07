## Why

Currently, the Gradle MCP tools only expose project dependencies from the main configurations (e.g., `implementation`, `testImplementation`). Dependencies declared in the `buildscript` block, which are used by Gradle plugins and the build
scripts themselves, are not visible. This makes it impossible for agents to research plugin internals, debug build logic issues, or audit the full build-time dependency graph.

## What Changes

- **Build Classpath Integration**: Buildscript dependencies will be automatically included in the dependency report and source tools.
- **Naming Convention**: Buildscript configurations will be reported with a `buildscript:` prefix (e.g., `buildscript:classpath`) to distinguish them from project configurations.
- **Unified Source Access**: The source code for buildscript dependencies will be automatically extracted and indexed, making them searchable via `search_dependency_sources` and readable via `read_dependency_sources`.
- **Init Script Update**: Modify the `mcpDependencyReport` task in `dependencies-report.init.gradle.kts` to traverse and output `project.buildscript.configurations`.

## Capabilities

### New Capabilities

- `build-classpath-support`: Provides authoritative access to the build-time classpath (plugins and build script dependencies) through existing dependency and source tools.

### Modified Capabilities

<!-- None, this is purely additive functionality to existing tools. -->

## Impact

- **Tool APIs**: No new parameters are added. Existing tools (`inspect_dependencies`, `read_dependency_sources`, `search_dependency_sources`) will now see and act on `buildscript:` prefixed configurations.
- **Init Script**: The `dependencies-report.init.gradle.kts` will be updated to extract buildscript configurations.
- **Models**: `GradleDependencyReport` and related models will now contain buildscript configuration data.
