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

The `query` argument for full-text search supports standard Lucene query syntax:

- **Phrases**: Use double quotes for exact phrases (e.g., `"exact phrase"`).
- **Wildcards**: Use `*` for multiple characters and `?` for a single character.
- **Boolean Operators**: Use `AND`, `OR`, `NOT`, `+`, or `-` to combine search terms.
- **Grouping**: Use parentheses `( )` for complex logical expressions.
- **Fuzzy Search**: Use `~` for similar spellings (e.g., `test~`).
- **Proximity Search**: Use `"word1 word2"~10` to find terms within a specific distance.
- **Field Search**: Search within specific fields: `path` (file path, e.g., `path:**/Job.kt`) or `contents` (file content, default field). Note that when using wildcards on the `path` field, terms must be lowercased (e.g., `path:*job.kt`).

#### Examples

- `"thread pool" AND execute*`: Finds files containing the exact phrase "thread pool" and any word starting with "execute".
- `(public OR private) AND "void main"`: Finds files containing either "public" or "private" along with the exact phrase "void main".
- `path:*test* AND assert~`: Finds files whose path contains "test" (case-insensitive) and whose content includes a word similar to "assert".
- `"IllegalStateException"~5`: Finds occurrences where the word "IllegalStateException" appears within 5 words of another specific term (if another term was provided in the phrase, e.g., `"throw IllegalStateException"~5`).

## Reading Source Files

When you provide a `path` to `read_dependency_sources`, the tool:

1. **Locate File**: It finds the file within the extracted source JARs for the specified scope.
2. **Read Content**: It reads the file's content and returns it as a string.

## Limitations

- **Language Support**: Symbol search is currently optimized for Java and Kotlin.
- **Source Availability**: If a library does not publish a source JAR, the tool cannot index or read its sources.
- **Index Size**: Large projects with many dependencies might take longer to index initially.
- **Scope Specificity**: Each scope (project, configuration, or source set) is indexed separately. Ensure you are querying the correct scope.
