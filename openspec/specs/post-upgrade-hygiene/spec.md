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

### Requirement: Shared Koin Server Resolution
The duplicated transport-specific Koin resolution of the MCP server SHALL be replaced with one shared `Application.resolveServer(): Server` helper returning the Kotlin MCP SDK `Server`. Stdio, SSE, and Streamable HTTP SHALL use this helper. The helper SHALL log `"Failed to initialize MCP Server"` and rethrow on resolution failure, preserving current failure behavior. The implementation SHALL resolve the directly composed SDK server and SHALL not resolve a project-owned `McpServer` aggregate.

#### Scenario: Single SDK server resolution path
- **WHEN** any application transport needs the MCP server
- **THEN** it SHALL call `Application.resolveServer()`
- **AND** no transport SHALL contain its own Koin resolution try/catch
- **AND** the returned type SHALL be the SDK `Server`.

### Requirement: Documented Coroutine and Session Design
Non-obvious MCP server design decisions SHALL have adjacent comments that describe direct SDK `Server` composition, SDK-owned bounded tool-handler jobs, the on-demand per-session roots resolution, and SDK-first close through the shared lifecycle helper. Comments SHALL not describe a project wrapper, custom cancellation handler, detached tool scope, active-tool registry, server roots setter, or `onConnect` root registration. The `toKotlinxSerialization()` enum special case SHALL remain documented because schema-kenerator emits `enum` without `type`.

#### Scenario: Lifecycle rationale is documented without stale design
- **WHEN** a developer reads the direct server construction, on-demand root resolution, or `closeServer` declaration
- **THEN** adjacent comments SHALL explain the SDK ownership and SDK-first close rationale
- **AND** no removed workaround terminology SHALL remain in the documented design.
