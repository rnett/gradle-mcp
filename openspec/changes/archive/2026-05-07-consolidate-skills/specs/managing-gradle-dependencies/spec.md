## ADDED Requirements

### Requirement: Managing Gradle Dependencies covers the full dependency lifecycle

The `managing_gradle_dependencies` skill SHALL cover the complete dependency management lifecycle:

- Auditing existing dependency graphs via `inspect_dependencies`
- Checking for updates via `updatesOnly` and `stableOnly` flags
- Discovering library versions via `lookup_maven_versions`
- Adding new dependencies (Maven Central discovery → version catalog update → `build.gradle.kts` application → verification)

#### Scenario: Agent audits project dependencies

- **WHEN** an agent needs to see a module's full dependency tree
- **THEN** the skill provides `inspect_dependencies(projectPath=":app")` with optional configuration/source set filtering

#### Scenario: Agent checks for updates

- **WHEN** an agent performs maintenance
- **THEN** the skill provides `inspect_dependencies(updatesOnly=true, stableOnly=true)` for a flat update report

#### Scenario: Agent adds a new dependency

- **WHEN** an agent needs to add a library to a module
- **THEN** the skill provides the workflow: search Maven Central → update version catalog → apply in `build.gradle.kts` → verify with `inspect_dependencies`

#### Scenario: Agent discovers library version history

- **WHEN** an agent needs to find available versions of a library
- **THEN** the skill provides `lookup_maven_versions(coordinates="group:artifact")` with pagination

### Requirement: Dependency addition workflow is included

The skill SHALL include a workflow for adding a new dependency to a Gradle module:

1. Search Maven Central using `lookup_maven_versions` to find the artifact and latest version
2. Update `gradle/libs.versions.toml` with the dependency coordinates
3. Apply the dependency to `build.gradle.kts` using the type-safe catalog accessor
4. Verify resolution with `inspect_dependencies(fresh: true)`

#### Scenario: Agent adds a plugin dependency

- **WHEN** an agent needs to verify plugin resolution
- **THEN** the skill provides `inspect_dependencies(sourceSetPath=":buildscript")` for plugin classpath verification

### Requirement: Existing dependency auditing workflows preserved

All existing `managing_gradle_dependencies` workflows SHALL be preserved:

- Dependency tree auditing with `onlyDirect: false`
- Automated update detection with `updatesOnly`
- Version conflict resolution with targeted `dependency` filter
- Build script dependency inspection via `sourceSetPath=":buildscript"`

#### Scenario: Agent investigates a version conflict

- **WHEN** a specific library has unexpected version resolution
- **THEN** the skill provides `inspect_dependencies(dependency="^(regex)$")` with the Kotlin regex pattern
