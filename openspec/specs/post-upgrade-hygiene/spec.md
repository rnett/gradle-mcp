# Capability: post-upgrade-hygiene

## Purpose

Defines the code-hygiene standards to be satisfied after the dependency upgrades (MCP SDK 0.14.0, gradle-tooling-api 9.6.1, ktor 3.5.1, and related). Covers removal of dead deprecated aliases, re-validation of nullability suppressions, deduplication of Koin server resolution, and documentation of non-obvious coroutine and session-lifecycle design decisions.

## Requirements

### Requirement: No Dead Deprecated Aliases

Deprecated compatibility aliases that have no remaining callers SHALL be removed from `CASDependencySourcesDir` in `SourcesDir.kt`.

- **Targets**: `advisoryLockFile` (deprecated alias of `baseLockFile`) and `completionMarker` (deprecated alias of `baseCompletedMarker`).

#### Scenario: Alias removal is safe

- **WHEN** `advisoryLockFile` and `completionMarker` are deleted
- **THEN** the project SHALL compile without referencing either name
- **AND** all former callers SHALL already use `baseLockFile` and `baseCompletedMarker`.

### Requirement: Re-validated Nullability Suppressions

Every `@Suppress("UNNECESSARY_SAFE_CALL")` and `@Suppress("SENSELESS_COMPARISON")` that exists solely to silence a gradle-tooling-api nullability mismatch SHALL be re-checked against gradle-tooling-api 9.6.1, and the code SHALL be adjusted to match the corrected annotations.

- **Affected Sites**: `ProblemsAccumulator.toModel()` (both overloads; safe calls such as `documentationLink?.url` and `details?.details`) and `Problems.kt` `ProblemGroup.fqName` (`parent?.fqName`).
- **Decision Rule**: The correct action depends on what 9.6.1 changed:
  - If the member is now annotated **nullable**, the safe call is warranted and the warning no longer fires — remove the `@Suppress`, keep the safe call.
  - If the member is now annotated **non-null** and is genuinely never null at runtime — remove both the safe call and the `@Suppress`.
  - If the annotation is **unchanged** (declared non-null but nullable at runtime) — keep both the safe call and the `@Suppress`; the workaround is still necessary.

#### Scenario: Suppression removed only when justified

- **WHEN** a suppressed site is re-checked against 9.6.1
- **THEN** the `@Suppress` SHALL be removed if and only if the compiler warning no longer fires
- **AND** a safe call SHALL be removed only if the value is provably non-null at runtime
- **AND** no site SHALL retain a stale `@Suppress` for a warning that 9.6.1 eliminated.

### Requirement: Shared Koin McpServer Resolution

The duplicated try/catch that resolves `McpServer` from the Koin context — currently present in both the `Stdio` and `Sse` transports in `Application.kt` — SHALL be extracted into a single shared helper, `Application.resolveMcpServer(): McpServer`, used by every transport (including the Streamable HTTP transport from `streamable-http-transport`).

- **Behavior Preserved**: The helper SHALL log `"Failed to initialize MCP Server"` and rethrow on Koin resolution failure, exactly as the current inline blocks do.

#### Scenario: Single resolution path

- **WHEN** any transport needs the `McpServer`
- **THEN** it SHALL call `Application.resolveMcpServer()`
- **AND** no transport SHALL contain its own copy of the resolution try/catch.

### Requirement: Documented Coroutine and Session Design

Non-obvious design decisions in the MCP server layer SHALL carry explanatory comments so their rationale survives future refactors.

- **`McpServer.scope`**: A comment SHALL explain that the scope is deliberately **not** a child of the SDK session/message-processing scope. Tool execution must be decoupled from the protocol session so that a `notifications/cancelled` can cancel a hung tool (via `activeToolCallJobs`) without cancelling the session's message-processing coroutine.
- **`McpServer.init` `onConnect` block**: A comment SHALL note that SSE (and Streamable HTTP) sessions bypass `connect()` — they are created by the Ktor plugin via `createSession()` — so `onConnect` is what ensures those sessions receive the cancellation and roots-list notification handlers.
- **`toKotlinxSerialization()` enum special case**: Documented per `mcp-schema-simplification` (schema-kenerator emits `enum` without `type`).

#### Scenario: Rationale is documented at the declaration

- **WHEN** a developer reads `McpServer.scope` or the `onConnect` block
- **THEN** an adjacent comment SHALL explain the cancellation-decoupling and SSE-bypass rationale respectively.
