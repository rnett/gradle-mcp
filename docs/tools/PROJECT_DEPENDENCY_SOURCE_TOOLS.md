[//]: # (@formatter:off)

# Project Dependency Source Tools

Tools for searching and inspecting source code of Gradle dependencies.

## read_dependency_sources

Read specific source files or list directories from the combined source code of all external library dependencies or Gradle's internal sources within a given scope.

Sources are downloaded and indexed per scope (project, configuration, or source set) and cached for reuse across tool calls.
By default, if cached sources exist for the scope, they are used without refreshing the dependency list from Gradle (fresh = false). 
It is strongly recommended to set **fresh = true** if the project dependencies have changed since the last refresh. 
You can check the currently indexed libraries by reading the root directory of the sources (providing no path).

**projectRoot** should be the file system path of the Gradle project's root directory (containing gradlew script and settings.gradle). Providing this ensures the tool executes in the correct project context and avoids ambiguities in multi-root or environment-dependent workspaces. If omitted, the tool will attempt to auto-detect the root from the current MCP roots or the GRADLE_MCP_PROJECT_ROOT environment variable. **It MUST be an absolute path.**

Use this tool to explore the implementation of a library or Gradle itself, and to read a source file once you have identified the file path.
If the provided path is a directory, its contents will be listed.
To find specific classes or methods across all dependencies, use the `search_dependency_sources` tool.

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
      "type": [
        "string",
        "null"
      ],
      "description": "The project path to get dependencies from (e.g. ':', ':app'). If null, all projects are used. This has the lowest precedence."
    },
    "configurationPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "The configuration path to get dependencies from (e.g. ':app:debugCompileClasspath'). If set, projectPath is ignored. This has higher precedence than projectPath."
    },
    "sourceSetPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "The source set path to get dependencies from (e.g. ':app:main'). If set, projectPath and configurationPath are ignored. This has higher precedence than configurationPath."
    },
    "gradleSource": {
      "type": "boolean",
      "description": "If true, searches/reads Gradle Build Tool's own source code instead of the project's dependencies. If set, projectPath, sourceSetPath, and configurationPath are ignored. This has the highest precedence."
    },
    "path": {
      "type": [
        "string",
        "null"
      ],
      "description": "Specific file path within the source to read. This path is relative to the root of the combined sources for the given scope, i.e. it should start with the group/filename that is present in the search result paths."
    },
    "forceDownload": {
      "type": "boolean",
      "description": "If true, re-downloads and re-indexes the sources even if they are already cached. This is only necessary for snapshots or things that change.."
    },
    "fresh": {
      "type": "boolean",
      "description": "If true, a fresh list of dependencies and their sources is retrieved from Gradle. If false (default), the cached list for the scope is used if it exists. It is strongly recommended to set this to true if the project dependencies have changed since the last refresh. You can check the currently indexed libraries by reading the root directory of the sources."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


## search_dependency_sources

Search for symbols or text within the combined source code of all external library dependencies or Gradle's internal sources within a given scope.

Sources are downloaded and indexed per scope (project, configuration, or source set) and cached for reuse. Subsequent searches in the same scope will be much faster.
By default, if cached sources exist for the scope, they are used without refreshing the dependency list from Gradle (fresh = false). 
It is strongly recommended to set **fresh = true** if the project dependencies have changed since the last refresh. 
You can check the currently indexed libraries by reading the root directory of the sources with **read_dependency_sources**.

**projectRoot** should be the file system path of the Gradle project's root directory (containing gradlew script and settings.gradle). Providing this ensures the tool executes in the correct project context and avoids ambiguities in multi-root or environment-dependent workspaces. If omitted, the tool will attempt to auto-detect the root from the current MCP roots or the GRADLE_MCP_PROJECT_ROOT environment variable. **It MUST be an absolute path.**

Use this tool to find specific classes, methods, or text in library source code or Gradle itself.
Once you have found the file path, you can read the file using the `read_dependency_sources` tool.
Note that all paths are relative to the combined source root, and are exactly what you should use with read_dependency_sources.
When searching for symbols, the results may have some false-positives - look at the included snippets.

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
      "type": [
        "string",
        "null"
      ],
      "description": "The project path to get dependencies from (e.g. ':', ':app'). If null, all projects are used. This has the lowest precedence."
    },
    "configurationPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "The configuration path to get dependencies from (e.g. ':app:debugCompileClasspath'). If set, projectPath is ignored. This has higher precedence than projectPath."
    },
    "sourceSetPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "The source set path to get dependencies from (e.g. ':app:main'). If set, projectPath and configurationPath are ignored. This has higher precedence than configurationPath."
    },
    "gradleSource": {
      "type": "boolean",
      "description": "If true, searches/reads Gradle's internal source code instead of external dependencies. If set, projectPath, sourceSetPath, and configurationPath are ignored. This has the highest precedence."
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
      "description": "If true, re-downloads and re-indexes the sources even if they are already downloaded. This is only necessary for snapshots or things that change."
    },
    "fresh": {
      "type": "boolean",
      "description": "If true, a fresh list of dependencies and their sources is retrieved from Gradle. If false (default), the cached list for the scope is used if it exists. It is strongly recommended to set this to true if the project dependencies have changed since the last refresh. You can check the currently indexed libraries by reading the root directory of the sources."
    }
  },
  "required": [
    "query"
  ],
  "type": "object"
}
```


</details>




