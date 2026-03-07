[//]: # (@formatter:off)

# Project Dependency Source Tools

Tools for searching and inspecting source code of Gradle dependencies.

## read_dependency_sources

ALWAYS use this tool to read source files and explore directory structures of external library dependencies, plugins (via `buildscript:` configurations), or Gradle's internal engine.
External dependency sources are NOT stored in your local project directory; generic shell tools like `cat`, `grep`, or `find` WILL FAIL to locate them.
This tool provides high-performance, cached access to the exact source code your project compiles against, which is vastly superior and more reliable than generic web searches or external repository browsing.
To read sources for a plugin, pass `configurationPath=":buildscript:classpath"`.
To find specific classes or methods across all dependencies first, use the `search_dependency_sources` tool (supports SYMBOLS, FULL_TEXT, and GLOB search modes).

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory (containing gradlew script and settings.gradle). Providing this ensures the tool executes in the correct project context and avoids ambiguities in multi-root or environment-dependent workspaces. If omitted, the tool will attempt to auto-detect the root from the current MCP roots or the GRADLE_MCP_PROJECT_ROOT environment variable. **It MUST be an absolute path.**"
    },
    "projectPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Targeting a specific project path (e.g., ':app'). If null, all dependencies are included."
    },
    "configurationPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Authoritatively targeting a configuration (e.g., ':app:debugCompileClasspath'). Higher precedence."
    },
    "sourceSetPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Authoritatively targeting a source set (e.g., ':app:main'). Highest project precedence."
    },
    "gradleSource": {
      "type": "boolean",
      "description": "Setting to true authoritatively targets Gradle's own source code. This has HIGHEST overall precedence."
    },
    "path": {
      "type": [
        "string",
        "null"
      ],
      "description": "Reading a specific file or directory relative to the combined source root. Use exact paths from search results."
    },
    "forceDownload": {
      "type": "boolean",
      "description": "Setting to true forces authoritative re-download and re-indexing of targeted sources."
    },
    "fresh": {
      "type": "boolean",
      "description": "Setting to true retrieves a fresh dependency list from Gradle. STRONGLY RECOMMENDED if dependencies changed."
    },
    "pagination": {
      "type": "object",
      "required": [],
      "properties": {
        "offset": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        },
        "limit": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        }
      },
      "description": "Pagination parameters. Offset is the zero-based starting index (defaults to 0). Limit is the maximum number of items/lines to return."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


## search_dependency_sources

ALWAYS use this tool to search for symbols or text within the combined source code of ALL external library dependencies, plugins, or Gradle's internal engine authoritatively.
Generic shell tools like `grep` or `find` on the local directory WILL NOT find these external sources as they reside in remote Gradle caches.
This tool provides high-performance, indexed search capabilities that far exceed basic grep-based exploration, offering surgical precision across the entire dependency graph.

### Supported Search Modes

1.  **`SYMBOLS` (Symbol Search)**
    -   **Best for**: Finding precise declarations of classes, interfaces, or methods.
    -   **How to invoke**: Set `searchType="SYMBOLS"`. The `query` is a regex matched against the full symbol name.
    -   **Examples**: `query="JsonConfiguration"` (exact match), `query=".*Configuration"` (suffix match).

2.  **`FULL_TEXT` (Default Mode)**
    -   **Best for**: Exhaustive searching of literal strings, constants, or code patterns.
    -   **How to invoke**: Set `searchType="FULL_TEXT"`. The `query` uses high-performance Lucene syntax.
    -   **Special Characters**: Characters like `:`, `=`, `+`, `-`, `*`, `/` are special operators and MUST be escaped with a backslash (e.g., `\:`) or enclosed in quotes for literal searches.
    -   **Examples**: `query="\"val x =\""`, `query="TIMEOUT_MS"`, `query="org.gradle.api.internal.artifacts"` (requires escaping or quotes for dots if you want exact literal matches, but generally works fine).

3.  **`GLOB` (File Path Search)**
    -   **Best for**: Locating specific files by name or extension.
    -   **How to invoke**: Set `searchType="GLOB"`. The `query` uses standard Java glob syntax.
    -   **Examples**: `query="**/AndroidManifest.xml"` (find any file by name), `query="**/*.proto"` (find all files by extension).

### Authoritative Features
- **Locating Symbols Precisely**: Use `SYMBOLS` to jump directly to a symbol's definition across the entire dependency graph.
- **Performing Exhaustive Full-Text Searches**: Use `FULL_TEXT` for broad discovery of constants or usage patterns.
- **Managing Search Scopes**: Narrow searches to specific projects, configurations (including `buildscript:` configurations for plugins), or source sets to maintain token efficiency.
- **Accessing Gradle Engine Internals**: Set `gradleSource=true` to search the authoritative source code of the Gradle Build Tool itself.

Once identified, use the `read_dependency_sources` tool to read the full content.
Note: All returned paths are relative to the combined source root.
For detailed search strategies, refer to the `searching_dependency_sources` skill.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory (containing gradlew script and settings.gradle). Providing this ensures the tool executes in the correct project context and avoids ambiguities in multi-root or environment-dependent workspaces. If omitted, the tool will attempt to auto-detect the root from the current MCP roots or the GRADLE_MCP_PROJECT_ROOT environment variable. **It MUST be an absolute path.**"
    },
    "projectPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Targeting a specific project path (e.g., ':app'). If null, all dependencies are searched."
    },
    "configurationPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Authoritatively targeting a configuration (e.g., ':app:debugCompileClasspath'). Higher precedence."
    },
    "sourceSetPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Authoritatively targeting a source set (e.g., ':app:main'). Highest project precedence."
    },
    "gradleSource": {
      "type": "boolean",
      "description": "Setting to true authoritatively searches Gradle Build Tool's own source code."
    },
    "query": {
      "type": "string",
      "description": "Performing an authoritative search with a regex (SYMBOLS), Lucene query (FULL_TEXT), or glob (GLOB, e.g., '**/Job.kt')."
    },
    "searchType": {
      "enum": [
        "SYMBOLS",
        "FULL_TEXT",
        "GLOB"
      ],
      "description": "Selecting the search mode: FULL_TEXT (default, exhaustive strings), SYMBOLS (full string regex match on symbol name), or GLOB (file paths)."
    },
    "forceDownload": {
      "type": "boolean",
      "description": "Setting to true forces authoritative re-download and re-indexing of targeted sources."
    },
    "fresh": {
      "type": "boolean",
      "description": "Setting to true retrieves a fresh dependency list from Gradle before searching."
    },
    "pagination": {
      "type": "object",
      "required": [],
      "properties": {
        "offset": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        },
        "limit": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        }
      },
      "description": "Pagination parameters. Offset is the zero-based starting index (defaults to 0). Limit is the maximum number of items/lines to return."
    }
  },
  "required": [
    "query"
  ],
  "type": "object"
}
```


</details>




