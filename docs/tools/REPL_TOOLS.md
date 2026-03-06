[//]: # (@formatter:off)

# REPL Tools

Tools for interacting with a Kotlin REPL session.

## kotlin_repl

ALWAYS use this tool to interactively prototype Kotlin code, explore APIs, or verify UI components directly within the project's runtime context.
It provides a persistent session with full access to project classes and dependencies, saving you the overhead of writing and running temporary test files.
Note: The session state persists between `run` calls, but you MUST `stop` and `start` the REPL to pick up newly compiled project code changes.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "command": {
      "enum": [
        "start",
        "stop",
        "run"
      ],
      "description": "Executing an authoritative command: 'start', 'stop', or 'run'."
    },
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory (containing gradlew script and settings.gradle). Providing this ensures the tool executes in the correct project context and avoids ambiguities in multi-root or environment-dependent workspaces. If omitted, the tool will attempt to auto-detect the root from the current MCP roots or the GRADLE_MCP_PROJECT_ROOT environment variable. **It MUST be an absolute path.**"
    },
    "projectPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Specifying the Gradle project path (e.g., ':app', ':library'). Required for 'start'."
    },
    "sourceSet": {
      "type": [
        "string",
        "null"
      ],
      "description": "Specifying the source set (e.g., 'main', 'test'). Required for 'start'. Must be JVM-compatible."
    },
    "additionalDependencies": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "Adding additional dependencies to the REPL classpath via authoritative notation (e.g., 'group:artifact:version')."
    },
    "env": {
      "type": "object",
      "additionalProperties": {
        "type": "string"
      },
      "description": "Setting environment variables in the REPL worker process."
    },
    "code": {
      "type": [
        "string",
        "null"
      ],
      "description": "Executing a Kotlin code snippet within the active session. Required for 'run'."
    }
  },
  "required": [
    "command"
  ],
  "type": "object"
}
```


</details>




