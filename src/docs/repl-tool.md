### Kotlin REPL Tool Design

#### Overview

The Kotlin REPL tool allows running Kotlin code snippets against a specific Gradle source set's classpath (e.g., `main`, `test`). This is useful for testing code in the context of the project without needing to create a full test or main
method.

This document presents two alternative implementation designs:

1. **Jupyter Kernel Alternative**: Uses the Kotlin Jupyter Kernel for execution and rich output.
2. **Scripting Alternative**: Uses the Kotlin Scripting API for a more lightweight, integrated approach.

Both alternatives MUST support the compiler plugins configured for the target Gradle source set.

---

#### Shared Component: Gradle Environment Resolution

Regardless of the execution mechanism, the MCP server must first resolve the environment from Gradle. This is done via a Gradle init script.

**Init Script (`repl-env.init.gradle.kts`):**

- Adds a task to the project that:
    - Resolves a given source set (e.g., `main`, `test`).
    - **Classpath**: Outputs the full runtime classpath, including compiled output directories. The task ensures these are built.
    - **JVM**: Identifies the JVM executable used by the project (via `JavaToolchainService` or the `java` extension).
    - **Compiler Plugins**:
        - Locates the `KotlinCompile` task for the source set.
        - Extracts the **Plugin Classpath** (`pluginClasspath`).
        - Extracts the **Compiler Arguments** (`compilerOptions.freeCompilerArgs`) to identify plugin options (e.g., `-P plugin:id:key=value`).
- The task outputs this information using a simple line-based format or with specific markers to stdout, avoiding JSON serialization within the init script.

---

#### Alternative 1: Jupyter Kernel

This approach uses the engine behind Kotlin Notebooks and Jupyter.

##### 1. Execution Mechanism

The MCP server launches the `kotlin-jupyter-kernel` as a separate JVM process.

- **JVM Selection**: Started using the project's resolved JVM.
- **Launch Command**:
  ```bash
  <project-jvm> -cp <shadowed-kernel-jar> org.jetbrains.kotlinx.jupyter.IkotlinKt <args>
  ```
- **Library**: The MCP server includes the `kotlin-jupyter-kernel-shadowed` JAR as a resource.

##### 2. Compiler Plugin Support

To support compiler plugins in the Jupyter Kernel:

- **Initialization**: The MCP server sends a hidden initialization snippet before the user's code.
- **Mechanism**: It uses the kernel's internal APIs or line magics (if supported for plugins) to register the extracted plugin classpath and arguments.
- *Note:* The Jupyter kernel is built on the scripting API, so it can be configured with `ScriptCompilationConfiguration` which includes compiler plugins.

##### 3. Interaction Protocol and Rich Output

- **Protocol**: Jupyter Messaging Protocol (ZeroMQ). The MCP server acts as a client.
- **Rich Output**: Supports MIME-encoded data (`text/html`, `image/png`, `application/json`).
- **Mapping to MCP**:
    - `text/plain` -> `TextContent`
    - `text/html` -> `TextContent` (with hint/wrapping)
    - `image/*` -> `ImageContent` (base64)
- **State Management**: The kernel process maintains state (variables, functions) for the session's lifetime.

##### 4. MCP Tool: `run_kotlin_snippet`

- **Arguments**: `projectRoot`, `projectPath`, `sourceSet`, `code`, `sessionId`.
- **Workflow**:
    1. Resolve environment via Gradle (Classpath, JVM, Plugins).
    2. Start/reuse the Jupyter Kernel process.
    3. Initialize with compiler plugins.
    4. Execute snippet and return mapped MCP content.

---

#### Alternative 2: Kotlin Scripting

This approach uses the standard `kotlin-scripting-jvm-host` API directly within the MCP server process (or a small worker process).

##### 1. Execution Mechanism

Uses the `BasicJvmScriptingHost` or a custom host to evaluate code snippets.

- **JVM Selection**: If using a worker process, it uses the project's JVM. If running inside the MCP server, it must be compatible. *Recommendation: Use a worker process to match project JVM.*
- **Host Configuration**: Configured with the resolved classpath and compiler plugins.

##### 2. Compiler Plugin Support

The Scripting API has native support for compiler plugins via `ScriptCompilationConfiguration`.

- **Configuration**:
  ```kotlin
  val compilationConfiguration = ScriptCompilationConfiguration {
      jvm {
          compilerOptions.append(extractedFreeCompilerArgs)
          plugins.append(extractedPluginClasspath.map { JvmScriptCompilationConfigurationBuilder.ClasspathEntry(it) })
      }
      ide {
          acceptedCompilerPlugins.append("id.of.plugin")
      }
  }
  ```
- This ensures that plugins like Compose, AtomicFU, or No-arg work exactly as they do in the Gradle build.

##### 3. Interaction Protocol and Output Handling

- **Protocol**: Standard IPC (e.g., stdin/stdout or a simple Socket/gRPC) between the MCP server and the worker process.
- **Rich Output**: The Scripting API can be configured with a `ScriptEvaluationConfiguration` that provides a `REPL` evaluator. Results are returned as Kotlin objects.
- **Mapping to MCP**:
    - Simple types -> `TextContent`.
    - Objects with `toString()` or specific renderers -> `TextContent`.
    - Custom renderers for `BufferedImage` or HTML-string types -> `ImageContent` or specialized `TextContent`.

##### 4. MCP Tool: `run_kotlin_snippet`

- **Arguments**: `projectRoot`, `projectPath`, `sourceSet`, `code`, `sessionId`.
- **Workflow**:
    1. Resolve environment via Gradle (Classpath, JVM, Plugins).
    2. Start/reuse the Scripting Worker process using the project JVM.
    3. Configure the Scripting Host with extracted plugins.
    4. Evaluate snippet and return mapped MCP content.

---

#### Comparison Summary

| Feature            | Jupyter Kernel                      | Kotlin Scripting                    |
|:-------------------|:------------------------------------|:------------------------------------|
| **Complexity**     | Higher (ZeroMQ, Messaging Protocol) | Lower (Direct API, Simple IPC)      |
| **Rich Output**    | Excellent (Native support)          | Limited (Needs manual mapping)      |
| **Plugin Support** | Supported                           | Deeply integrated                   |
| **Footprint**      | Heavy (Separate process + JARs)     | Lighter                             |
| **Statefulness**   | Native                              | Native (via `ReplEvaluator`)        |
| **Maintenance**    | Relies on Jupyter project           | Relies on Kotlin Compiler/Scripting |
