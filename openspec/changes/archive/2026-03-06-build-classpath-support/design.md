## Context

The Gradle MCP currently exposes project dependencies through a custom `McpDependencyReportTask` in an init script. This task utilizes Gradle's `AbstractDependencyReportTask` as a base, which by default only considers configurations in
`project.configurations`. Buildscript dependencies (declared in `buildscript { dependencies { ... } }`) reside in `project.buildscript.configurations` and are currently ignored.

## Goals / Non-Goals

**Goals:**

- Provide access to `buildscript` configurations and their dependencies in `inspect_dependencies`.
- Ensure build classpath dependencies are searchable via `search_dependency_sources` and readable via `read_dependency_sources`.
- Minimize API friction by automatically including build classpath data with a `buildscript:` prefix.

**Non-Goals:**

- Modifying how Gradle resolves buildscript dependencies.
- Supporting buildscript dependencies for projects that don't have a `buildscript` block (though all Gradle projects have one).

## Decisions

- **Decision 1: Automatic Inclusion with `buildscript:` Prefix**
    - **Rationale**: This follows the user's suggestion of "always reported" but provides clear namespacing. It avoids a new parameter in tool schemas and matches Gradle's internal naming convention while keeping it distinct from project
      configurations.
    - **Alternatives**: Using a separate flag. **Rejected** to simplify the API.

- **Decision 2: Manual iteration over `buildscript.configurations` in `McpDependencyReportTask`**
    - **Rationale**: `AbstractDependencyReportTask` is hardcoded to use `getProject().getConfigurations()`. To include buildscript configurations, we will manually iterate over `project.buildscript.configurations` and invoke the renderer's
      `startConfiguration`, `render`, and `completeConfiguration` methods. We will prefix configuration names with `buildscript:`.
    - **Alternatives**: Creating a separate task for build classpath. **Rejected** to keep the response consolidated in a single tool call.

- **Decision 3: Unified Source Extraction**
    - **Rationale**: Update `GradleDependencyService` and `SourcesService` to automatically include dependencies from `buildscript:` prefixed configurations in the source extraction and indexing pipeline.

## Risks / Trade-offs

- **[Risk]** → Longer resolution/extraction times as the build classpath is now always processed.
- **[Mitigation]** → Buildscript dependencies are usually few and already cached by Gradle. The impact on overall task duration is expected to be minimal.

- **[Risk]** → Resolution failures in buildscript configurations.
- **[Mitigation]** → Use `lenientConfiguration` or handle exceptions within the init script to ensure the task doesn't fail the entire build if a buildscript dependency is unresolvable.
