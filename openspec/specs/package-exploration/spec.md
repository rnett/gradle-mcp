# Capability: package-exploration

## Purpose

Enables index-backed package content listing and path resolution for dependency sources, supporting dot-separated package navigation backed by the symbol index.

## Requirements

### Requirement: Index-Backed Package Content Listing

The `read_dependency_sources` tool SHALL support listing the contents of a specific package by querying the symbol index. This ensures that the results are accurate even in situations where the directory structure does not strictly match
the package name (e.g., in Kotlin).

#### Scenario: Listing a package

- **WHEN** user calls `read_dependency_sources` with a dot-separated package path (e.g., `org.gradle.api`)
- **THEN** system queries the index for symbols directly within that package.
- **AND** system queries the index for immediate sub-packages by examining all symbols whose `packageName` starts with the provided path.
- **AND** system returns a listing of these symbols and sub-packages.

#### Scenario: Listing a non-existent package

- **WHEN** user calls `read_dependency_sources` with a dot-separated path that contains no symbols or sub-packages in the index
- **THEN** system returns an error indicating the package was not found.

### Requirement: Path Resolution Priority

When a `path` is provided to `read_dependency_sources`, the tool SHALL first attempt to resolve it as a literal file or directory on the file system. If no match is found, it SHALL attempt to resolve it as a dot-separated package name.

#### Scenario: Path is a dot-separated package

- **WHEN** user calls `read_dependency_sources` with `path="org.gradle.api"`
- **THEN** system resolves this as a package by querying the index, regardless of whether a matching directory exists.
