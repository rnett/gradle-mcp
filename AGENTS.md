# Gradle MCP Project Memory

This repository contains a Model Context Protocol (MCP) server written in Kotlin, providing deep, programmatic access to Gradle build systems.

## Primacy Zone: Fundamental Mandates

1. **Security & Secrets**: NEVER log, print, or commit API keys, secrets, or .env contents.
2. **Source Control**: ALWAYS add changes to Git for persistence, but NEVER create commits or push unless explicitly instructed.
3. **Tool Preference**: ALWAYS prefer Gradle MCP tools (`gradle`, `inspect_build`, etc.) over raw shell execution of `./gradlew`.
4. **Verification**: NO CHANGE OR WORK ITEM IS COMPLETE WITHOUT ENSURING THAT THE RELEVENT TESTS PASS, and that there is sufficient test coverage. Use `test` for most changes; `check` or `integrationTest` for wider impacts or features that
   need full integration tests (e.g. the repl).
5. **Tool Metadata**: After modifying tool descriptions or structure in Kotlin source code, MUST run `./gradlew :updateToolsList` to sync auto-generated documentation (`docs/tools/*.md`) and LLM metadata.
6. **Agent Documentation**: Behavior and features MUST be documented in tool descriptions AND skills to be considered "existing".

---

## WHAT (Architecture & Terminology)

- **MCP Server**: Main entry point using MCP Kotlin SDK.
- **Gradle Provider**: Engine orchestrating Tooling API calls and background builds.
- **Repl Worker**: Process executing Kotlin snippets for prototyping (`repl-worker` module).
- **Skills**: Specialized agentic workflows in `./skills/`.
- **CAS (Content-Addressable Storage)**: Immutable global cache for dependency sources and indices (keyed by content hash).
- **Session View**: Ephemeral, project-level directory containing junctions to CAS entries and a `manifest.json`.
- **BuildId**: UUID tracking background builds.
- **Task Output Capturing**: Mechanism to isolate specific task console output.

---

## WHY (Architectural Rationale)

- **Immutable CAS Model**: Replaced in-place index mutation and complex locking with an immutable generational model to eliminate deadlocks and 60s timeouts on Windows.
- **Virtual Searching**: Leverages Lucene `MultiReader` to search across multiple dependency indices without physical merging.
- **Isolated State**: Each tool call operates on a unique session view, ensuring stability during concurrent updates.
- **Kotlin & Koin**: Leverage Gradle's type system; isolated Koin prevents global state leakage.
- **Testing**: Use **Power Assert** for rich failure messages (avoid overly nested assertions to prevent compiler crashes). Reuse class-level test resources for speed. Avoid using the `!!` operator on test results before assertion (e.g.,
  use `assertTrue(result.property.contains(...))` directly on nullable properties) to ensure Power Assert can display the actual values in failure reports. When asserting on the output of an MCP tool that passes through a service layer,
  verify the final re-rendered format (e.g., `Project: :path`) rather than the raw task output format (e.g., `PROJECT: :path`) to prevent false negatives caused by formatting layers. When calling MCP tools from tests using
  `server.client.callTool`, always use `kotlinx.serialization.json.buildJsonObject` for arguments containing complex types like `List` or `Map` to prevent serialization mismatches. Any integration test class inheriting from
  `BaseReplIntegrationTest` must ensure `createProvider()` is overridden to return a `DefaultGradleProvider` instead of a relaxed mock, as the REPL environment resolution relies on real Gradle builds.
- **Mocking & Future-Proofing**: When refactoring service interfaces mocked in many tests, prioritize using a **data class for parameters** (e.g., `DependencyRequestOptions`). This avoids "boolean blindness" and allows adding new configuration flags with defaults without breaking existing test call sites.
- **MCP Design**: Return structured Markdown for LLM reasoning. Use Tooling API for stability. MCP tool descriptions must be self-sufficient enough for standalone use, while skills remain the primary "agentic" interface.
- **Ambiguity Reporting**: Use "exact match -> unique prefix match -> ambiguous prefix match" flow for lookup tools.
- **Worker Diagnostics via Stderr Buffering**: When managing external worker processes (like the REPL worker), implement a small circular buffer for `stderr` lines in the manager's session state. This provides immediate, high-context
  feedback in tool responses when a process terminates unexpectedly (e.g., exit code 0 or JVM crashes), which is otherwise swallowed by standard logging.
- **Granular Advisory Locking**: Employ a two-level locking strategy (Shared 'Base' lock + Exclusive 'Provider' locks) for multi-stage resource processing. This maximizes concurrent throughput by allowing independent facet-level work to
  proceed once the base structure is finalized.
- **Failure Detection via Shared Locks**: Distinguish successful completion from process crashes by checking for a completion marker after a shared lock on a 'Base' file is released (indicating the exclusive worker finished).

---

## HOW (Operational Execution)

Detailed operational guidance is offloaded to specialized **Expert Skills** to maintain a lean context. Activate these skills when working in their respective domains:

- **Indexing & Search**: Activate `gradle_mcp_search_and_indexing_expert` for Lucene 10+, MultiReader, and search optimization.
- **Concurrency & Testing**: Activate `gradle_mcp_concurrency_expert` for Coroutines, lock-free patterns, and async verification.
- **Caching & Filtering**: Activate `gradle_mcp_caching_expert` for CAS management, path abstractions, and manifest-based filtering.
- **Progress Reporting**: Activate `gradle_mcp_progress_reporting_expert` for UX-focused progress tracking and init script reporting.
- **Skill Authoring**: Activate `gradle_mcp_skill_authoring` for project-specific skill development workflows.
- **REPL & K2 Scripting**: Activate `interacting_with_project_runtime` for prototyping logic and technical mandates for K2-based scripting.
- **Kotlin Idioms**: Refer to `kotlin_reference` for Kotlin 2.3+ mandates (Atomics, Time, UUID).

### General Mandates

- **Caching**: Use `inspect_dependencies` and `search_dependency_sources` with `fresh = true` only when dependencies change.
- **Research First**: ALWAYS research correct implementation/API usage (especially Kotlin Scripting) before attempting code changes.
- **Rubber Ducking**: Document findings and "think out loud" when stuck.

---

## Project-Specific Operational Guidance

- **MCP Dependency Reporting Deduplication**: When deduplicating dependencies across multiple Gradle configurations in an MCP report, prefer `RESOLVED` entries over `UNRESOLVED` ones. Use only the component ID (stripping `UNRESOLVED:`
  prefix) for matching. This handles configurations that are unresolvable by design (e.g., `implementation`) but report the same logical dependency that is `RESOLVED` in child configurations (e.g., `runtimeClasspath`).
- **Structured Gradle Output Parsing**: When parsing structured Gradle output in a service layer, ensure all dependencies, including those inherited from parent configurations, are added to the configuration's dependency list, even if they
  have a `fromConfiguration` marker. This prevents missing top-level dependencies in reports for complex projects (like KMP) where most dependencies are declared in non-resolvable parent configurations.
- **Integration Test Project Dependency Reporting**: For project dependencies in integration tests, consistently use a specific group (e.g., `"project"`) and the project path as the name in the report. This ensures compatibility with
  existing test assertions, as changing project dependencies to report their actual `ModuleVersionIdentifier` (group/name/version) can break tests reliant on path-based naming conventions.
- **Gradle Project Path Normalization**: Prefer a simple `startsWith(':')` check and early returns for null/empty/":" over more expensive `trim(':')` operations for standardizing project paths to reduce unnecessary string allocations.
- **Integration Test Timeouts**: Always specify a generous timeout (e.g., 10 minutes) for integration tests using `runTest` that trigger Gradle builds or start external processes. This prevents flaky failures due to the default 60-second
  timeout being exceeded during parallel execution or on slow machines.
- **Test Resource Management**: Ensure `HttpClient` instances created manually in tests are explicitly closed using `@AfterEach` or `AutoCloseable` to prevent resource leaks (threads, file descriptors) that can cause subsequent tests to
  fail or hang, especially during parallel execution.
- **REPL Session Management**: Explicitly terminate previous REPL sessions in `ReplTools` before starting new ones when session IDs are regenerated. This prevents leaking worker processes and ensures stable session management during
  concurrent or sequential tool calls.
- **Test Search Syntax**: Use simple name prefixes without parentheses when searching for tests with `inspect_build`. This avoids matching issues caused by JUnit 5's addition of parentheses to test method names in report outputs.

---

## Build & Test Commands

- **Build All**: `./gradlew build`
- **Run Fast Tests**: `./gradlew test` (Targeted testing is preferred).
- **Run All Tests**: `./gradlew test integrationTest`
- **Quality Check**: `./gradlew check` (Linting + All tests)
- **Update Tools**: `./gradlew :updateToolsList` (Mandatory after metadata changes)

---

## Resource Index

- **Internal Documentation**: [openspec/docs/](./openspec/docs/)
- **Tool Definitions**: [ToolNames.kt](src/main/kotlin/dev/rnett/gradle/mcp/tools/ToolNames.kt)
- **Full Skill List**: [docs/skills.md](./docs/skills.md)
- **Gradle Source Overview**: [gradle-sources-overview.md](./openspec/docs/gradle-sources-overview.md)
