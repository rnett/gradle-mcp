---
name: gradle-library-sources
description: Searching and exploring the source code of library dependencies.
license: Apache-2.0
allowed-tools: read_dependency_sources, search_dependency_sources
metadata:
  author: rnett
  version: "3.0"
---

# Exploring Library Sources

Instructions and examples for searching and reading the source code of external library dependencies using `read_dependency_sources` and `search_dependency_sources`.

## Directives

- **Identify the scope**: You can target dependencies from the entire project, a specific project path (e.g., `:app`), a configuration (e.g., `:app:debugCompileClasspath`), or a source set (e.g., `:app:main`).
- **Search for symbols**: Use `search_dependency_sources` with the `query` argument to find specific classes, methods, or symbols. By default, this uses regex-based symbol search.
- **Full-text search**: Use `search_dependency_sources` with `searchType: "FULL_TEXT"` and a `query` to perform a Lucene-based full-text search.
- **Read specific files**: Use `read_dependency_sources` with the `path` argument to read a specific source file.
- **List files**: Use `read_dependency_sources` without a `path` to list the directory structure of the sources.
- **Force download**: Set `forceDownload: true` if you suspect the cached sources are outdated or incomplete.
- **Progressive Disclosure**: For details on how indexing and search work, refer to `references/source-indexing.md`.

## When to Use

- When you need to understand how a library's API is implemented.
- When you are looking for the correct usage of a library's class or method.
- When you need to debug an issue that might be caused by a library's behavior.
- When you want to explore the source code of a dependency to learn from its implementation.

## Workflows

### Searching for Symbols

1. Use `search_dependency_sources(query="SymbolName", projectPath=":app")`.
2. Review the search results, which include file paths, line numbers, and code snippets.
3. Identify the `path` of the most relevant source file.

### Full-Text Search

1. Use `search_dependency_sources(query="some text", searchType="FULL_TEXT", configurationPath=":app:debugCompileClasspath")`.
2. Review the search results, which include file paths, line numbers, and code snippets.

### Reading a Source File

1. Use `read_dependency_sources(path="org/junit/Assert.java", projectPath=":")`.
2. Read the content of the source file.

### Listing Files in Dependencies

1. Call `read_dependency_sources(projectPath=":app")` without a `path`.
2. Review the directory structure of the dependencies' sources.

## Examples

### Search for the `Assert` class in the root project's dependencies

```json
{
  "query": "Assert",
  "projectPath": ":"
}
```

### Full-text search for "thread-safe" in a specific configuration

```json
{
  "query": "thread-safe",
  "searchType": "FULL_TEXT",
  "configurationPath": ":app:debugCompileClasspath"
}
```

### Read a specific file from the app project's dependencies

```json
{
  "path": "kotlinx/coroutines/Job.kt",
  "projectPath": ":app"
}
```

### List the source files in a specific source set

```json
{
  "sourceSetPath": ":app:main"
}
```

## Troubleshooting

- **Symbol Not Found**: Try a different search term or ensure you are searching within the correct scope (project, configuration, or source set).
- **Source JAR Not Available**: Some libraries do not publish source JARs. In this case, the tool will not be able to index or read their sources.
- **Path Not Found**: Ensure the `path` is correct. Use the file listing or search results to find the valid path.
