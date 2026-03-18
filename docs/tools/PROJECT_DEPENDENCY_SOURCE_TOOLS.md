[//]: # (@formatter:off)

# Project Dependency Source Tools

Tools for searching and inspecting source code of Gradle dependencies.

## read_dependency_sources


            ALWAYS use this tool to read source files and explore directory structures of external library dependencies, plugins (via `buildscript:` configurations), or Gradle's internal engine.
            External dependency sources are NOT stored in your local project directory; generic shell tools like `cat`, `grep`, or `find` WILL FAIL to locate them.
            This tool provides high-performance, cached access to the exact source code your project compiles against, which is VASTLY superior and more reliable than generic web searches, external repository browsing, or interactive REPL exploration.
            Reading the source is the professionally recommended way to understand how to use an API, discover its available methods, and see its exact implementation logic.
            This tool supports dot-separated package paths (e.g., `org.gradle.api`) by querying the symbol index, which is more reliable than directory-based resolution in some cases (e.g., Kotlin). This allows exploring package contents even if they don't match the directory structure.
            To read sources for a plugin, pass `configurationPath=\":buildscript:classpath\"`.
            To read the sources of a particular library, the `path` MUST include the first- and second-level "library directories". 
            It typically looks like `<group>/<artifact>[-<variant>]-<version>-sources`. You can see all libraries by reading the root dir or group dirs.
            To find specific classes or methods across all dependencies first, use the `search_dependency_sources` tool (supports DECLARATION, FULL_TEXT, and GLOB search modes).
            
            ### Targeted Exploration
            Use the `dependency` parameter to target a single library (e.g., `dependency="org.mongodb:mongodb-driver-sync"`). This is significantly faster and avoids project-wide index creation.
            **Note:** When `dependency` is used, the `path` is relative to the library root, so the `<group>/<artifact>...` prefix MUST be omitted. All returned paths will be relative to the targeted library root.

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
      "description": "Targeting a specific project path (e.g., ':app'). If null, all dependencies are included."
    },
    "configurationPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Authoritatively targeting a configuration (e.g., ':app:debugCompileClasspath'). Higher precedence."
    },
    "sourceSetPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Authoritatively targeting a source set (e.g., ':app:main'). Highest project precedence."
    },
    "dependency": {
      "type": [
        "string",
        "null"
      ],
      "description": "Authoritatively targeting a single dependency by its coordinates (e.g., 'org.jetbrains.kotlinx:kotlinx-coroutines-core'). Supports 'group:name:version:variant', 'group:name:version', 'group:name', or just 'group'. Targets ONLY the specific library, NOT its transitive dependencies."
    },
    "gradleSource": {
      "type": "boolean",
      "description": "Setting to true authoritatively targets Gradle's own source code. This has HIGHEST overall precedence."
    },
    "path": {
      "type": [
        "string",
        "null"
      ],
      "description": "Reading a specific file, directory, or package. If 'dependency' is NOT provided, this is relative to the combined source root and file paths MUST include the first- and second-level \"library directories\" (e.g., 'group/artifact...'). If 'dependency' IS provided, file paths are relative to the library root and the prefix MUST be omitted. Note: Dot-separated package paths (e.g., 'org.mongodb.client') are logically absolute within the library namespace and do not change relativity."
    },
    "forceDownload": {
      "type": "boolean",
      "description": "Setting to true forces authoritative re-download and re-indexing of targeted sources."
    },
    "fresh": {
      "type": "boolean",
      "description": "Setting to true retrieves a fresh dependency list from Gradle. STRONGLY RECOMMENDED if dependencies changed."
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


## search_dependency_sources


            ALWAYS use this tool to search for symbols or text within the combined source code of ALL external library dependencies, plugins, or Gradle's internal engine authoritatively.
            Generic shell tools like `grep` or `find` on the local directory WILL NOT find these external sources as they reside in remote Gradle caches.
            This tool provides high-performance, indexed search capabilities that far exceed basic grep-based exploration, offering surgical precision across the entire dependency graph.
            Searching the source code is the PROFESSIONALLY RECOMMENDED way to understand how to use an API, discover its available methods, and see its exact implementation logic.
            It is vastly superior and more reliable than interactive REPL exploration or external repository browsing.
            
            ### Supported Search Modes
            
            1.  **`DECLARATION` (Declaration Search)**
                -   **Best for**: Finding precise declarations of classes, interfaces, or methods.
                -   **How to invoke**: Set `searchType="DECLARATION"`. The `query` is **case-sensitive**. Do NOT include keywords like `class`, `interface`, or `fun` (e.g., use `MyClass`, not `class MyClass`).
                -   **Examples**: `query="Project"` (matches by simple name), `query="org.gradle.api.Project"` (matches by FQN), `query="fqn:org.gradle.*.Project"` (glob wildcard), `query="name:.*Configuration"` (regex match).
            
            2.  **`FULL_TEXT` (Default Mode)**
                -   **Best for**: Exhaustive searching of literal strings, constants, or code patterns.
                -   **How to invoke**: Set `searchType="FULL_TEXT"`. The `query` is **case-insensitive**.
                -   **Special Characters**: Characters like `:`, `=`, `+`, `-`, `*`, `/` are special operators and MUST be escaped with a backslash (e.g., `\:`) or enclosed in quotes for literal searches.
                -   **Examples**: `query="\"val x =\""`, `query="TIMEOUT_MS"`, `query="org.gradle.api.internal.artifacts"` (requires escaping or quotes for dots if you want exact literal matches, but generally works fine).
            
            3.  **`GLOB` (File Path Search)**
                -   **Best for**: Locating specific files by name or extension.
                -   **How to invoke**: Set `searchType="GLOB"`. The `query` is **case-insensitive**.
                -   **Examples**: `query="**/AndroidManifest.xml"` (find any file by name), `query="**/*.proto"` (find all files by extension).
            
            ### Authoritative Features
            - **Locating Declarations Precisely**: Use `DECLARATION` to jump directly to a declaration's definition across the entire dependency graph.
            - **Performing Exhaustive Full-Text Searches**: Use `FULL_TEXT` for broad discovery of constants or usage patterns.
            - **Managing Search Scopes**: Narrow searches to specific projects, configurations (including `buildscript:` configurations for plugins), or source sets to maintain token efficiency.
            - **Accessing Gradle Engine Internals**: Set `gradleSource=true` to search the authoritative source code of the Gradle Build Tool itself.
            
            ### Targeted Exploration
            Use the `dependency` parameter to target a single library (e.g., `dependency="org.mongodb:mongodb-driver-sync"`). This is significantly faster and avoids project-wide index creation.
            
            Once identified, use the `read_dependency_sources` tool to read the full content.
            Note: All returned paths are relative to the search root (either the combined source root or the targeted library root if `dependency` is used).
            For detailed search strategies, refer to the `searching_dependency_sources` skill.

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
      "description": "Targeting a specific project path (e.g., ':app'). If null, all dependencies are searched."
    },
    "configurationPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Authoritatively targeting a configuration (e.g., ':app:debugCompileClasspath'). Higher precedence."
    },
    "sourceSetPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Authoritatively targeting a source set (e.g., ':app:main'). Highest project precedence."
    },
    "dependency": {
      "type": [
        "string",
        "null"
      ],
      "description": "Authoritatively targeting a single dependency by its coordinates (e.g., 'org.jetbrains.kotlinx:kotlinx-coroutines-core'). Supports 'group:name:version:variant', 'group:name:version', 'group:name', or just 'group'. Targets ONLY the specific library, NOT its transitive dependencies."
    },
    "gradleSource": {
      "type": "boolean",
      "description": "Setting to true authoritatively searches Gradle Build Tool's own source code."
    },
    "query": {
      "type": "string",
      "description": "Performing an authoritative search with a regex (DECLARATION), Lucene query (FULL_TEXT), or glob (GLOB, e.g., '**/Job.kt'). Note: If 'dependency' is used, GLOB patterns are relative to the library root."
    },
    "searchType": {
      "enum": [
        "DECLARATION",
        "FULL_TEXT",
        "GLOB"
      ],
      "description": "Selecting the search mode: FULL_TEXT (default, exhaustive strings), DECLARATION (full string regex match on declaration name), or GLOB (file paths).",
      "type": "string"
    },
    "forceDownload": {
      "type": "boolean",
      "description": "Setting to true forces authoritative re-download and re-indexing of targeted sources."
    },
    "fresh": {
      "type": "boolean",
      "description": "Setting to true retrieves a fresh dependency list from Gradle before searching."
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
  "required": [
    "query"
  ],
  "type": "object"
}
```


</details>




