[//]: # (@formatter:off)

# Project Dependency Source Tools

Tools for searching and inspecting source code of Gradle dependencies.

## read_dependency_sources

Reads source files and explores directory structures of external library dependencies, plugins, or Gradle's internal engine; use instead of shell tools which cannot locate remote dependency sources.
Supports dot-separated package paths via the symbol index. Use `search_dependency_sources` to find paths first.
`path` without `dependency`: must include group/artifact prefix. With `dependency`: relative to library root.
Sources are CAS-cached (immutable). Use `fresh=true` for dependency changes; `forceDownload=true` only to recover corrupt/missing files.
ALWAYS scope with `dependency`, `projectPath`, `configurationPath`, or `sourceSetPath` — unscoped access indexes ALL dependencies and is VERY EXPENSIVE on large projects.

### Examples
- Browse all deps: `{}`
- Browse single dep: `{ dependency: "org.jetbrains.kotlin:kotlin-stdlib" }`
- Read file: `{ dependency: "org.jetbrains.kotlin:kotlin-stdlib", path: "kotlin/collections/List.kt" }`
- Read package: `{ dependency: "org.jetbrains.kotlin:kotlin-stdlib", path: "kotlin.collections" }`
- Plugin sources: `{ configurationPath: ":buildscript:classpath" }`
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
    "dependency": {
      "type": [
        "string",
        "null"
      ],
      "description": "Single dependency filter by GAV (e.g., 'group:name'). Excludes transitive dependencies."
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
      "description": "File, dir, or package path. Requires group/artifact prefix unless 'dependency' is set."
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
Sources are CAS-cached (immutable). Use `fresh=true` for dependency changes; `forceDownload=true` only to recover corrupt/missing files.
ALWAYS scope with `dependency`, `projectPath`, `configurationPath`, or `sourceSetPath` — unscoped search indexes ALL dependencies and is VERY EXPENSIVE on large projects.

### Search Modes
- `DECLARATION`: class/method/interface names. Case-sensitive; use `name:X` or `fqn:x.y.*`. No keywords like `class`.
- `FULL_TEXT` (default): Lucene query, case-insensitive. Escape special chars like `:` `=` `+`.
- `GLOB`: file paths, case-insensitive (e.g., `**/AndroidManifest.xml`).

### Examples
- All deps: `{ query: "CoroutineScope", searchType: "DECLARATION" }`
- Full-text: `{ query: "TIMEOUT_MS" }`
- Single dep: `{ dependency: "org.jetbrains.kotlinx:kotlinx-coroutines-core", query: "launch", searchType: "DECLARATION" }`
- Gradle internals: `{ gradleSource: true, query: "DefaultProject", searchType: "DECLARATION" }`
- Plugins: `{ configurationPath: ":buildscript:classpath", query: "MyPlugin", searchType: "DECLARATION" }`
- Files: `{ query: "**/plugin.properties", searchType: "GLOB" }`

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
    "dependency": {
      "type": [
        "string",
        "null"
      ],
      "description": "Single dependency filter by GAV (e.g., 'group:name'). Excludes transitive dependencies."
    },
    "gradleSource": {
      "type": "boolean",
      "description": "Search Gradle Build Tool's own source code."
    },
    "query": {
      "type": "string",
      "description": "Search query: regex (DECLARATION), Lucene query (FULL_TEXT), or glob (GLOB, e.g., '**/Job.kt')."
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




