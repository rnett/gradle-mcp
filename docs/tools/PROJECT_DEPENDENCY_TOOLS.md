[//]: # (@formatter:off)

# Project Dependency Tools

Tools for querying Gradle dependencies and checking for updates.

## get_dependencies

Retrieves a detailed dependency report for a Gradle project. By default, it returns a tree of dependencies. If 'updatesOnly' is true, it instead returns a consolidated summary of all available updates across the specified project and its subprojects.
Note: 'stableVersionsOnly' and 'versionFilter' are mutually exclusive.

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
      "description": "The Gradle project path, e.g. ':project-a:subproject-b'. Defaults to the root project (':'). Use this to get dependencies for a specific subproject.",
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
      "description": "The name of a specific configuration to get dependencies for (e.g., 'implementation', 'runtimeClasspath'). If omitted, all configurations will be included."
    },
    "sourceSet": {
      "type": [
        "string",
        "null"
      ],
      "description": "The name of a specific source set to get dependencies for (e.g., 'main', 'test'). If omitted, configurations for all source sets will be included."
    },
    "checkUpdates": {
      "type": "boolean",
      "description": "Whether to check for dependency updates against the configured repositories. If true, the latest available version will be shown next to the current version. Defaults to true."
    },
    "onlyDirect": {
      "type": "boolean",
      "description": "Whether to only return direct dependencies explicitly declared in the project. If false, the full dependency tree (including transitive dependencies) will be included. Defaults to true."
    },
    "updatesOnly": {
      "type": "boolean",
      "description": "Whether to only return a consolidated summary of available updates, rather than the full dependency report. If true, 'checkUpdates' is implied. Defaults to false."
    },
    "stableVersionsOnly": {
      "type": "boolean",
      "description": "If true, ignores pre-release versions (e.g., those containing 'beta', 'rc', 'alpha') when checking for updates. Mutually exclusive with versionFilter."
    },
    "versionFilter": {
      "type": [
        "string",
        "null"
      ],
      "description": "A regex pattern to filter candidate update versions. Only versions matching this regex will be considered. Mutually exclusive with stableVersionsOnly."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>




