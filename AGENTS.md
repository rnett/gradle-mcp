# Gradle MCP Project Memory

This repository contains a Model Context Protocol (MCP) server providing deep, programmatic access to Gradle build systems.

## Primacy Zone: Fundamental Mandates

1. **Security & Secrets**: NEVER log, print, or commit API keys, secrets, or .env contents.
2. **Source Control**: NEVER create commits or push unless explicitly instructed.
3. **Tool Preference**: ALWAYS prefer Gradle MCP tools (`gradle`, `inspect_build`, etc.) over raw shell execution.
4. **Verification**: NO WORK IS COMPLETE WITHOUT TESTS. Use `test` for most changes; `check` or `integrationTest` for wider impacts. See [testing-standards](openspec/specs/testing-standards/spec.md).
5. **Tool Metadata**: After modifying tool source, MUST run `./gradlew :updateToolsList` to sync documentation.
6. **Isolated Contexts**: NEVER use Koin's global context (`startKoin`). ALWAYS use local isolated contexts.
7. **JBang Catalog**: We maintain a catalog at `https://github.com/rnett/jbang-catalog` for easy execution.

---

## Technical Standards (OpenSpec)

OpenSpec specs are the **primary source of truth** for architectural rationale, implementation standards, and technical mandates. Refer to them for all project-specific guidance:

- **Testing Standards**: [testing-standards](openspec/specs/testing-standards/spec.md) (Power Assert, tool verification, mocking, async events).
- **Concurrency & Execution**: [build-execution](openspec/specs/build-execution/spec.md) and [build-output-concurrency](openspec/specs/build-output-concurrency/spec.md) (Thread safety, atomic operations, hot-path optimizations, output parsing, REPL sessions).
- **Indexing & Search**: [multi-reader-search](openspec/specs/multi-reader-search/spec.md) (Lucene standards, FQN/Regex search, match expansion, pooling, fail-fast).
- **Caching & Locking**: [cas-dependency-cache](openspec/specs/cas-dependency-cache/spec.md) (CAS model, granular locking, failure detection, GC, path abstractions).
- **Progress Reporting**: [build-monitoring-progress](openspec/specs/build-monitoring-progress/spec.md) (UX, parsing, init script reporting, parallel strategies).
- **Gradle Source Overview**: [gradle-internal-research](openspec/specs/gradle-internal-research/spec.md) (Core APIs, platforms, and research workflows).
- **Architecture Overview**: [build-output-concurrency](openspec/specs/build-output-concurrency/spec.md) and [concurrency-cas-architecture](openspec/specs/concurrency-cas-architecture.md).

---

## Build & Test Commands

- **Build All**: `./gradlew build`
- **Run Fast Tests**: `./gradlew test` (Targeted testing preferred).
- **Run All Tests**: `./gradlew test integrationTest`
- **Quality Check**: `./gradlew check` (Linting + All tests)
- **Update Tools**: `./gradlew :updateToolsList` (Mandatory after metadata changes)
