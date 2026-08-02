# REPL Session Setup

Shared setup plumbing for the persistent JVM/Kotlin REPL used by runtime probing and Compose rendering.

## Starting a Session

Call `kotlin_repl(command="start", projectPath=":module", sourceSet="main")` to initialize a REPL session. The session runs in a dedicated worker process with the project's full classpath.

- `projectPath`: The Gradle project path (e.g., `:app`, `:` for root).
- `sourceSet`: The source set to use (e.g., `main`, `test`). Use `test` to access test fixtures and test dependencies.
- `additionalDependencies`: Optional list of extra Maven coordinates to add to the classpath.
- `optIn`: Optional list of annotations to opt-in to (e.g., `kotlinx.coroutines.ExperimentalCoroutinesApi`).

## Lifecycle Management

- **`start`**: Initialize a session. Required before `run`.
- **`run`**: Execute a snippet. Session state (variables, imports, class definitions) persists between calls.
- **`stop`**: Terminate a session and release JVM resources. **Always** stop when finished.
- **After modifying project source code**, call `stop` then `start` to pick up classpath changes.

## Troubleshooting

- **ClassNotFound**: Ensure the correct `projectPath` and `sourceSet` are specified. The class must be on the resolved classpath.
- **Stale Classpath**: After modifying source code, call `stop` then `start` to reload.
- **OutOfMemory**: The worker process has limited heap. Break large operations into smaller steps.
- **Compilation Errors**: The snippet must be valid Kotlin. Imports must be explicit (no wildcard auto-imports).
