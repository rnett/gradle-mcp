[//]: # (@formatter:off)

# Project Dependency Tools

Tools for querying Gradle dependencies and checking for updates.

## inspect_dependencies

Query project dependencies, check for available updates, and view repository configurations.

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
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located. REQUIRED IF NO MCP ROOTS CONFIGURED, or more than one. If the GRADLE_MCP_PROJECT_ROOT environment variable is set, it will be used as the default if no root is specified and no MCP root is registered. If MCP roots are configured, it must be within them, may be a root name instead of path, and if there is only one root, will default to it."
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




