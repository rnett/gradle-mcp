[//]: # (@formatter:off)

# Project Dependency Tools

Tools for querying Gradle dependencies and checking for updates.

## inspect_dependencies

Inspects the project's resolved dependency graph, checks for updates, and audits plugins; use instead of manually parsing build files which misses transitive deps and dynamic versions.

- **Update Check**: `checkUpdates=true` (default) detects newer versions; use `updatesOnly=true` for a summary of available updates.
- **Plugin Auditing**: Use `configuration="buildscript:classpath"` to audit plugins.
- **Targeted**: Use `dependency="org:artifact"` to target a single library — significantly faster.
- Use `search_maven_central` to find GAV coordinates; `gradle` for `dependencyInsight`.

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
      "type": "string",
      "description": "Specifying the Gradle project path (e.g., ':app'). Defaults to root project (':').",
      "examples": [
        ":",
        ":my-project",
        ":my-project:subproject"
      ]
    },
    "configuration": {
      "type": [
        "string",
        "null"
      ],
      "description": "Filtering the report by a specific configuration (e.g., 'runtimeClasspath')."
    },
    "sourceSet": {
      "type": [
        "string",
        "null"
      ],
      "description": "Filtering the report by a specific source set (e.g., 'test')."
    },
    "dependency": {
      "type": [
        "string",
        "null"
      ],
      "description": "Single dependency filter by GAV (e.g., 'group:name'). Excludes transitive dependencies."
    },
    "checkUpdates": {
      "type": "boolean",
      "description": "Checking project repositories for newer versions of all dependencies authoritatively."
    },
    "onlyDirect": {
      "type": "boolean",
      "description": "Showing only direct dependencies in the summary. Set to false for the full tree."
    },
    "updatesOnly": {
      "type": "boolean",
      "description": "Returning only a summary of dependencies that have available updates."
    },
    "stableOnly": {
      "type": "boolean",
      "description": "Ignoring pre-release versions (alpha, beta, rc, etc.) when checking for updates."
    },
    "versionFilter": {
      "type": [
        "string",
        "null"
      ],
      "description": "Regex filter for considered update versions (e.g., '^1\\.' to match versions starting with 1)."
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




