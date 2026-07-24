## Why

The dependency upgrades (MCP SDK 0.7.2→0.14.0, gradle-tooling-api 9.5.0→9.6.1, ktor 3.5.1→3.5.1+, and related) expose opportunities to clean up accumulated technical debt: dead deprecated aliases, potentially stale nullability suppressions, duplicated Koin resolution logic, and undocumented architectural decisions. Addressing these before further features land keeps the codebase maintainable and prevents degrading signal-to-noise ratios as more changes accumulate on top.

## What Changes

- **Remove dead deprecated aliases**: Delete `advisoryLockFile` and `completionMarker` from `CASDependencySourcesDir` in `SourcesDir.kt` — no remaining callers after migration to `baseLockFile` and `baseCompletedMarker`.
- **Re-validate nullability suppressions**: Check every `@Suppress("UNNECESSARY_SAFE_CALL")` and `@Suppress("SENSELESS_COMPARISON")` tied to gradle-tooling-api nullability against 9.6.1 annotations; remove or retain based on a three-way decision rule (nullable → keep safe call/remove suppress; non-null → remove both; unchanged → keep both).
- **Extract shared Koin McpServer resolution**: Deduplicate the identical try/catch blocks in `Application.kt`'s Stdio and Sse transport paths into `Application.resolveMcpServer(): McpServer`, a prerequisite for the `streamable-http-transport` proposal.
- **Add design rationale documentation comments**: Explain the scope-cancellation-decoupling rationale on `McpServer.scope`, note SSE bypass of `connect()` on the `onConnect` block, and cross-reference `mcp-schema-simplification` for the enum serialization quirk comment.

## Capabilities

No new or modified requirements — this change implements the existing target-state spec at `openspec/specs/post-upgrade-hygiene/spec.md` through code-level refactoring and documentation. All four work items are behavioral-neutral (dead-code removal, suppression re-validation, duplication extraction, comment additions).

## Impact

- `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/model/SourcesDir.kt` — remove dead aliases
- `src/main/kotlin/dev/rnett/gradle/mcp/gradle/build/ProblemsAccumulator.kt` — re-validate `@Suppress` annotations
- `src/main/kotlin/dev/rnett/gradle/mcp/gradle/Problems.kt` — re-validate `@Suppress` annotation
- `src/main/kotlin/dev/rnett/gradle/mcp/Application.kt` — extract shared resolution helper
- `src/main/kotlin/dev/rnett/gradle/mcp/McpServer.kt` — add design rationale comments
- No API changes, no breaking changes, no new dependencies.
