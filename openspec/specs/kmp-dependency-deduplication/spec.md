# Capability: kmp-dependency-deduplication

## Purpose

TBD

## Requirements

### Requirement: KMP Metadata Configuration Filtering

The system SHALL exclude Gradle configurations ending with `DependenciesMetadata` from dependency reports and source resolution processes to avoid redundant entries in multiplatform projects.

#### Scenario: Listing project dependencies in KMP

- **WHEN** a Kotlin Multiplatform project is being analyzed
- **THEN** configurations like `commonMainDependenciesMetadata` or `jvmMainDependenciesMetadata` SHALL NOT be used for resolving the primary dependency graph for sources.

### Requirement: Source Duplication Prevention in KMP

The system SHALL prevent source duplication in both the physical session view and search results while maintaining completeness for isolated platform artifacts.

#### Component: Physical Session View Isolation

- KMP platform artifacts ("target artifacts") SHALL link to a `normalized-target/` directory containing ONLY platform-specific sources **IF** their common sibling is present in the session.
- If no common sibling is present, the platform artifact SHALL link to its full `normalized/` sources.

#### Component: Search Result Isolation

- All platform artifacts SHALL index **ALL** sources (both common and platform-specific) to ensure search completeness when searched in isolation.
- Each indexed document SHALL be marked with an `isDiff` flag indicating if it is unique to the platform.
- Each index SHALL be tagged with the `sourceHash` of the artifact.
- The search engine SHALL use the project manifest to dynamically filter results:
  - **IF** a platform artifact has a common sibling in the current project view, the searcher SHALL only return results where `isDiff = true` for that artifact's index.
  - **IF** no common sibling is present, the searcher SHALL return all results from the artifact's index.

#### Scenario: Resolving Coroutines in KMP

- **WHEN** `kotlinx-coroutines-core` and `kotlinx-coroutines-core-jvm` are both resolved
- **THEN** search results for `commonMain` symbols SHALL only come from the `kotlinx-coroutines-core` index.
- **AND** search results for JVM-specific symbols SHALL come from the `kotlinx-coroutines-core-jvm` index.
- **AND** no duplicate results SHALL be returned.

#### Scenario: Searching isolated JVM artifact

- **WHEN** only `kotlinx-coroutines-core-jvm` is resolved in a pure JVM project
- **THEN** search results for `commonMain` symbols SHALL still be returned from its index, as no common sibling is present to filter them out.
