[//]: # (@formatter:off)

# Project Dependency Tools

Tools for querying Gradle dependencies and checking for updates.

## inspect_dependencies

Query project dependencies, check for available updates, and view repository configurations.

**projectRoot** should be the file system path of the Gradle project's root directory (containing gradlew and settings.gradle). Providing this ensures the tool executes in the correct project context and avoids ambiguities in multi-root or environment-dependent workspaces. If omitted, the tool will attempt to auto-detect the root from the current MCP roots or the GRADLE_MCP_PROJECT_ROOT environment variable. **It MUST be an absolute path.**

Use this tool for:
- Viewing the dependency tree for a specific project or configuration.
- Checking for available library updates across the project.
- Filtering dependencies by source set or configuration.
- Identifying repository URLs used for dependency resolution.

To search for new libraries or see all versions of a library on Maven Central, use the `search_maven_central` tool.
For built-in Gradle dependency tasks, use the `gradlew` tool with `captureTaskOutput`.
For detailed workflows on dependency management, refer to the `gradle-dependencies` skill.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory (containing gradlew and settings.gradle). Providing this ensures the tool executes in the correct project context and avoids ambiguities in multi-root or environment-dependent workspaces. If omitted, the tool will attempt to auto-detect the root from the current MCP roots or the GRADLE_MCP_PROJECT_ROOT environment variable. **It MUST be an absolute path.**"
    },
    "projectPath": {
      "type": "string",
      "description": "The Gradle project path (e.g., :app). Defaults to root.",
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
      "description": "Filter by specific configuration (e.g., runtimeClasspath)."
    },
    "sourceSet": {
      "type": [
        "string",
        "null"
      ],
      "description": "Filter by source set (e.g., test)."
    },
    "checkUpdates": {
      "type": "boolean",
      "description": "Check against repositories for newer versions."
    },
    "onlyDirect": {
      "type": "boolean",
      "description": "Only show direct dependencies. Defaults to true."
    },
    "updatesOnly": {
      "type": "boolean",
      "description": "Only show a summary of available updates."
    },
    "stableOnly": {
      "type": "boolean",
      "description": "Ignore pre-release versions (alpha, beta, rc, etc.)."
    },
    "versionFilter": {
      "type": [
        "string",
        "null"
      ],
      "description": "Regex for filtering candidate update versions."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>




