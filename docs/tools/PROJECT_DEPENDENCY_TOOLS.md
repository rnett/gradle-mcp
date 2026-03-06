[//]: # (@formatter:off)

# Project Dependency Tools

Tools for querying Gradle dependencies and checking for updates.

## inspect_dependencies

ALWAYS use this tool to inspect project dependencies and check for updates instead of manually parsing build files or running shell commands like `grep`.
Manual parsing is HIGHLY UNRELIABLE as it misses transitive dependencies, version resolution, and dynamic version updates.
This tool provides the ONLY authoritative, searchable view of the project's exact resolved dependency graph, automatically detects available version updates, and resolves configurations natively.
To discover new external libraries, use `search_maven_central`. For built-in Gradle tasks like `dependencyInsight`, use `gradle`.

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
      "description": "Applying a regex pattern for surgical control over considered update versions."
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




