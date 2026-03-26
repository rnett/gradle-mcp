[//]: # (@formatter:off)

# REPL Tools

Tools for interacting with a Kotlin REPL session.

## kotlin_repl

Provides a persistent, project-aware Kotlin REPL for prototyping, logic verification, and UI rendering within the project's JVM classpath.

Prefer reading sources via `search_dependency_sources` / `read_dependency_sources` for exploring APIs — the REPL is for dynamic behavior and prototyping.
After modifying project source code, `stop` then `start` to pick up classpath changes and the new compiled sources.

### Commands
- **`start`**: Initialize a session. Requires `projectPath` and `sourceSet`.
- **`run`**: Execute a snippet. Session state (variables, imports) persists between calls.
- **`stop`**: Terminate the session and release JVM resources.

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
      "type": "string"
    },
    "projectRoot": {
      "type": "string",
      "description": "Absolute path to Gradle project root. Auto-detected from MCP roots or GRADLE_MCP_PROJECT_ROOT when present, must be specified otherwise (usually)."
    },
    "projectPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Gradle project path (e.g., ':app'). Required for 'start'."
    },
    "sourceSet": {
      "type": [
        "string",
        "null"
      ],
      "description": "Source set (e.g., 'main', 'test'). Required for 'start'. Must be JVM-compatible."
    },
    "additionalDependencies": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "Additional classpath dependencies (e.g., 'group:artifact:version')."
    },
    "env": {
      "type": "object",
      "additionalProperties": {
        "type": "string"
      },
      "description": "Environment variables for the REPL worker process."
    },
    "code": {
      "type": [
        "string",
        "null"
      ],
      "description": "Kotlin snippet to execute. Required for 'run'."
    }
  },
  "required": [
    "command"
  ],
  "type": "object"
}
```


</details>




