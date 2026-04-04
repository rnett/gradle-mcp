# Capability: kmp-dependency-deduplication

## Purpose

TBD

## Requirements

### Requirement: KMP Metadata Configuration Filtering

The system SHALL exclude Gradle configurations ending with `DependenciesMetadata` from dependency reports and source resolution processes to avoid redundant entries in multiplatform projects.

#### Scenario: Listing project dependencies in KMP

- **WHEN** a Kotlin Multiplatform project is being analyzed
- **THEN** configurations like `commonMainDependenciesMetadata` or `jvmMainDependenciesMetadata` SHALL NOT be used for resolving the primary dependency graph for sources.

### Requirement: Target Source Isolation for KMP Platform Artifacts

The system SHALL isolate platform-specific ("target") sources from common sources for KMP platform artifacts to prevent duplication when both are resolved.

#### Scenario: Resolving Coroutines in KMP

- **WHEN** `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0` and `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0` are both resolved in the same project graph
- **THEN** the session view SHALL link the JVM artifact to a "target" source directory containing ONLY the JVM-specific sources
- **AND** the session view SHALL link the common artifact to its full source directory containing the common source sets
- **AND** no source file SHALL appear twice in the search results or session view.
