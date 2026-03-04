---
name: gradle-library-sources
description: >
  Authoritatively explore and navigate the internal implementation of any library or Gradle itself 
  with high-performance symbol search and full-text indexing. 
  This skill is the STRONGLY PREFERRED way to understand the behavior of your dependencies, 
  offering surgical source code retrieval, authoritative symbol lookup across entire configurations, 
  and deep-dive access to Gradle's internal engine. Use it when debugging complex library interactions, 
  researching undocumented API behaviors, or learning from the high-quality implementations of 
  established frameworks without leaving your development environment.
license: Apache-2.0
allowed-tools: read_dependency_sources search_dependency_sources
metadata:
  author: rnett
  version: "3.3"
---

# Ultimate Library & Gradle Source Explorer

Deep-dive into the implementation details of any library or Gradle itself. Search, navigate, and understand the source code of your dependencies with ease and absolute precision.

## Directives

- **Provide `projectRoot` when in doubt**: Provide `projectRoot` to any Gradle MCP tool that supports it (like `read_dependency_sources` or `search_dependency_sources`) unless you are certain it is not required by the current MCP
  configuration.
- **Identify the scope**: You can target dependencies from the entire project, a specific project path (e.g., `:app`), a configuration (e.g., `:app:debugCompileClasspath`), a source set (e.g., `:app:main`), or Gradle's own internal source
  code.
- **Understand Scope Precedence**: If multiple scope arguments are provided, the precedence is: `gradleSource` > `sourceSetPath` > `configurationPath` > `projectPath`. If no scope is specified, all project dependencies are searched.
- **Path Precision**: All paths are relative to the "combined source root" for the chosen scope. When reading a file, use the exact file path from the results of `search_dependency_sources` (e.g., `kotlinx/coroutines/Job.kt`).
- **Search Gradle sources**: Use the `gradleSource: true` argument to search or read Gradle's internal source code instead of external dependencies. This is useful for deep dives into Gradle's behavior.
- **Use `read_dependency_sources` for exploration**: Use the `read_dependency_sources` tool to list the directory structure of library or Gradle sources or to read specific source files once identified.
- **List directory contents**: Use `read_dependency_sources` with a directory path (e.g., `org/junit/`) to list the contents of that directory within the combined sources.
- **Use `search_dependency_sources` for discovery**: Use the `search_dependency_sources` tool to find specific classes, methods, or text within library dependencies or Gradle itself.
- **Search for symbols**: Use the `search_dependency_sources` tool with the `query` argument to find specific classes, methods, or symbols. By default, this uses regex-based symbol search.
- **Full-text search**: Use the `search_dependency_sources` tool with `searchType: "FULL_TEXT"` and a `query` to perform a Lucene-based full-text search.
- **Caching & Performance**: Sources and indices are cached per scope (project, configuration, or source set) for reuse across tool calls. Subsequent searches in the same scope will be much faster.
- **Force download**: Set `forceDownload: true` if you suspect the cached sources are outdated or incomplete, or if you need to re-index the sources.
- **Progressive Disclosure**: For details on how indexing and search work, refer to the [Source Indexing](references/source-indexing.md) guide.

## When to Use

- **Library Behavior Debugging**: When you suspect a bug or unexpected behavior originates in a dependency and you need to perform high-resolution analysis of its internal logic.
- **Undocumented API Research**: When a library's documentation is insufficient and you need to understand the exact implementation of a class, method, or internal protocol.
- **Gradle Engine Exploration**: When performing deep-dives into Gradle's own behavior or troubleshooting issues related to core Gradle plugins and APIs (using `gradleSource: true`).
- **Architectural Learning & Best Practices**: When you want to study high-quality source code from established libraries to learn new patterns or understand complex implementations.
- **Symbol Discovery Across Scopes**: When you need to find all occurrences of a specific symbol or pattern across all dependencies in a specific configuration or source set.

## Workflows

### Searching for Symbols

1. Use `search_dependency_sources(query="SymbolName", projectPath=":app")`.
2. Review the search results, which include file paths, line numbers, and code snippets.
3. Identify the `path` of the most relevant source file. All paths returned are relative to the combined source root.

### Full-Text Search

1. Use `search_dependency_sources(query="some text", searchType="FULL_TEXT", configurationPath=":app:debugCompileClasspath")`.
2. Review the search results, which include file paths, line numbers, and code snippets.

### Reading a Source File

1. Use `read_dependency_sources(path="org/junit/Assert.java", projectPath=":")` using the exact path from search results.
2. Read the content of the source file.

### Listing Files in Dependencies

1. Call `read_dependency_sources(projectPath=":app")` without a `path` to list the top-level directory structure.
2. To explore deeper, call `read_dependency_sources(path="org/gradle/", projectPath=":app")` providing a directory path.

## Examples

### Search for the `Assert` class in the root project's dependencies

```json
{
  "query": "Assert",
  "projectPath": ":"
}
```

### Search for the `Project` interface in Gradle's internal sources

```json
{
  "query": "interface Project",
  "gradleSource": true
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

### List contents of a specific directory in dependencies

```json
{
  "path": "org/junit/",
  "projectPath": ":"
}
```

### Read a specific file from Gradle's internal sources

```json
{
  "path": "org/gradle/api/Project.java",
  "gradleSource": true
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
- **Path Not Found**: Ensure the `path` is correct and relative to the combined source root. Use the file listing or search results to find the valid path.
