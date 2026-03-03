# Source Indexing and Search

This document provides details on how the `read_dependency_sources` and `search_dependency_sources` tools index and search for symbols and files within library dependencies.

## How Indexing Works

When you first query a dependency's sources, the Gradle MCP server performs the following steps:

1. **Download Source JARs**: It uses Gradle to download the source JARs for all dependencies in the specified scope (project, configuration, or source set).
2. **Extract and Index**: The JARs are extracted, and their contents are indexed.
3. **Cache Index**: The index is cached locally to speed up subsequent searches for the same scope.

## Search Types

The `search_dependency_sources` tool supports two types of search:

### Symbol Search (`searchType: "SYMBOLS"`)

This is the default search type. It uses regex-based matching to find declarations of:

- Classes, interfaces, enums, objects, etc.
- Methods and functions.
- Properties and fields.

It is optimized for finding where a specific symbol is defined.

### Full-Text Search (`searchType: "FULL_TEXT"`)

This search type uses Apache Lucene to perform a full-text search across all files in the dependency. It is useful for:

- Finding usages of a string or constant.
- Searching for terms in comments or documentation.
- Finding code patterns that are not simple symbol declarations.

The `query` argument for full-text search supports Lucene query syntax.

## Reading Source Files

When you provide a `path` to `read_dependency_sources`, the tool:

1. **Locate File**: It finds the file within the extracted source JARs for the specified scope.
2. **Read Content**: It reads the file's content and returns it as a string.

## Limitations

- **Language Support**: Symbol search is currently optimized for Java and Kotlin.
- **Source Availability**: If a library does not publish a source JAR, the tool cannot index or read its sources.
- **Index Size**: Large projects with many dependencies might take longer to index initially.
- **Scope Specificity**: Each scope (project, configuration, or source set) is indexed separately. Ensure you are querying the correct scope.
