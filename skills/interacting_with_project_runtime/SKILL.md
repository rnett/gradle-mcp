---
name: interacting_with_project_runtime
description: >
  Provides a persistent, project-aware Kotlin REPL for rapid logic verification and prototyping within the project's JVM classpath;
  use for testing dynamic behavior, prototyping snippets, or rendering Compose UI previews.
  Do NOT use for exploring library APIs (prefer `searching_dependency_sources`) or running Gradle builds.
license: Apache-2.0
metadata:
  author: https://github.com/rnett/gradle-mcp
  version: "2.2"
---

# Authoritative Project Runtime & Source Interaction

Probes project logic, tests utility functions, and interacts with the JVM runtime using a project-aware REPL.

## Constitution

- **ALWAYS** prefer reading source code via `search_dependency_sources` and `read_dependency_sources` for exploring unfamiliar library APIs.
- **ALWAYS** use `kotlin_repl` instead of a standalone Kotlin REPL for project-aware interaction.
- **ALWAYS** provide absolute paths for `projectRoot`.
- **ALWAYS** start a REPL session with the correct `projectPath` and `sourceSet` (e.g., `main`, `test`).
- **ALWAYS** restart the REPL (`stop` then `start`) after modifying project source code to pick up changes in the classpath.
- **ALWAYS** use the `responder` API for rich output (images, markdown) to improve diagnostic visibility.
- **NEVER** leave a REPL session running indefinitely; use `stop` when finished.

## Directives

- **Read the source first**: For exploring unfamiliar library APIs or internal project utilities, ALWAYS prefer reading the source code first. It provides complete context, implementation details, and documentation that a REPL cannot easily
  expose. Use `search_dependency_sources` to find definitions and `read_dependency_sources` to analyze them.
- **ALWAYS use project-aware REPL**: Only the `kotlin_repl` tool provides full access to the project's exact classpath, dependencies, and source sets. NEVER attempt to use standalone runners for project-internal logic.
- **Identify the environment**: When starting a session, ALWAYS ensure you select the appropriate `projectPath` (e.g., `:app`) and `sourceSet` (e.g., `main` for application code, `test` for test utility access).
- **Pick up source changes**: The REPL uses a static snapshot of the classpath. If you change project code, you MUST `stop` and then `start` the session again to pick up the updated classes.
- **Utilize the `responder`**: ALWAYS use `responder.render(value)` or specialized methods (`markdown`, `image`, `html`) to return rich content.
- **Import necessary classes**: ALWAYS provide explicit imports for project-specific and library classes.
- **Use `envSource: SHELL` if environment variables are missing**: Set `env: { envSource: "SHELL" }` when calling `start` if the REPL cannot find expected env vars (e.g., `JAVA_HOME`).

## K2 Scripting & REPL Internals

### Snippet Naming & Evaluation

- **Naming Protocol**: When using `K2ReplCompiler`, ALWAYS name the `SourceCode` snippets using the `_<ID>` convention (e.g., `_1`, `_2`). This ensures the generated class name matches the name expected by the evaluator.
- **Stack Trace Identification**: Identify K2 REPL script execution frames by looking for the `$$eval` method name in stack traces. Use this marker for cleaning and isolating script-level errors from internal evaluator infrastructure.
- **`RuntimeException` Ambiguity**: Explicitly import or use the FQN for `java.lang.RuntimeException` in snippets to avoid ambiguity with `kotlin.RuntimeException` during FIR analysis.

### Architectural Boundaries

- **Workarounds**: The current experimental K2 REPL implementation does not support the standard `providedProperties` scripting API. Use a global singleton object in a shared classloader and `defaultImports` as a workaround.
- **Abstraction Mandate**: ALWAYS abstract `K2ReplCompiler` and `K2ReplEvaluator` behind the generic `ReplCompiler` and `ReplEvaluator` interfaces from the Kotlin Scripting API.

## When to Use

- **Rapid Logic Verification**: When you need to quickly test a function, algorithm, or class behavior without the overhead of writing a full test suite.
- **Interactive Prototyping**: When you want to experiment with a snippet of logic within the exact context of your project's dependencies and JVM configuration before implementing it.
- **Visual Component Auditing**: When iterating on UI components (e.g., Compose) and you need to see the result instantly by rendering them to images.
- **Dynamic State & Data Probing**: When you need to perform one-off data transformations or queries using your project's existing utility classes.

## Workflows

### Starting an Authoritative Session

1. Identify the project module (e.g., `:app`) and source set (e.g., `main`).
2. Call `kotlin_repl(command="start")`.
3. Optionally provide `env` for environment variables or `additionalDependencies` if you need external libraries not currently in the project.

### Probing Code & State

1. Use `kotlin_repl(command="run")` with your Kotlin code.
2. Use `responder.render()` for rich diagnostics.
3. Review the returned text or image content.

### Lifecycle Management

1. Use `kotlin_repl(command="stop")` once your investigation is complete to release system resources.

## Examples

### Probing a project utility function

```json
// Start the session
{
  "command": "start",
  "projectPath": ":my-project",
  "sourceSet": "main"
}

// Execute the probe
{
  "command": "run",
  "code": "import com.example.utils.MyHelper\nMyHelper.calculateSum(1, 2)"
}
// Reasoning: Using kotlin_repl to verify a utility function in the context of the main source set.
```

### Visualizing a UI Component

```kotlin
import androidx.compose.ui.test.*
import com.example.ui.MyComposable

runComposeUiTest {
    setContent { MyComposable() }
    val bitmap = onRoot().captureToImage()
    responder.render(bitmap)
}
// Reasoning: Using the responder API to retrieve a high-resolution image of a Compose component.
```

## Troubleshooting

- **REPL Not Started**: You must call `start` successfully before calling `run`.
- **ClassNotFoundException**: Ensure the project has been built at least once and that you have selected the correct `sourceSet` (e.g., `test` if the class is in `src/test/kotlin`).
- **Changes Not Reflected**: If your code changes aren't appearing, `stop` and `start` the REPL to refresh the classpath.
