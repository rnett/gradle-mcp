[//]: # (@formatter:off)

# Project Dependency Tools

Tools for querying Gradle dependencies and checking for updates.

## inspect_dependencies

The authoritative tool for querying project dependencies, performing high-resolution update checks, and viewing repository configurations.
It provides a managed, searchable view of your project's dependency graph that is far superior to reading raw build files.

### High-Performance Features
- **Deep Dependency Intelligence**: View the full dependency tree for any project, configuration, or source set. Understand exactly why a specific version of a library is being included.
- **Automated Update Detection**: Instantly identify dependencies with newer versions available in your configured repositories. Support for stable-only filtering and custom version regexes.
- **Surgical Precision**: Filter results by configuration or source set to minimize noise. Use `updatesOnly` for highly token-efficient health checks.
- **Repository Visibility**: See the authoritative list of repositories (Maven Central, Google, etc.) being used for dependency resolution in each project.

### Common Usage Patterns
- **Full Audit**: `inspect_dependencies(projectPath=":app")`
- **Token-Efficient Update Check**: `inspect_dependencies(updatesOnly=true, stableOnly=true)`
- **Configuration Deep Dive**: `inspect_dependencies(configuration="runtimeClasspath")`

To discover new libraries or see all versions of a specific artifact, use the `search_maven_central` tool.
For built-in Gradle tasks like `dependencyInsight`, use the `gradle` tool with `captureTaskOutput`.
For detailed dependency management strategies, refer to the `gradle-dependencies` skill.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The absolute path to the project root directory. Defaults to the current workspace root. Always provide this if you are working in a multi-root workspace to ensure the correct project is targeted."
    },
    "projectPath": {
      "type": "string",
      "description": "The Gradle project path (e.g., ':app'). Defaults to the root project (':'). Use the 'projects' task in 'gradle-introspection' to see all valid project paths.",
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
      "description": "Filter the report by a specific configuration (e.g., 'runtimeClasspath', 'implementation'). This is highly recommended for large projects to reduce noise and context usage."
    },
    "sourceSet": {
      "type": [
        "string",
        "null"
      ],
      "description": "Filter the report by a specific source set (e.g., 'test', 'main')."
    },
    "checkUpdates": {
      "type": "boolean",
      "description": "If true, checks project repositories for newer versions of all dependencies. This is the authoritative way to audit your dependency health."
    },
    "onlyDirect": {
      "type": "boolean",
      "description": "If true (default), only shows direct dependencies in the summary. Set to false to see the full transitive dependency tree."
    },
    "updatesOnly": {
      "type": "boolean",
      "description": "If true, only returns a summary of dependencies that have available updates. This is the most token-efficient way to perform regular update audits."
    },
    "stableOnly": {
      "type": "boolean",
      "description": "If true, ignores pre-release versions (alpha, beta, rc, etc.) when checking for updates. STRONGLY RECOMMENDED for stable production environments."
    },
    "versionFilter": {
      "type": [
        "string",
        "null"
      ],
      "description": "A regex pattern for filtering candidate update versions. Use this for surgical control over which versions are considered."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>




