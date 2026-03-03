[//]: # (@formatter:off)

# Project Dependency Source Tools

Tools for searching and inspecting source code of Gradle dependencies.

## read_dependency_sources

Read specific source files or list directories from the combined source code of all external library dependencies within a given scope (project, configuration, or source set).

Use this tool to explore the implementation of a library once you have identified the file path.
To find specific classes or methods across all dependencies, use the `search_dependency_sources` tool.

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
      "description": "The project path to get dependencies from (e.g. ':', ':app'). If null, all projects are used."
    },
    "configurationPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "The configuration path to get dependencies from (e.g. ':app:debugCompileClasspath'). If set, projectPath is ignored."
    },
    "sourceSetPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "The source set path to get dependencies from (e.g. ':app:main'). If set, projectPath and configurationPath are ignored."
    },
    "path": {
      "type": [
        "string",
        "null"
      ],
      "description": "Specific file path within the source to read. This path is relative to the root of the combined sources for the given scope."
    },
    "forceDownload": {
      "type": "boolean",
      "description": "If true, re-downloads and re-indexes the sources even if they are already cached."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


## search_dependency_sources

Search for symbols or text within the combined source code of all external library dependencies within a given scope (project, configuration, or source set).

Use this tool to find specific classes, methods, or text in library source code.
Once you have found the file path, you can read the file using the `read_dependency_sources` tool.

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
      "description": "The project path to get dependencies from (e.g. ':', ':app'). If null, all projects are used."
    },
    "configurationPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "The configuration path to get dependencies from (e.g. ':app:debugCompileClasspath'). If set, projectPath is ignored."
    },
    "sourceSetPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "The source set path to get dependencies from (e.g. ':app:main'). If set, projectPath and configurationPath are ignored."
    },
    "query": {
      "type": "string",
      "description": "Search query for symbols or file names."
    },
    "searchType": {
      "enum": [
        "SYMBOLS",
        "FULL_TEXT"
      ],
      "description": "The type of search to perform. Defaults to SYMBOLS."
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




