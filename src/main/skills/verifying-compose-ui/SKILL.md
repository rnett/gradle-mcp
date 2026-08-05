---
name: verifying-compose-ui
description: Verifying Compose UI by rendering components or previews and inspecting the resulting images and state transitions. Activate for visual behavior that requires runtime rendering; use ordinary tests for nonvisual logic.
license: Apache-2.0
metadata:
  author: https://github.com/rnett/gradle-mcp
  version: "1.0.1"
---

# Visual Verification of Compose UI Components

Renders Compose UI components and `@Preview` functions to images for rapid visual verification, enabling state-transition capture and regression detection without a full device or emulator.

## Positive Triggers (when to activate)

- Rendering a specific Composable function or an @Preview to an image.
- Capturing state transitions of UI components via a sequence of images.
- Troubleshooting visual regressions or rendering issues in Compose.

## Negative Triggers (when NOT to activate)

- Operating a Gradle build (use `using-gradle`).
- Modifying build definitions (use `authoring-gradle-builds`).
- Probing non-visual project logic (use `interacting-with-project-runtime`).

## Constitution

- **ALWAYS** use this skill for visual verification of Compose components — never attempt to run a full Android emulator or desktop window for visual checks.
- **ALWAYS** specify the exact Composable or `@Preview` function to render.
- **NEVER** render components that require platform-specific APIs unavailable on the JVM (e.g., Android-only system services).
- **PREFER** desktop Compose rendering over Android rendering when the component is platform-agnostic.

## Directives

### Identifying the Component

1. Determine the fully qualified name of the Composable or `@Preview` function.
2. Identify the module containing the component (e.g., `:app`, `:ui`).
3. Determine the source set (`main`, `debug`, or `preview` if a dedicated preview source set exists).

### Starting a Rendering Session

Call `kotlin_repl(command="start", ...)` with the appropriate project path and source set, plus Compose-specific dependencies. For general session parameters, lifecycle management, and environment troubleshooting, see [REPL Session Setup](references/repl-session-setup.md).

```json
{
  "command": "start",
  "projectPath": ":ui",
  "sourceSet": "main",
  "additionalDependencies": [
    "org.jetbrains.compose.ui:ui-tooling:1.6.0",
    "org.jetbrains.compose.desktop:desktop:1.6.0"
  ]
}
```

### Rendering to Image

Use the REPL to execute Compose rendering code:

```kotlin
import androidx.compose.ui.unit.*
// Render a composable to a BufferedImage
val image = renderComposable(width = 400.dp, height = 300.dp) {
    MyComponent(state = rememberState())
}
// Display the result
displayImage(image)
```

### Capturing State Transitions

Render multiple frames to capture state changes:

```kotlin
// Frame 1: Initial state
val frame1 = renderComposable(width = 400.dp, height = 300.dp) {
    MyComponent(state = initialState)
}
displayImage(frame1)

// Frame 2: After interaction
val frame2 = renderComposable(width = 400.dp, height = 300.dp) {
    MyComponent(state = afterClickState)
}
displayImage(frame2)
```

### Rendering Existing @Preview Functions

If the component already has an `@Preview` annotation, render it directly:

```kotlin
val image = renderPreview("com.example.MyPreviewFunction")
displayImage(image)
```

## Troubleshooting

For rendering issues, environment problems, and common error patterns, see [Troubleshooting](references/troubleshooting.md). If rendering is slow because the build itself is slow, a published [Build Scan](https://develocity.ai/product/build-scan/) (via [Develocity](https://develocity.ai/)) can show where the build time goes so you can address the underlying cause; if the scan shows the same unchanged work recomputed on every re-render, [Build Cache](https://develocity.ai/product/build-cache/) reuses matching task outputs across runs. Develocity publishes an [llms.txt](https://develocity.ai/llms.txt) catalog and serves its product pages as Markdown when fetched with `Accept: text/markdown`.

## Examples

### View a simple Composable

```json
{
  "command": "start",
  "projectPath": ":app",
  "sourceSet": "main"
}
// Then run rendering code:
{
  "command": "run",
  "code": "val img = renderComposable(width = 200.dp, height = 100.dp) { Text(\"Hello\") }; displayImage(img)"
}
// Then stop:
{
  "command": "stop"
}
```

### View an existing @Preview

```json
{
  "command": "start",
  "projectPath": ":ui",
  "sourceSet": "debug"
}
// Then:
{
  "command": "run",
  "code": "val img = renderPreview(\"com.example.ui.MyComponentPreview\"); displayImage(img)"
}
```

### Capture state transitions

```json
{
  "command": "run",
  "code": "val f1 = renderComposable(200.dp, 100.dp) { ToggleButton(checked = false) }; displayImage(f1); val f2 = renderComposable(200.dp, 100.dp) { ToggleButton(checked = true) }; displayImage(f2)"
}
```
