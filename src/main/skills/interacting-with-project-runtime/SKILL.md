---
name: interacting-with-project-runtime
description: |
  Provides a persistent JVM/Kotlin REPL for executing and probing project logic within the full classpath context.

  ## Positive Triggers (when to activate)
  - Verifying dynamic behavior of a class or function in the project runtime.
  - Probing internal state or executing experimental logic without a full build cycle.
  - Rapidly prototyping logic changes within the JVM classpath.

  ## Negative Triggers (when NOT to activate)
  - Operating a Gradle build (use `using-gradle`).
  - Modifying build definitions (use `authoring-gradle-builds`).
  - Rendering Compose UI components (use `verifying-compose-ui`).
license: Apache-2.0
metadata:
  author: https://github.com/rnett/gradle-mcp
  version: "1.0.0"
---
<!--
class: authored-local
skill: interacting-with-project-runtime
-->

# Persistent JVM/Kotlin REPL for Project Runtime Probing

Executes Kotlin code interactively within the project's full runtime classpath, enabling rapid logic verification and state inspection without a full build cycle.

## Constitution

- **ALWAYS** prefer reading source code over running it when the question is about API shape, signatures, or static behavior.
- **Use the REPL** when you need to verify **runtime behavior**: dynamic dispatch, state mutations, side effects, or complex logic that is hard to reason about statically.
- **ALWAYS** call `stop` after finishing a session to release JVM resources.
- **NEVER** run code that modifies project files, deletes data, or has irreversible side effects without explicit user approval.
- **After modifying project source code**, call `stop` then `start` to pick up classpath changes.

## Directives

### Starting a Session

Call `kotlin_repl(command="start", projectPath=":module", sourceSet="main")` to initialize a REPL session. The session runs in a dedicated worker process with the project's full classpath.

For session parameters, lifecycle management, and environment troubleshooting, see [REPL Session Setup](references/repl-session-setup.md).

### Running Code

Call `kotlin_repl(command="run", code="...")` to execute a Kotlin snippet. Session state (variables, imports, class definitions) persists between calls.

```kotlin
// Example: Verify a utility function
val result = MyUtils.parseDate("2024-01-15")
println("Parsed: $result, type: ${result::class.simpleName}")
```

### Common Patterns

#### Probing Project Classes

```kotlin
// Inspect a service's behavior
val service = MyService()
val output = service.process("test-input")
println("Output: $output")
println("State: ${service.internalState}")
```

#### Testing Coroutine Logic

```kotlin
import kotlinx.coroutines.runBlocking

runBlocking {
    val result = mySuspendFunction()
    println("Result: $result")
}
```

#### Visualizing Data Structures

```kotlin
val tree = buildTree(sampleData)
println(tree.toPrettyString())
```

## Troubleshooting

For session startup, classpath, and environment issues, see [REPL Session Setup](references/repl-session-setup.md).

## Examples

### Verify a utility function

```json
{
  "command": "start",
  "projectPath": ":app",
  "sourceSet": "main"
}
// Then:
{
  "command": "run",
  "code": "val result = StringUtils.slugify(\"Hello World!\"); println(result)"
}
// Then:
{
  "command": "stop"
}
```

### Prototype logic with test dependencies

```json
{
  "command": "start",
  "projectPath": ":",
  "sourceSet": "test",
  "additionalDependencies": ["org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0"]
}
```
