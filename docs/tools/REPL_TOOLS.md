[//]: # (@formatter:off)

# REPL Tools

Tools for interacting with a Kotlin REPL session.

## kotlin_repl

ALWAYS use this tool to interactively prototype Kotlin code, explore APIs, or verify UI components directly within the project's runtime context.
It provides a persistent session with full access to project classes and dependencies, saving you the overhead of writing and running temporary test files.

### Prototyping & Exploration Best Practices

1.  **Prefer Reading Sources**: For exploring unfamiliar library APIs or internal project utilities, ALWAYS prefer reading the source code first using `search_dependency_sources` and `read_dependency_sources`. Reading the source provides complete context, implementation details, and documentation that a REPL cannot easily expose.
2.  **Use REPL for Prototyping**: Only use the REPL when you need to verify dynamic behavior, test small snippets of logic, or prototype a new feature before implementing it.
3.  **Iterative Development**: Use `run` for rapid experimentation. If you modify project source code, you MUST `stop` and `start` the REPL again to pick up the new classes.
4.  **UI Verification**: The REPL can render and return UI components (e.g., Compose previews) as images.

### Authoritative Commands

1.  **`start`**: Initializes (or replaces) a persistent REPL session. Requires `projectPath` and `sourceSet`.
2.  **`run`**: Executes a Kotlin code snippet within the active session. The session state (variables, imports) persists between calls.
3.  **`stop`**: Terminates the session and releases JVM resources.

### Advanced Configuration

1.  **Dependencies**: Use `additionalDependencies` (group:artifact:version) to pull in external libraries not already in the project.
2.  **Environment**: Use `env` to set specific system properties or environment variables for the REPL worker.

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
      "description": "Executing an authoritative command: 'start', 'stop', or 'run'.",
      "type": "string"
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




