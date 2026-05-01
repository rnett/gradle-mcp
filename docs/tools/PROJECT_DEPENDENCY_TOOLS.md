[//]: # (@formatter:off)

# Project Dependency Tools

Tools for querying Gradle dependencies and checking for updates.

## inspect_dependencies

Inspects the project's resolved dependency graph, checks for updates, and audits plugins; use instead of manually parsing build files which misses transitive deps and dynamic versions.

- **Update Check**: `checkUpdates=true` (default) detects newer versions — individual lines show `[UPDATE AVAILABLE: X.Y.Z]`; use `updatesOnly=true` for a flat summary: `group:artifact: current → latest` with the project paths where each dep is used (forces `checkUpdates=true`). Use `stableOnly=true` to exclude pre-release versions.
- **[UPDATE CHECK SKIPPED]**: Appears only for dependencies that were in scope for update checking but whose resolution genuinely failed — not for dependencies intentionally excluded from the update-check scope (e.g., transitive deps when `onlyDirect=true`).
- **Plugin Auditing**: Use `sourceSet="buildscript"` to audit plugins.
- **Targeted**: Use `dependency="org:artifact"` to target a single library — significantly faster.
- Use `lookup_maven_versions` to find released versions; `gradle` for `dependencyInsight`.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "Absolute path to Gradle project root (parent of gradlew and settings.gradle). Auto-detected from MCP roots when available; specify explicitly for multi-root workspaces or when auto-detection fails."
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
      "description": "Filtering reported components to those matching a GAV coordinate (`group:name:version:variant`, `group:name:version`, `group:name`, or `group`). Transitive children of matched components are shown when `onlyDirect=false`."
    },
    "checkUpdates": {
      "type": "boolean",
      "description": "Checking project repositories for newer versions of all dependencies authoritatively. Always `true` when `updatesOnly=true`."
    },
    "onlyDirect": {
      "type": "boolean",
      "description": "Showing only direct dependencies in the summary. Set to false for the full tree. Also controls update-check scope: only direct deps are checked when `true`."
    },
    "updatesOnly": {
      "type": "boolean",
      "description": "Returning a flat list of upgradeable dependencies: `group:artifact: current → latest` with project paths. Forces `checkUpdates=true`. Note: format changed from earlier versions — the dep key no longer includes the version and the separator changed from ASCII `->` to Unicode `→`."
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
    },
    "excludeBuildscript": {
      "type": "boolean",
      "description": "Whether to exclude buildscript dependencies from the report. Defaults to false."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>




