## ADDED Requirements

### Requirement: Scoped Group and Name Directory Layout

The system SHALL structure dependency source directories in session views using the `$group/$name` format.

#### Scenario: Browsing dependency sources

- **WHEN** resolving a dependency like `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0`
- **THEN** the system SHALL store and expose the sources at the relative path `org.jetbrains.kotlinx/kotlinx-coroutines-core`
- **AND** the version number SHALL NOT be included in the path
- **AND** the `deps/` prefix SHALL NOT be used

### Requirement: Same-Hash Index Deduplication

The system SHALL deduplicate dependency sources pointing to the same resolved artifact or same-hash index directory to avoid redundant entries in the session view.

#### Scenario: Multiple Gradle variants for the same library

- **WHEN** the project dependency graph resolves multiple Gradle variants of the same artifact
- **THEN** the system SHALL collapse these variants into a single `$group/$name` entry in the session view manifest
