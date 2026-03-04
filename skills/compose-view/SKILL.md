---
name: compose-view
description: >
  Authoritatively verify Compose UI components and previews by instantly rendering them 
  to high-quality images directly from the interactive REPL. 
  This skill is the STRONGLY PREFERRED way to iterate on UI designs, offering surgical 
  visual feedback for any @Composable or @Preview without the overhead of running 
  the full application. Use it for rapid UI prototyping, verifying complex state 
  transitions, or checking the visual correctness of your components across 
  different configurations and source sets.
license: Apache-2.0
allowed-tools: project_repl inspect_build search_project
metadata:
  author: rnett
  version: "1.2"
---

# Instant Compose UI Preview & Visual Verification

See your UI as you build it. Render any @Composable or @Preview directly to high-quality images from the interactive REPL for instant, authoritative visual feedback.

## Directives

- **Provide `projectRoot` when in doubt**: Provide `projectRoot` to any Gradle MCP tool that supports it (like `project_repl`) unless you are certain it is not required by the current MCP configuration.
- **Search for Previews**: Use `search_project` with a query like `"@Preview"` to find existing preview functions in the project's source code.
- **Ensure Dependencies**: Compose UI testing dependencies (e.g., `androidx.compose.ui:ui-test-junit4` or `org.jetbrains.compose.ui:ui-test-junit4-desktop`) must be on the classpath. If they are not in the selected `sourceSet`, add them
  using `additionalDependencies`.
- **Note on Versions and Imports**: The package for `runComposeUiTest` and the exact version of dependencies may vary depending on your Compose version and whether you are using JetBrains Compose (Desktop) or Android Compose. Common imports
  include `androidx.compose.ui.test.*` or `org.jetbrains.compose.ui.test.*`. Always check your project's version catalog and existing tests for the correct imports and versions.
- **Render as Image**: Use `node.captureToImage()` and `responder.render(bitmap)` to return the image to the MCP output.
- **Progressive Disclosure**: For complex UI testing or rendering issues, refer to the [Troubleshooting](references/troubleshooting.md) guide.

## When to Use

- **Rapid UI Prototyping & Iteration**: When you need to see the visual result of a Composable change instantly without the latency of a full application launch.
- **Authoritative @Preview Verification**: When you want to verify the visual correctness of existing `@Preview` functions or create new ones for specialized component testing.
- **Complex UI State & Interaction Testing**: When you need to capture visual state before and after interactions (like clicks or state changes) to ensure correct UI behavior.
- **Multi-Configuration Visual Auditing**: When checking how a component renders across different data states or configurations (e.g., different view models or mock data).

## Workflows

### 1. Identify the Composable/Preview

Find the fully qualified name of the `@Composable` or `@Preview` function you want to view. Note any required parameters.

**To find all previews in the project:**

```json
{
  "search_term": "@Preview"
}
```

### 2. Start the REPL

There are two recommended approaches to starting the REPL for viewing Compose components:

#### Kotlin Multiplatform (KMP) Note

The Gradle REPL currently only supports **JVM-based** source sets. If you are working on a Kotlin Multiplatform project, ensure you select a JVM or Desktop target source set (e.g., `jvmMain`, `jvmTest`, `desktopMain`, or `desktopTest`).

#### Approach A: Using the `test` Source Set (Preferred)

This approach is preferred because test source sets typically already include the necessary Compose UI testing dependencies.

1. Run `project_repl` with `command: "start"`.
2. Set `sourceSet: "test"` (or `jvmTest` / `desktopTest` in KMP).
3. Select the appropriate `projectPath`.

#### Approach B: Using the `main` Source Set with Additional Dependencies

Use this approach if you need to run against the `main` source set but it lacks testing dependencies.

1. Run `project_repl` with `command: "start"`.
2. Set `sourceSet: "main"`.
3. Add the required dependency to `additionalDependencies`, for example: `["org.jetbrains.compose.ui:ui-test-junit4-desktop:1.7.0"]`.

### 3. Render the Composable

Execute a script that uses `runComposeUiTest` to render and capture the Composable.

## Examples

### Viewing a simple Composable

```kotlin
import androidx.compose.ui.test.*
import com.example.ui.MyButton

runComposeUiTest {
    setContent {
        MyButton(text = "Click Me")
    }
    val node = onRoot()
    responder.render(node.captureToImage())
}
```

### Viewing a @Preview

```kotlin
import androidx.compose.ui.test.*
import com.example.ui.MyButtonPreview // Top-level function

runComposeUiTest {
    setContent {
        MyButtonPreview()
    }
    val node = onRoot()
    responder.render(node.captureToImage())
}
```

### Capturing Interaction and State

Example showing how to capture images before and after an interaction, and how to inspect ViewModel state.

```kotlin
import androidx.compose.ui.test.*
import com.example.ui.MyCounter
import com.example.viewmodel.MyViewModel

runComposeUiTest {
    val viewModel = MyViewModel()
    setContent {
        MyCounter(viewModel)
    }

    // Capture state before interaction
    responder.render("State before: ${viewModel.count}")
    responder.render(onRoot().captureToImage())

    // Perform interaction
    onNodeWithText("Increment").performClick()

    // Capture state after interaction
    responder.render("State after: ${viewModel.count}")
    responder.render(onRoot().captureToImage())
}
```

### Adding Missing Dependencies

If you get `ClassNotFoundException` for `runComposeUiTest`, restart the REPL with additional dependencies:

```json
{
  "command": "start",
  "projectPath": ":app",
  "sourceSet": "main",
  "additionalDependencies": ["org.jetbrains.compose.ui:ui-test-junit4-desktop:1.7.0"]
}
```

## Troubleshooting

- **No Image Returned**: Ensure you are calling `responder.render(bitmap)`.
- **Empty Image**: If the Composable is empty or has zero size, the image might be empty. Check your Composable's modifiers.
- **Build Failures**: Ensure the project builds before starting the REPL. Use `inspect_build` if the REPL fails to start.
