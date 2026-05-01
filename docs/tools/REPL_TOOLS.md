[//]: # (@formatter:off)

# REPL Tools

Tools for interacting with a Kotlin REPL session.

## kotlin_repl

Executes Kotlin code interactively within the project's full JVM classpath — use when you need to **run** code, not just read it.

**Decision rule:**
- Need to understand an API's shape, signature, or behavior? → read its source with `search_dependency_sources` / `read_dependency_sources`. Instant, complete, no JVM needed.
- Need to actually execute code — verify runtime behavior, experiment with your own logic, render UI? → use this tool.

After modifying project source code, `stop` then `start` to pick up classpath changes.

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
      "description": "Absolute path to Gradle project root (parent of gradlew and settings.gradle). Auto-detected from MCP roots when available; specify explicitly for multi-root workspaces or when auto-detection fails."
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
    "envSource": {
      "enum": [
        "NONE",
        "INHERIT",
        "SHELL"
      ],
      "description": "Where to get the base environment variables from. Defaults to INHERIT.",
      "type": "string"
    },
    "optIn": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "List of annotations to opt-in to (e.g., 'kotlinx.coroutines.ExperimentalCoroutinesApi')."
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




