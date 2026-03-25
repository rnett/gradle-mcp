# Capability: symbol-search-enhancements

## Purpose

Defines improved field names, case-sensitivity rules, glob-style FQN wildcards, and result filtering for the SYMBOLS search mode.

## Requirements

### Requirement: Improved Symbol Search Field Names

The `SYMBOLS` search index SHALL use clear, intuitive field names to support direct Lucene queries:

- `name`: Case-sensitive analyzed simple name of the symbol.
- `fqn`: Case-sensitive analyzed fully qualified name of the symbol.

### Requirement: Universal Case-Sensitivity for Symbols

All symbol searches (simple name and FQN) SHALL be case-sensitive. This matches standard programming practices and allows users to distinguish between packages (lowercase) and declarations (typically mixed-case).

#### Scenario: Searching for a class

- **WHEN** user searches for "Project" with `searchType="SYMBOLS"`
- **THEN** system returns symbols where `name` is exactly "Project".
- **AND** system SHALL NOT return symbols whose name is "project" (lowercase).

#### Scenario: Searching for a package path

- **WHEN** user searches for "org.gradle.api" with `searchType="SYMBOLS"`
- **THEN** system returns symbols whose FQN segments match the casing (all lowercase in this case).
- **AND** system SHALL NOT return symbols if the query is "Org.Gradle.Api" and the package is lowercase.

### Requirement: Glob-Style FQN Wildcards

The `SYMBOLS` search SHALL support `*` for a single package segment and `**` for multiple segments when searching across FQNs.

#### Scenario: Single segment wildcard

- **WHEN** user searches for `fqn:org.gradle.*.Project` with `searchType="SYMBOLS"`
- **THEN** system returns symbols like `org.gradle.api.Project` but NOT `org.gradle.Project` or `org.gradle.api.internal.Project`.

#### Scenario: Multi-segment wildcard

- **WHEN** user searches for `fqn:org.**.Project` with `searchType="SYMBOLS"`
- **THEN** system returns symbols like `org.gradle.api.Project` and `org.gradle.api.internal.Project`.

### Requirement: Exclude Packages from Search Results

Search results for `SYMBOLS` SHALL only include actual declarations (classes, methods, fields, etc.) and MUST NOT include packages as standalone result items.

#### Scenario: Searching for a package path

- **WHEN** user searches for "org.gradle.api" with `searchType="SYMBOLS"`
- **THEN** system returns declarations within that package.
- **AND** the package "org.gradle.api" itself SHALL NOT be a search result.

### Requirement: Lucene Syntax for Symbol Search

The `SYMBOLS` search mode SHALL support full Lucene query syntax. The default search (when no field is specified) SHOULD include `name` and `fqn` with case-sensitive matching.
