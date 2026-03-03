[//]: # (@formatter:off)

# Project Dependency Source Tools

Tools for searching and inspecting source code of Gradle dependencies.

## search_dependency_sources

Downloads and indexes sources for the specified project/configuration/sourceSet (if not already cached), then searches them for the given query.

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
      "type": [
        "string",
        "null"
      ],
      "description": "The Gradle project path to scope dependencies to, e.g. ':project-a:subproject-b'. If omitted, the entire build's dependencies are used.",
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
      "description": "The name of a specific configuration to scope to (e.g., 'implementation', `commonMainApi`). If omitted, all configurations are included."
    },
    "sourceSet": {
      "type": [
        "string",
        "null"
      ],
      "description": "The name of a specific source set to scope to (e.g., 'main', `commonMain`). If omitted, all source sets are included."
    },
    "query": {
      "type": "string",
      "description": "The search query. For symbol search, it's the symbol name. For full text search, it's a regex or exact string based on provider implementation."
    },
    "searchType": {
      "type": "string",
      "description": "The search type. Must be 'symbol' or 'full_text'. Defaults to 'symbol'."
    },
    "forceDownload": {
      "type": "boolean",
      "description": "If true, re-downloads and re-indexes the sources even if they are already cached."
    }
  },
  "required": [
    "query"
  ],
  "type": "object"
}
```


</details>


## read_dependency_source_path

Reads a file or walks a directory (up to 2 levels) in the downloaded dependency sources.

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
      "type": [
        "string",
        "null"
      ],
      "description": "The Gradle project path to scope dependencies to, e.g. ':project-a:subproject-b'. If omitted, the entire build's dependencies are used.",
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
      "description": "The name of a specific configuration to scope to (e.g., 'implementation', `commonMainApi`). If omitted, all configurations are included."
    },
    "sourceSet": {
      "type": [
        "string",
        "null"
      ],
      "description": "The name of a specific source set to scope to (e.g., 'main', `commonMain`). If omitted, all source sets are included."
    },
    "path": {
      "type": "string",
      "description": "The relative path within the downloaded sources to read (e.g., as returned by the search tool)."
    },
    "forceDownload": {
      "type": "boolean",
      "description": "If true, re-downloads and re-indexes the sources even if they are already cached."
    }
  },
  "required": [
    "path"
  ],
  "type": "object"
}
```


</details>




