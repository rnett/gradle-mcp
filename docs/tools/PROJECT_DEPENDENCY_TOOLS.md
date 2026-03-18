[//]: # (@formatter:off)

# Project Dependency Tools

Tools for querying Gradle dependencies and checking for updates.

## inspect_dependencies

ALWAYS use this tool to inspect project dependencies, plugins (via `buildscript:` configurations), and check for updates instead of manually parsing build files.
Manual parsing is HIGHLY UNRELIABLE as it misses transitive dependencies, version resolution, and dynamic version updates.
This tool provides the ONLY authoritative, searchable view of the project's exact resolved dependency graph.

### Dependency Intelligence Features

1.  **Auditing the Graph**: Get a searchable, paginated view of direct and transitive dependencies.
2.  **Checking Updates**: Use `checkUpdates=true` (default) to detect newer versions in all repositories.
3.  **Update Summaries**: Use `updatesOnly=true` to return only a summary of dependencies that have available updates.
4.  **Plugin Auditing**: Use `configuration="buildscript:classpath"` to audit build script dependencies (plugins).

### Targeted Auditing
Use the `dependency` parameter to target a single library (e.g., `dependency="org.mongodb:mongodb-driver-sync"`). This is significantly faster as it avoids resolving the entire project graph.
**Note:** When a dependency filter is applied, update checks are skipped for non-matching transitive dependencies to improve performance. These will be marked with `[UPDATE CHECK SKIPPED]` in the output.

### Discovery Best Practices
- **Searching Maven Central**: Use `search_maven_central` to find coordinates or version histories.
- **Dependency Insight**: Use `gradle` for built-in Gradle tasks like `dependencyInsight`.
- **Stability Filtering**: Use `stableOnly=true` to ignore pre-release versions (alpha, beta, rc) in update checks.
- **Precision Slicing**: Use `versionFilter` (regex) for surgical control over considered update versions.

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
      "description": "Authoritatively targeting a single dependency by its coordinates (e.g., 'org.jetbrains.kotlinx:kotlinx-coroutines-core'). Supports 'group:name:version:variant', 'group:name:version', 'group:name', or just 'group'. Targets ONLY the specific library, NOT its transitive dependencies."
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
      "description": "Applying a regex pattern for surgical control over considered update versions. This is a regex search (e.g., use ^1\\. to match versions starting with 1.)."
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




