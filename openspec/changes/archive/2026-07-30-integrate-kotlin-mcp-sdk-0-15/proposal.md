## Why
Kotlin MCP SDK 0.15.0 makes the project-owned `McpServer.kt` workaround obsolete. The SDK now owns request identity, bounded concurrent dispatch, cooperative cancellation, response suppression, session registration, and handler teardown. Keeping a project server wrapper or replacement owner would duplicate lifecycle authority and leave SSE and Streamable HTTP on a different path from stdio.

This change therefore deletes `src/main/kotlin/dev/rnett/gradle/mcp/mcp/McpServer.kt` entirely. The application composes the SDK `Server` directly, while `McpServerComponent` remains the grouping boundary for tools. The change also replaces stale lifecycle and test-infrastructure requirements with guidance for direct composition, official client roots APIs, and SDK-first teardown.

## What Changes
- Bump the shared `mcpSdk` version from `0.14.0` to `0.15.0`, keeping the SDK and testing artifacts aligned.
- Delete `src/main/kotlin/dev/rnett/gradle/mcp/mcp/McpServer.kt` completely: remove `ToolCallRequestId`, transport wrapping, `activeToolCallJobs`, custom cancellation handling, detached scopes, `connect` overrides, and custom shutdown.
- Construct and resolve the SDK `Server` directly in DI and `Application` using exactly one `List<McpServerComponent>` value for both registration and teardown; pass explicit `Json` and `McpContext` carries SDK `Server`, `Json`, and `ClientConnection`.
- Replace `RootsState` with a per-call session match via `server.sessions[clientConnection.sessionId]`. Make `GradleProjectRootInput.resolve()` and `GradleDocsTools.resolveVersion` suspend and delegate to pure `resolveRoot(roots: Set<Root>?)`. Fixtures use official SDK client roots APIs; no `sendRootsListChanged()` or internal state.
- Add `McpLifecycle.kt` with a shared `closeServer(server: Server, components: List<McpServerComponent>)` helper that closes the SDK server first, then sequential best-effort component closes with per-component exception isolation.
- Prove SDK-owned inline execution, concurrent dispatch, cross-transport cancellation (with real SSE response suppression), and multi-client roots isolation.
- Synchronize the modified `post-upgrade-hygiene` and `mcp-test-infrastructure` requirements and add the `mcp-server-composition` capability.
- Run `:updateToolsList`, review generated changes, run `:check`, and validate this change with strict OpenSpec validation.

## Capabilities
- `mcp-server-composition` is added.
- `post-upgrade-hygiene` is modified with complete restatements of `Shared Koin Server Resolution` and `Documented Coroutine and Session Design`.
- `mcp-test-infrastructure` is modified with complete restatements of `No Reflection Into SDK Internals` and `Deterministic Server and Fixture Teardown`.

## Impact
- Production: delete `McpServer.kt`; add `McpLifecycle.kt`; modify `McpServerComponent.kt`, `McpContext.kt`, `GradleInputs.kt`, `DI.kt`, `Application.kt`, and `UpdateTools.kt`; bump `gradle/libs.versions.toml`.
- Tests: migrate `McpServerFixture`, `BaseMcpServerTest`, `GradleInputsTest`, `DIE2ETest`, and all root-setting callers; add ChannelTransport concurrency and cancellation isolation tests, real SSE E2E, and official roots round-trip coverage.
- Documentation and generated metadata: update `build-output-concurrency` documentation, then run and review `:updateToolsList` output.
- Retain schema normalization, explicit stdio options, and required transport startup coverage. Streamable HTTP cancellation is optional follow-up.
- Duplicate-registration and invalid-params behavior are incidental upstream behavior.

## Evidence and Governance
The implementation must preserve an evidence matrix using the official release page, compare view, and upstream PRs: release `https://github.com/modelcontextprotocol/kotlin-sdk/releases/tag/0.15.0`, compare `https://github.com/modelcontextprotocol/kotlin-sdk/compare/0.14.0...0.15.0`, PR #884 for request IDs, bounded concurrent dispatch, and built-in cancellation, PR #892 for cancellation-buffer cleanup, and PR #885 for the startup race correction. This matrix maps SDK behavior to every deletion/retention. No further roots or close design research is required; Phase 0 only re-confirms APIs/release mapping.

No commit, push, or PR mutation is part of this proposal. The eventual PR handoff requires explicit authorization. Renovate #233 may be closed as superseded only after the integrated change merges to main with green verification; it must never be merged itself. Rollback is one cohesive revert of the version bump, source and test migration, generated metadata, and synchronized specifications.
