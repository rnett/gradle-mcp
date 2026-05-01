# Project Playbook (Windows + Gradle 9.4.1)

## Environment

### Variables

- `GRADLE_MCP_PROJECT_ROOT` — optional, sets default Gradle project root for MCP tools
- `GRADLE_MCP_LOG_DIR` — test log output directory (set automatically by build)
- JDK 21+ required (project uses `jvmToolchain(21)` for main module)
- Gradle wrapper version: 9.4.1

---

## Modules

### `gradle-mcp` (root)

#### Summary

Main MCP server application. A Kotlin JVM project providing a Model Context Protocol server for Gradle, enabling AI agents to explore project structures, run tasks, audit dependencies, and interact with the JVM runtime. Uses Ktor (Netty)
for HTTP transport and STDIO for local transport.

#### How to Build

```
./gradlew build -x integrationTest
```

Builds the project and runs all unit tests (338 tests, all pass). Skips integration tests for a faster feedback loop.

#### How to Run All Tests (Unit)

```
./gradlew test
```

Runs all unit tests in the root project and submodules.

#### How to Run All Tests (Including Integration)

```
./gradlew integrationTest
```

Runs integration tests (74/76 pass; 2 pre-existing REPL-related failures: `JavaReplIntegrationTest.initializationError` and `KotlinReplIntegrationTest.basic execution works()`).

#### How to Run Full Check

```
./gradlew check
```

Runs linting + all tests (unit + integration).

#### How to Run a Single Test

```
./gradlew :test --tests "dev.rnett.gradle.mcp.tools.PaginationTest"
```

Use `:test` (root project prefix) to scope the test filter to the root project only. Replace the fully qualified class name as needed.

#### Run / Check

- **Run the application (STDIO transport)**:
  ```
  ./gradlew :run
  ```
  Starts the Gradle MCP server with STDIO transport. Output: `Starting Gradle MCP server with STDIO transport...`

- **Build fat JAR**:
  ```
  ./gradlew shadowJar
  ```
  Creates an uber-JAR with all dependencies merged.

- **Update tool documentation**:
  ```
  ./gradlew :updateToolsList
  ```
  Syncs auto-generated tool documentation in `docs/tools/*.md`. Must be run after modifying tool descriptions or structure in Kotlin source code.

- **Verify tool documentation**:
  ```
  ./gradlew :verifyToolsList
  ```
  Verifies that tool docs are up-to-date (runs as part of `check`).

#### Notes

- The project uses Gradle configuration cache (stored on first build).
- Integration tests require JDK 21+ and may need additional JVM modules (`jdk.incubator.vector`).
- 2 integration tests fail pre-existing: `JavaReplIntegrationTest` and `KotlinReplIntegrationTest` — these are REPL environment issues, not regressions.

---

### `:repl-worker`

#### Summary

A standalone Kotlin JVM process that executes Kotlin snippets for the REPL functionality. Uses Kotlin scripting (JSR 223 / Kotlin Scripting JVM). Built as a shadow JAR and bundled into the main application.

#### How to Build

```
./gradlew :repl-worker:build
```

#### How to Run Tests

```
./gradlew :repl-worker:test
```

#### How to Run Single Test

```
./gradlew :repl-worker:test --tests "dev.rnett.gradle.mcp.repl.ReplManagerTest"
```

#### Run / Check

- **Build shadow JAR**:
  ```
  ./gradlew :repl-worker:shadowJar
  ```
  Produces `repl-worker-all.jar` with a main class of `dev.rnett.gradle.mcp.repl.ReplWorker`.

#### Notes

- Targets JVM toolchain 8 for maximum compatibility.
- Uses Kotlin Power Assert plugin.
- Tests require `kotlin.stdlib.path` and `kotlin.stdlib.kotlin2.path` system properties (set automatically by build script).

---

### `:repl-shared`

#### Summary

Shared data models and serialization contracts used by both the main application and the REPL worker. Minimal module with no tests.

#### How to Build

```
./gradlew :repl-shared:build
```

#### How to Run Tests

No tests configured (`tasks.test.enabled = false`).

#### Notes

- Pure data/contract module with Kotlinx Serialization.
- Targets JVM toolchain 8 for compatibility with `repl-worker`.

---

## Tools

- **Gradle Wrapper**: `gradlew.bat` (Windows) / `gradlew` (Unix) — Gradle 9.4.1
- **JDK**: 21+ required (main module), 8+ (repl modules)
- **Build System**: Gradle with Kotlin DSL (`build.gradle.kts`)
- **Testing**: JUnit 5 (Platform), Kotlin Test, MockK, Kotest (Power Assert)
- **CI**: No GitHub Actions workflows found in repository

## Notes

- All commands use the Gradle wrapper (`./gradlew`). On Windows, use `gradlew.bat` directly.
- The Gradle MCP server can also be run via JBang for end-user consumption (see README), but development builds use the Gradle wrapper.
- The project uses `jvm-test-suite` plugin for integration tests (separate source set from unit tests).
- Configuration cache is enabled and stored after the first successful build.

---

This file is the single source of truth for the project workflow.

- If you discover better or corrected commands, update `.junie/playbook.md` immediately.
- If a section contains `TBD` and you later validate it, replace `TBD` with the command.
- Do not keep `TBD` if validation succeeded.
- If a command becomes invalid, fix it or replace it with `TBD`.
