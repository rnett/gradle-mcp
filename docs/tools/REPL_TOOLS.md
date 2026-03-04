[//]: # (@formatter:off)

# REPL Tools

Tools for interacting with a Kotlin REPL session.

## project_repl

The authoritative tool for executing Kotlin code interactively within your project's full runtime context.
It provides a managed execution environment with direct access to your project's source sets, dependencies, and compiler configuration.

### Authoritative Features
- **Deep Context Integration**: Unlike standalone REPLs, this tool uses your project's exact classpath. Call your project's functions, instantiate its classes, and use its libraries with absolute precision.
- **Persistent Execution State**: Maintain variables, functions, and imports across multiple `run` calls within a single session.
- **Rich Output Rendering**: Authoritatively render images (Compose/AWT), Markdown, and HTML directly to your context via the `responder` API.
- **Managed Lifecycle**: Explicit `start` and `stop` commands ensure resources are managed efficiently.

### Common Usage Patterns
- **Prototyping Logic**: Rapidly test a complex algorithm or project utility without writing a full test suite.
- **API Exploration**: Interactively explore a new library's API within your project's environment.
- **UI Verification**: Render Compose components to images for instant visual feedback (see the `compose-view` skill).
- **Debugging**: Authoritatively inspect the state of your project or its dependencies at runtime.

### Execution and Result Handling
- **Standard Streams**: Both `stdout` and `stderr` are captured and returned as authoritative text.
- **Automatic Rendering**: The result of the last expression in a `run` call is automatically rendered.
- **The Responder API**: Use the `responder: dev.rnett.gradle.mcp.repl.Responder` property for manual, rich output (e.g., `responder.render(myBitmap)`).

**Safety Note**: Changes to project source code are NOT reflected in an active session. You MUST `stop` and `start` the REPL to pick up changes to project classes.
For detailed interactive workflows, refer to the `gradle-repl` skill.

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
      "description": "The authoritative command to execute: 'start', 'stop', or 'run'."
    },
    "projectRoot": {
      "type": "string",
      "description": "The absolute path to the project root directory. Defaults to the current workspace root. Always provide this if you are working in a multi-root workspace to ensure the correct project context."
    },
    "projectPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "The Gradle project path (e.g., ':app', ':library'). Required for the 'start' command to establish the classpath."
    },
    "sourceSet": {
      "type": [
        "string",
        "null"
      ],
      "description": "The source set to use (e.g., 'main', 'test'). Required for the 'start' command. Must be a JVM-compatible source set."
    },
    "additionalDependencies": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "Additional dependencies to add to the REPL classpath, using authoritative Gradle dependency notation (e.g., 'group:artifact:version'). Optional for 'start'."
    },
    "env": {
      "type": "object",
      "additionalProperties": {
        "type": "string"
      },
      "description": "A map of environment variables to set in the REPL worker process. Optional for 'start'."
    },
    "code": {
      "type": [
        "string",
        "null"
      ],
      "description": "The Kotlin code snippet to execute within the active session. Required for the 'run' command."
    }
  },
  "required": [
    "command"
  ],
  "type": "object"
}
```


</details>




