## Why

Currently, the `search_dependency_sources` tool only supports `SYMBOLS` and `FULL_TEXT` search types, which are restricted to source files (Java, Kotlin, Groovy). Users cannot search for specific files by name or path pattern (e.g.,
`AndroidManifest.xml`, `pom.xml`, `**/LICENSE`), which are often necessary when exploring dependency internals.

## What Changes

- **Add GLOB search type**: Extend the `SearchType` enum in `DependencySourceTools` to include a `GLOB` option.
- **Implement GlobSearch provider**: Create a new `SearchProvider` implementation that indexes all file paths within a dependency and supports searching them using glob patterns.
- **Update indexing service**: Register the new `GlobSearch` provider in `DefaultIndexService` to ensure file paths are indexed alongside symbols and text.
- **Enhance tool capabilities**: Update the `search_dependency_sources` tool to handle the new search type and provide relevant search results based on glob patterns.

## Capabilities

### New Capabilities

- `dependency-source-glob-search`: Provides the ability to search for files by name or path pattern using standard glob syntax across all dependency files, not just source files.

### Modified Capabilities

<!-- Existing capabilities whose REQUIREMENTS are changing (not just implementation).
     Only list here if spec-level behavior changes. Each needs a delta spec file.
     Use existing spec names from openspec/specs/. Leave empty if no requirement changes. -->

## Impact

- `dev.rnett.gradle.mcp.tools.dependencies.DependencySourceTools`: Updated enum and tool logic.
- `dev.rnett.gradle.mcp.gradle.dependencies.search.DefaultIndexService`: Registration of new provider.
- `dev.rnett.gradle.mcp.gradle.dependencies.search.GlobSearch`: New implementation of `SearchProvider`.
- `search_dependency_sources` tool schema and documentation.
