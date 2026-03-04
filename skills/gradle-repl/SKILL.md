---
name: gradle-repl
description: Execute Kotlin code interactively within your project's full runtime context, including all dependencies and source code. Use to test functions, explore APIs, or prototype logic.
license: Apache-2.0
allowed-tools: project_repl inspect_build
metadata:
  author: rnett
  version: "1.2"
---

# Interactive Kotlin REPL with Full Project Context

Execute Kotlin code interactively in the exact environment of your project's source sets. Test logic, explore library behaviors, and visualize UI components with ease.

## Directives

- **ONLY use the MCP REPL**: NEVER use a shell-based Kotlin REPL or a raw Kotlin runner. The `project_repl` tool provides full access to your project's classpath, source sets, and dependencies, which is not available in a standalone REPL.
- **Provide `projectRoot` when in doubt**: Provide `projectRoot` to any Gradle MCP tool that supports it (like `project_repl`) unless you are certain it is not required.
- **Use the correct source set**: When starting a REPL session, ensure you select the appropriate `projectPath` and `sourceSet` in the `project_repl` tool.
    - `projectPath`: The Gradle project path (e.g., `:app`, `:lib`). Use `:` for the root project.
    - `sourceSet`: Usually `main` for application code or `test` for test code.
- **Restart for code changes**: The REPL uses a snapshot of the classpath. If you change project code, you must call `project_repl` with `command: "stop"` and then `command: "start"` again to pick up the changes.
- **Use the responder**: Use `responder.render(value)` or specialized methods (`markdown`, `image`, `html`) to return rich content to the MCP output. The last expression in a `run` command is also automatically rendered.
- **Imports are required**: Most classes from your project or dependencies must be imported unless they are in the default Kotlin/Java packages.
- **Investigate build failures**: If the REPL fails to start, it's often due to a configuration or build error. See the `gradle-build` skill for details on how to investigate these failures using the `inspect_build` tool.

## When to Use

- To test a function or class interactively without writing a full test.
- To inspect library behavior in your project's context.
- To visualize UI components (like Compose or AWT) by rendering them to images. See the `compose-view` skill for a deep dive into Compose.
- To explore the project's runtime environment or state.

## Workflows

### Starting a REPL Session

1. Use `project_repl` with `command: "start"`.
2. Provide `projectPath` (e.g., `:app`) and `sourceSet` (e.g., `main`).
3. Optionally provide `env` for environment variables and `additionalDependencies` if you need libraries not in the project.

### Executing Code

1. Use `project_repl` with `command: "run"` and the `code` to execute.
2. The result of the last expression will be returned.
3. Use the `responder` for rich output like images or markdown.

### Stopping a REPL Session

1. Use `project_repl` with `command: "stop"` when finished to free up resources.

## Examples

### Run a simple project function

```json
// Start REPL
{
  "command": "start",
  "projectPath": ":my-project",
  "sourceSet": "main"
}

// Run code
{
  "command": "run",
  "code": "import com.example.utils.MyHelper\nMyHelper.calculateSum(1, 2)"
}
// Response: 3
```

### Preview a Compose component

```json
{
  "command": "run",
  "code": "import androidx.compose.ui.test.*\nimport com.example.ui.MyComposable\nrunComposeUiTest {\n  setContent { MyComposable() }\n  val bitmap = onRoot().captureToImage()\n  responder.render(bitmap)\n}"
}
// Response: [Image Data]
```

## Troubleshooting

- **REPL Not Started**: You must call `start` before calling `run`.
- **Project Changes Not Reflected**: Stop and restart the REPL after recompiling your project code.
- **ClassNotFoundException**: Ensure you have selected the correct `sourceSet` and that the project has been built at least once to generate the classpath.
