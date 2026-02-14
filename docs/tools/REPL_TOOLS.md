[//]: # (@formatter:off)

# REPL Tools

Tools for interacting with a Kotlin REPL session.

## project_repl

Interacts with a Kotlin REPL session. The REPL runs with the classpath and compiler configuration (plugins, args) of a Gradle source set. The source set must be for a JVM target.
The REPL uses a classpath that includes the source set and all of its dependencies. The REPL must be restarted to pick up changes to the classpath, compile configuration, or the source code.

### Example Use Cases
- **Testing Project Logic**: Quickly test functions or classes from your project without writing a full test suite or main method.
- **Compose UI Inspection**: Render Compose components to images for visual verification.
- **Rapid Prototyping**: Experiment with new libraries or Kotlin features in the context of your project's environment.
- **Debugging**: Inspect the state of your project or dependencies interactively.

### Commands
- `start`: Starts a new REPL session (replacing any existing one). Requires `projectPath` (e.g., `:app`) and `sourceSet` (e.g., `main`). Can set env vars via `env`.
- `stop`: Stops the currently active REPL session.
- `run`: Executes a Kotlin code snippet in the current session. Requires `code`.

### Execution and Output
- **stdout/stderr**: Captured and returned as text.
- **Last Expression**: The result of the last expression in your snippet is automatically rendered.
- **Responder**: A `responder: dev.rnett.gradle.mcp.repl.Responder` top-level property is available for manual output (no import necessary). Use it to return multiple items or specific formats to the MCP output.

### Automatic Rendering and Content Types
The tool returns a list of content items (text, images, etc.) in order of execution.
- Common image types (AWT `BufferedImage`, Compose `ImageBitmap`, Android `Bitmap`, or `ByteArray` with image headers) are automatically rendered as images.
- Markdown can be returned via `responder.markdown(md)`.
- HTML fragments can be returned via `responder.html(fragment)`.
- All other types are rendered via `toString()`.
- Standard out and error is also included in the tool result.

### Examples

#### Basic Usage
```kotlin
val x = 10
val y = 20
x + y // Result: 30
```

#### Using Project Classes
```kotlin
import com.example.MyService
val service = MyService()
service.doSomething()
```

#### Using the Responder
```kotlin
println("Generating plot...")
responder.image(plotBytes, "image/png")
println("Plot generated.")
"Success" // Last expression
```

#### Compose UI Preview
```kotlin
import androidx.compose.ui.test.*
import com.example.MyComposable

runComposeUiTest {
    setContent {
        MyComposable()
    }
    val node = onRoot()
    val bitmap = node.captureToImage()
    responder.render(bitmap) // Renders the composable as an image
}
```

### Important Notes
- **Source Changes**: Changes to the project's source code will **not** be reflected in an active REPL session. You must `stop` and `start` the REPL to pick up changes to project classes.
- **Methods on Responder**:
  - `render(value: Any?, mime: String? = null)`: Manually render a value. If `mime` is null, it is automatically detected.
  - `markdown(md: String)`: Render a markdown string.
  - `html(fragment: String)`: Render an HTML fragment.
  - `image(bytes: ByteArray, mime: String = "image/png")`: Render an image from bytes.

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
      ]
    },
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located. REQUIRED IF NO MCP ROOTS CONFIGURED, or more than one. If the GRADLE_MCP_PROJECT_ROOT environment variable is set, it will be used as the default if no root is specified and no MCP root is registered. If MCP roots are configured, it must be within them, may be a root name instead of path, and if there is only one root, will default to it."
    },
    "projectPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "The Gradle project path (e.g., ':app'). Required for 'start'."
    },
    "sourceSet": {
      "type": [
        "string",
        "null"
      ],
      "description": "The source set to use (e.g., 'main'). Required for 'start'."
    },
    "env": {
      "type": "object",
      "additionalProperties": {
        "type": "string"
      },
      "description": "Environment variables to set in the REPL worker process Optional for 'start'."
    },
    "code": {
      "type": [
        "string",
        "null"
      ],
      "description": "The Kotlin code snippet to execute. Required for 'run'."
    }
  },
  "required": [
    "command"
  ],
  "type": "object"
}
```


</details>




