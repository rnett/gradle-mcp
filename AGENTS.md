# Gradle MCP Project Memory

This repository contains a Model Context Protocol (MCP) server written in Kotlin, providing deep, programmatic access to Gradle build systems.

## Primacy Zone: Fundamental Mandates

1. **Security & Secrets**: NEVER log, print, or commit API keys, secrets, or .env contents.
2. **Source Control**: ALWAYS add changes to Git for persistence, but NEVER create commits or push unless explicitly instructed.
3. **Tool Preference**: ALWAYS prefer Gradle MCP tools (`gradle`, `inspect_build`, etc.) over raw shell execution of `./gradlew`.
4. **Verification**: NO change is complete without passing relevant tests. Use `test` for most changes; `check` or `integrationTest` for wider impacts.
5. **Tool Metadata**: After modifying tool descriptions or structure, MUST run `./gradlew :updateToolsList`.
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
- **Testing**: Use **Power Assert** for rich failure messages (avoid overly nested assertions to prevent compiler crashes). Reuse class-level test resources for speed.
- **MCP Design**: Return structured Markdown for LLM reasoning. Use Tooling API for stability. MCP tool descriptions must be self-sufficient enough for standalone use, while skills remain the primary "agentic" interface.
- **Ambiguity Reporting**: Use "exact match -> unique prefix match -> ambiguous prefix match" flow for lookup tools.

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
