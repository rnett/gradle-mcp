### Kotlin REPL Tool - Scripting Alternative Implementation Plan

This document outlines the step-by-step implementation plan for the Kotlin REPL tool using the `kotlin-scripting-jvm-host` API, as defined in `src/docs/repl-tool.md`.

#### 1. Dependency Management

Add the necessary Kotlin scripting and serialization dependencies to the project.

- **`gradle/libs.versions.toml`**:
    - `kotlin-scripting-jvm`: `org.jetbrains.kotlin:kotlin-scripting-jvm:<version>`
    - `kotlin-scripting-jvm-host`: `org.jetbrains.kotlin:kotlin-scripting-jvm-host:<version>`
    - `kotlin-compiler-embeddable`: `org.jetbrains.kotlin:kotlin-compiler-embeddable:<version>` (required for the host)
- **`build.gradle.kts`**:
    - Add these to the `implementation` configuration of the main source set.

#### 2. Gradle Environment Extraction (Init Script)

Create `src/main/resources/init-scripts/repl-env.init.gradle.kts` to resolve the project environment.

- **Functionality**:
    - Define a task (e.g., `resolveReplEnvironment`) that runs on demand.
    - Identify the target `SourceSet`.
    - Extract `runtimeClasspath`.
    - Identify the JVM executable using `JavaToolchainService`.
    - Extract Kotlin compiler plugins and their arguments from the `KotlinCompile` task.
    - Output the environment information (classpath, javaExecutable, compilerPlugins, and compilerArgs) using a simple delimited format or specific markers (e.g., `[gradle-mcp-repl-env] key=value`) to avoid JSON serialization
      overhead/dependencies in the init script.
- **Integration**: Update `InitScriptProvider.kt` and `BuildConfig` (if needed) to ensure the script is extracted and available.

#### 3. Scripting Worker Process

Create a standalone Kotlin application (or a sub-main in the current project) that will run on the project's JVM.

- **Responsibility**:
    - Listen on stdin for execution requests (JSON-encoded).
    - Maintain a `ReplEvaluator` instance to preserve state across calls.
    - Configure the `ScriptCompilationConfiguration` with the provided classpath and compiler plugins.
    - Execute snippets and return results (stdout/stderr + evaluation result) as JSON to stdout.
- **Implementation**:
    - Use `BasicJvmScriptingHost` with `JvmScriptCompilationConfigurationBuilder`.
    - Handle `eval` requests containing the code snippet.

#### 4. REPL Session Manager (`ReplManager`)

Implement a manager class within the MCP server to handle worker lifecycles.

- **State**:
    - `Map<String, Process>`: Mapping `sessionId` to the active worker process.
- **Lifecycle**:
    - `getOrCreateProcess(sessionId, env)`: Starts a new worker if one doesn't exist for the session or if the environment (source set/project) changed.
    - `terminateSession(sessionId)`: Kills the worker process.
    - Add a shutdown hook to clean up all active workers.

#### 5. MCP Tool Implementation (`run_kotlin_snippet`)

Add the tool to `GradleExecutionTools.kt` or a new `ReplTools.kt`.

- **Arguments**: `projectRoot`, `projectPath`, `sourceSet`, `code`, `sessionId`.
- **Workflow**:
    1. Resolve `projectRoot`.
    2. Run the `resolveReplEnvironment` task using the init script via `GradleProvider`.
    3. Parse the environment information from the task output.
    4. Call `ReplManager.execute(sessionId, env, code)`.
    5. Return `TextContent` containing the result or errors.

#### 7. Verification & Testing

- **Unit Tests**:
    - Test the init script's ability to extract information from a sample project.
    - Test the worker process independently with mock configurations.
- **Integration Tests**:
    - End-to-end test using `McpServerFixture` to call `run_kotlin_snippet`.
    - Verify that variables are preserved between calls in the same `sessionId`.
    - Verify that compiler plugins (e.g., `kotlinx-serialization`) work within the REPL.

#### 6.1 Rich Output Tests

- Unit tests for renderer selection (null, String, data classes, BufferedImage, ByteArray).
- Unit tests for image encoding and size-limit behavior (downscale, drop with placeholder).
- Unit tests for stdout/stderr capture and ordering relative to display frames.
- Integration tests for multi-display ordering, session persistence, and plugin-enabled rendering.
- Error-path tests: thrown exceptions set `isError = true` and suppress other outputs.

#### 7. Rich Output Handling

This section details how rich output is produced in the REPL worker and mapped on the server side. It keeps the Gradle init script text-only while allowing structured IPC internally between the MCP server and the worker.

- Worker–Server IPC (internal)
    - Use a framed, line-oriented protocol: each frame is a compact JSON object written to stdout by the worker process and parsed by the MCP server. The "no JSON" constraint applies only to Gradle init scripts; using JSON over the internal
      pipe here is acceptable.
    - Frame kinds:
        - `{"event":"display","kind":"text|html|markdown|image","mime":"…","data":"<base64 or text>","meta":{…}}`
        - `{"event":"stdout","data":"…"}` / `{"event":"stderr","data":"…"}`
        - `{"event":"result","type":"success|error","data":"…","renderKind":"text|html|markdown|image?","mime":"…"}`
    - The worker may emit multiple `display` frames per snippet; exactly one `result` or `incomplete` frame is emitted at the end.

- Display API available to snippets
    - Inject a small prelude at session start to expose:
        - `display(value: Any?)`
        - `markdown(md: String)`
        - `html(fragment: String)`
        - `image(bytes: ByteArray, mime: String = "image/png")`
        - `image(img: java.awt.image.BufferedImage, mime: String = "image/png")`
    - These functions forward to an internal `DisplaySink`, which serializes frames to stdout (the IPC channel).

- Automatic result rendering (when `display(...)` isn’t used)
    - `null` → text: `"null"`
    - `String`/`CharSequence` → text: raw value
    - `BufferedImage` → image/png (or requested mime)
    - `ByteArray` → if recognized as an image and small enough, encode accordingly; else base64 summary as text
    - Data classes/collections → pretty `toString()` as text

- Stdout/stderr capture
    - During evaluation, capture `System.out`/`System.err` and emit as `stdout`/`stderr` frames in-order.

- Size limits and safety
    - Apply a per-frame size cap (e.g., 2–4 MB).
    - For oversized text: truncate and note `<truncated: N bytes>`.
    - For oversized images: attempt downscale or lossy re-encode; if still too large, drop and emit a text placeholder.
    - HTML is treated as untrusted text by default. It is delivered as text; clients may choose how to render it.

- Server-side mapping to MCP contents
    - `display` frames map to:
        - kind `text|markdown|html` → `TextContent` with the provided text (markdown/html remain plain text by default).
        - kind `image` → `ImageContent` with base64 data and `mimeType`.
    - The final `result` frame becomes the primary `TextContent` (unless a better auto-rendered image is indicated).
    - Aggregate outputs in order: `[primary TextContent?] + [display frames in emission order] + [stdout/stderr as trailing TextContent blocks]`.
    - On errors, return a single `TextContent("Error: <message>\n<trimmed stack>")` and mark the tool call `isError = true`.
