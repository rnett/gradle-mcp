[//]: # (@formatter:off)

# Project Dependency Source Tools

Tools for searching and inspecting source code of Gradle dependencies.

## read_dependency_sources

The authoritative tool for reading source files and exploring directory structures from the combined source code of all external library dependencies or Gradle's internal engine.
It provides surgical access to the implementation details of your project's ecosystem, cached for high-performance retrieval.

### Authoritative Features
- **Deep Source Exploration**: Navigate the entire directory structure of your project's dependencies or Gradle's own source code.
- **Surgical File Retrieval**: Read the full content of any identified source file. Ideal for verifying library behavior or researching undocumented APIs.
- **Managed Lifecycle**: Sources and indices are cached per scope (project, configuration, or source set). Subsequent reads are nearly instantaneous.
- **Contextual Precedence**: Precisely target your search using `gradleSource`, `sourceSetPath`, `configurationPath`, or `projectPath`.

### Common Usage Patterns
- **Explore App Dependencies**: `read_dependency_sources(projectPath=":app")`
- **Read Gradle Source**: `read_dependency_sources(path="org/gradle/api/Project.java", gradleSource=true)`
- **List Library Structure**: `read_dependency_sources(path="org/junit/", configurationPath=":app:testCompileClasspath")`

To find specific classes or methods across all dependencies before reading, use the `search_dependency_sources` tool.
For detailed source navigation strategies, refer to the `gradle-library-sources` skill.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The absolute path to the project root directory. Defaults to the current workspace root. Always provide this if you are working in a multi-root workspace to ensure the correct project is targeted."
    },
    "projectPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "The project path to target (e.g., ':', ':app'). If null, all project dependencies are included. This has the lowest precedence."
    },
    "configurationPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Target a specific configuration (e.g., ':app:debugCompileClasspath'). If set, projectPath is ignored. Higher precedence than projectPath."
    },
    "sourceSetPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Target a specific source set (e.g., ':app:main'). If set, projectPath and configurationPath are ignored. Higher precedence than configurationPath."
    },
    "gradleSource": {
      "type": "boolean",
      "description": "If true, targets Gradle Build Tool's own authoritative source code instead of project dependencies. This has the HIGHEST precedence."
    },
    "path": {
      "type": [
        "string",
        "null"
      ],
      "description": "The specific file or directory path to read, relative to the combined source root. Use exact paths from 'search_dependency_sources'. Providing no path will list the top-level source directory."
    },
    "forceDownload": {
      "type": "boolean",
      "description": "If true, forces a re-download and re-indexing of all targeted sources. Use this only if you suspect cached data is corrupt or significantly outdated."
    },
    "fresh": {
      "type": "boolean",
      "description": "If true, retrieves a fresh dependency list from Gradle before processing. STRONGLY RECOMMENDED if your project dependencies have changed since the last source retrieval."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


## search_dependency_sources

The authoritative tool for searching for symbols or text within the combined source code of all external library dependencies or Gradle's internal engine.
It provides high-performance, indexed search capabilities that far exceed basic grep-based exploration.

### High-Performance Features
- **Precision Symbol Lookup**: Use authoritative regex patterns to find classes, methods, or interfaces across your entire dependency graph.
- **Exhaustive Full-Text Indexing**: Perform surgical text searches using high-performance Lucene indexing. Ideal for finding constants, strings, or specific implementation patterns.
- **Managed Search Scopes**: Narrow your search to specific projects, configurations, or source sets to maintain token efficiency and reduce noise.
- **Flexible File Search (GLOB)**: Locate specific files by name or path pattern using standard Java glob syntax.
  - `*`: Matches zero or more characters within a directory level.
  - `**`: Matches zero or more characters across directory levels.
  - `?`: Matches exactly one character.
  - `{a,b}`: Matches any of the comma-separated strings.
  - Fallback: If the pattern is not a valid glob, it performs a case-insensitive substring search on file paths.
- **Deep Engine Access**: Search the authoritative source code of the Gradle Build Tool itself to understand core system behavior.

### Common Usage Patterns
- **Find Class**: `search_dependency_sources(query="Assert", projectPath=":")`
- **Search Constants**: `search_dependency_sources(query="THREAD_POOL_SIZE", searchType="FULL_TEXT")`
- **Find XML File**: `search_dependency_sources(query="**/AndroidManifest.xml", searchType="GLOB")`
- **Find Java File**: `search_dependency_sources(query="**/*.java", searchType="GLOB")`
- **Find File with Substring**: `search_dependency_sources(query="LICENSE", searchType="GLOB")`
- **Find Gradle Interface**: `search_dependency_sources(query="interface Project", gradleSource=true)`

Once you have identified a file path from the search results, use the `read_dependency_sources` tool to read the full content.
Note: All returned paths are relative to the combined source root.
For detailed search strategies, refer to the `gradle-library-sources` skill.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The absolute path to the project root directory. Defaults to the current workspace root. Always provide this if you are working in a multi-root workspace to ensure the correct project is targeted."
    },
    "projectPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "The project path to search (e.g., ':', ':app'). If null, all project dependencies are searched. This has the lowest precedence."
    },
    "configurationPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Target a specific configuration (e.g., ':app:debugCompileClasspath'). If set, projectPath is ignored. Higher precedence than projectPath."
    },
    "sourceSetPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Target a specific source set (e.g., ':app:main'). If set, projectPath and configurationPath are ignored. Higher precedence than configurationPath."
    },
    "gradleSource": {
      "type": "boolean",
      "description": "If true, searches Gradle Build Tool's own authoritative source code instead of project dependencies. This has the HIGHEST precedence."
    },
    "query": {
      "type": "string",
      "description": "The search query. For SYMBOLS search (default), use regex for classes or methods. For FULL_TEXT, use Lucene queries. For GLOB, use Java glob syntax (e.g., '**/MyClass.kt'). If the query is not a valid glob, it will fall back to a case-insensitive substring match on file paths."
    },
    "searchType": {
      "enum": [
        "SYMBOLS",
        "FULL_TEXT",
        "GLOB"
      ],
      "description": "The type of search to perform. SYMBOLS (default) is ideal for class/method lookup; FULL_TEXT is best for finding specific strings; GLOB is for finding files by path using standard glob patterns (*, **, ?, etc.)."
    },
    "forceDownload": {
      "type": "boolean",
      "description": "If true, re-downloads and re-indexes the targeted sources. Use this only if you suspect cached data is corrupt or significantly outdated."
    },
    "fresh": {
      "type": "boolean",
      "description": "If true, retrieves a fresh dependency list from Gradle before searching. STRONGLY RECOMMENDED if your project dependencies have changed since the last search."
    }
  },
  "required": [
    "query"
  ],
  "type": "object"
}
```


</details>




