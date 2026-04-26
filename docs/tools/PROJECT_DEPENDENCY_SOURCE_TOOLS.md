[//]: # (@formatter:off)

# Project Dependency Source Tools

Tools for searching and inspecting source code of Gradle dependencies.

## read_dependency_sources

Reads source files and explores directory structures of external library dependencies, plugins, or Gradle's internal engine; use instead of shell tools which cannot locate remote dependency sources.
Buildscript (plugin) dependencies are excluded by default to reduce noise. To search plugins, use `sourceSetPath: ":buildscript"` (root project) or `sourceSetPath: ":app:buildscript"` (subproject).
Supports dot-separated package paths via the symbol index. Use `search_dependency_sources` to find paths first.
Strongly recommended: Use the `{group}/{artifact}/...` syntax for `path`. The `dependency` parameter should primarily be used to filter the scope for performance, not as a shortcut for path specification.
Sources are CAS-cached (immutable). Use `fresh=true` for dependency changes; `forceDownload=true` only to recover corrupt/missing files.
ALWAYS scope with a project, configuration, or source set (or use `gradleSource: true`) — unscoped access is no longer supported.
Returns the absolute path of the sources root. Dependency directories are symlinked; pass `--follow` to `rg` (e.g., `rg --follow <pattern> <path>`).

### Examples
- Browse project deps: `{ projectPath: ":" }`
- Browse single dep: `{ dependency: "org.jetbrains.kotlin:kotlin-stdlib" }`
- Read file: `{ path: "org.jetbrains.kotlin/kotlin-stdlib/kotlin/collections/List.kt" }`
- Read package: `{ path: "org.jetbrains.kotlin/kotlin-stdlib/kotlin.collections" }`
- Plugins: `{ sourceSetPath: ":buildscript" }`
- Gradle internals: `{ gradleSource: true }`

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "Absolute path to Gradle project root. Auto-detected from MCP roots or GRADLE_MCP_PROJECT_ROOT when present, must be specified otherwise (usually)."
    },
    "projectPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Targeting a specific project path (e.g., ':app')."
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
    "dependency": {
      "type": [
        "string",
        "null"
      ],
      "description": "Optional filter to scope the read to a single dependency by GAV (e.g., 'group:name'). Use this for performance/focus. Not recommended for general path specification; use `{group}/{artifact}/...` in `path` instead."
    },
    "gradleSource": {
      "type": "boolean",
      "description": "Targets Gradle's own source code; HIGHEST overall precedence."
    },
    "path": {
      "type": [
        "string",
        "null"
      ],
      "description": "File, dir, or package path. Strongly recommended to use the `{group}/{artifact}/...` prefix (e.g. 'org.jetbrains.kotlin/kotlin-stdlib/kotlin/collections/List.kt')."
    },
    "forceDownload": {
      "type": "boolean",
      "description": "Force re-download and re-indexing. EXPENSIVE — only use when sources are corrupt or missing, not for version changes."
    },
    "fresh": {
      "type": "boolean",
      "description": "Retrieve a fresh dependency list from Gradle. Use this (not forceDownload) when dependencies change."
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
      "description": "Pagination. offset = zero-based start index (default 0); limit = max items/lines to return."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


## search_dependency_sources

Searches for symbols or text across the source code of ALL external library dependencies, plugins, or Gradle's internal engine; use instead of shell grep which cannot find remote dependency sources.
Buildscript (plugin) dependencies are excluded by default to reduce noise. To search plugins, use `sourceSetPath: ":buildscript"` (root project) or `sourceSetPath: ":app:buildscript"` (subproject).
Sources are CAS-cached (immutable). Use `fresh=true` for dependency changes; `forceDownload=true` only to recover corrupt/missing files.
ALWAYS scope with a project, configuration, or source set (or use `gradleSource: true`) — unscoped search is no longer supported.
Returns the absolute path of the sources root. Dependency directories are symlinked; pass `--follow` to `rg` (e.g., `rg --follow <pattern> <path>`).

### Search Modes
- `DECLARATION`: Finds class, method, or interface definitions. All symbol searches are **case-sensitive**.
  - **Fields**: Matches against `name` (simple name, e.g., `MyClass`) and `fqn` (fully qualified name, e.g., `com.example.MyClass`).
  - **Unqualified Queries**: A query without a field prefix (e.g., `query: "MyClass"`) searches BOTH `name` and `fqn` fields.
  - **Prefix Syntax**: Use `name:X` for simple names or `fqn:x.y.Z` for precision. Supports Lucene wildcards (`*`, `?`).
  - **FQN Matching**: `fqn` is **literal** (uses `KeywordAnalyzer`). It preserves dots and case. Use `fqn:*.MyClass` for partial matches or `fqn:com.example.*` for package-level matches.
  - **Regex**: Wrap query in `/` for a full regular expression on the `fqn` field (e.g., `query: "/.*\.internal\..*/"`).
- `FULL_TEXT` (default): Exhaustive text search using a Lucene query. **Case-insensitive**. Escape special characters like `:`, `=`, `+`.
- `GLOB`: Locates files by name or extension using Java glob syntax. **Case-insensitive** (e.g., `query: "**/AndroidManifest.xml"`).

### Examples
- All deps: `{ query: "CoroutineScope", searchType: "DECLARATION" }`
- Full-text: `{ query: "TIMEOUT_MS" }`
- Single dep: `{ dependency: "org.jetbrains.kotlinx:kotlinx-coroutines-core", query: "launch", searchType: "DECLARATION" }`
- Gradle internals: `{ gradleSource: true, query: "DefaultProject", searchType: "DECLARATION" }`
- Plugins: `{ sourceSetPath: ":buildscript", query: "MyPlugin", searchType: "DECLARATION" }`
- Files: `{ query: "**/plugin.properties", searchType: "GLOB" }`

### Result Grouping
Results are grouped by proximity: matches within the snippet context range (2) are combined into a single result with a multi-line snippet showing context. Matches that are far apart in a file produce separate results.

Once found, read content with `read_dependency_sources`.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "Absolute path to Gradle project root. Auto-detected from MCP roots or GRADLE_MCP_PROJECT_ROOT when present, must be specified otherwise (usually)."
    },
    "projectPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Targeting a specific project path (e.g., ':app')."
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
    "dependency": {
      "type": [
        "string",
        "null"
      ],
      "description": "Optional filter to scope the search to a single dependency by GAV (e.g., 'group:name'). Use this to focus the search and improve performance."
    },
    "gradleSource": {
      "type": "boolean",
      "description": "Search Gradle Build Tool's own source code."
    },
    "query": {
      "type": "string",
      "description": "Search query: name/FQN/glob/regex (DECLARATION), Lucene query (FULL_TEXT), or glob (GLOB, e.g., '**/Job.kt')."
    },
    "searchType": {
      "enum": [
        "DECLARATION",
        "FULL_TEXT",
        "GLOB"
      ],
      "description": "Search mode: FULL_TEXT (default), DECLARATION (symbol names), or GLOB (file paths).",
      "type": "string"
    },
    "forceDownload": {
      "type": "boolean",
      "description": "Force re-download and re-indexing. EXPENSIVE — only use when sources are corrupt or missing, not for version changes."
    },
    "fresh": {
      "type": "boolean",
      "description": "Retrieve a fresh dependency list from Gradle. Use this (not forceDownload) when dependencies change."
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
      "description": "Pagination. offset = zero-based start index (default 0); limit = max items/lines to return."
    }
  },
  "required": [
    "query"
  ],
  "type": "object"
}
```


</details>




