## ADDED Requirements

### Requirement: Single dependency filtering

The system SHALL support filtering dependencies by providing a `dependency` string in `search_dependency_sources`, `read_dependency_sources`, and `inspect_dependencies`.

#### Scenario: Filter by group:name

- **WHEN** the `dependency` parameter is set to `org.jetbrains.kotlinx:kotlinx-coroutines-core`
- **THEN** the system SHALL only include sources/info from dependencies matching that group and name.

#### Scenario: Filter by group

- **WHEN** the `dependency` parameter is set to `org.jetbrains.kotlinx`
- **THEN** the system SHALL include sources/info from all dependencies in that group.

#### Scenario: Filter by version

- **WHEN** the `dependency` parameter is set to `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3`
- **THEN** the system SHALL only include sources/info from that specific version.

#### Scenario: Filter by variant

- **WHEN** the `dependency` parameter is set to `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3:jvm`
- **THEN** the system SHALL only include sources/info from that specific version and variant.

#### Scenario: No matches

- **WHEN** the `dependency` parameter does not match any resolved dependencies in the current scope (project, configuration, or source set)
- **THEN** the system SHALL return an informative error message indicating that no matching dependencies were found in the specified scope.

#### Scenario: Validation of dependency string

- **WHEN** the `dependency` parameter is provided
- **THEN** the system SHALL parse it into its components (group, name, version, variant) to perform efficient matching against the resolved dependency graph.

### Requirement: Optimized Storage for Single Dependency

When searching or reading a single dependency's sources, the system SHALL directly access the dependency's globally extracted index and source directory without merging it into the project-level cache.

#### Scenario: Search single dependency avoids project cache

- **WHEN** `search_dependency_sources` is called with a `dependency` parameter
- **THEN** the system SHALL query the index located in the `globalSourcesDir` for that specific dependency
- **AND** the system SHALL NOT modify or create a merged index in the project's `sourcesDir`
- **AND** the system SHALL NOT cache the resolved version of the dependency at the project level (e.g., no `.single_dependency` marker file), relying on Gradle's fast filtered resolution instead.

### Requirement: Direct Dependencies Only for Filter

The `dependency` filter SHALL only match direct or specific transitive dependencies targeted by the filter, and SHALL NOT automatically include their transitive dependencies unless they also match the filter.

#### Scenario: Transitives are not included

- **WHEN** `search_dependency_sources` is called with `dependency="org.jetbrains.kotlinx:kotlinx-coroutines-core"`
- **THEN** the system SHALL ONLY include sources for `kotlinx-coroutines-core`
- **AND** the system SHALL NOT include sources for `kotlin-stdlib` or other dependencies of `kotlinx-coroutines-core`.
