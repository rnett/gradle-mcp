## Phase 0: Evidence and API gates
- [x] 0.1 Re-read the official 0.15.0 release, `0.14.0...0.15.0` compare, and PRs #884, #892, and #885; record links, symbols, findings, and the deletion/retention matrix in `design.md`.
- [x] 0.2 Confirm `Server.connect` and `onConnect` invocation and both Ktor plugins' `createSession` paths.
- [x] 0.3 Confirm SDK request IDs, `RequestHandlerExtra`, 64/256 handler bounds, built-in cancellation and response suppression, session registry, and that `Server.close` cancels but does not join handler jobs and has no one-shot guard.
- [x] 0.4 Confirm official client roots APIs, `listRoots` session behavior, and that `resolveRoot` preserves env fallback, implicit roots, explicit names, containment, and null-explicit semantics.

## Phase 1: Version bump and failing behavior tests
- [x] 1.1 Bump `mcpSdk` in `gradle/libs.versions.toml` from `0.14.0` to `0.15.0`, retaining `kotlin-sdk` and `kotlin-sdk-testing` alignment.
- [x] 1.2 Add ChannelTransport tests for a slow and quick call on one session using deferred milestones to prove: (1) quick completes while slow is in flight, (2) cancelling slow leaves quick successful, (3) a subsequent quick succeeds, and (4) slow observes cancellation.
- [x] 1.3 Add mandatory `SseCancellationE2ETest` with a real Ktor server, SDK `Client`, `SseClientTransport`, `CompletableDeferred`, cooperative cancellation assertion, and response suppression assertion.
- [x] 1.4 Add official client roots round-trip coverage for multiple clients; retain existing Streamable HTTP startup coverage.
- [x] 1.5 Add lifecycle-helper tests proving that SDK `Server.close()` is invoked first; components are attempted sequentially in original list order; one component-close failure does not prevent later closes; repeated cleanup is operationally safe but is not an idempotence or exactly-once guarantee; handler cancellation is observed through a separate timeout-bound signal; and helper return is not proof that SDK handler jobs joined.

## Phase 2: Direct composition and McpServer deletion
- [x] 2.1 Add `McpLifecycle.kt` with the shared `closeServer(server: Server, components: List<McpServerComponent>)` helper (SDK-first order, best-effort sequential component close).
- [x] 2.2 Modify `McpServerComponent.kt`, `McpContext.kt`, and `GradleInputs.kt` to pass explicit `Json` and execute tools inline, preserving `runCatchingExceptCancellation`.
- [x] 2.3 Modify `DI.kt`, `Application.kt`, and `UpdateTools.kt` to construct and resolve the SDK `Server` directly via Koin; use the shared lifecycle helper.
- [x] 2.4 Delete `src/main/kotlin/dev/rnett/gradle/mcp/mcp/McpServer.kt` entirely.
- [x] 2.5 Remove all `ToolCallRequestId`, `wrapTransport`, active-job registration, custom cancellation, detached tool/roots scopes, connect override, and root setters.
- [x] 2.6 Adopt direct SDK session management and update comments to describe direct composition and SDK-first close.

## Phase 3: Fixture and test migration
- [x] 3.1 Migrate `McpServerFixture`, `BaseMcpServerTest`, `GradleInputsTest`, `DIE2ETest`, and all root-setting callers to direct SDK `Server` resolution and shared `closeServer`.
- [x] 3.2 Replace reflection and server-root setters with official SDK client roots APIs; verify all pure-resolver branches and multi-client round trips through `Client.addRoot/addRoots/removeRoot/removeRoots` without `sendRootsListChanged()`.
- [x] 3.3 Ensure component close is repeat-safe and join fixture-owned scopes; use explicit generous real-time `runTest` timeouts.
- [x] 3.4 Run the regression suite and verify no project aggregate server owner or stale McpServer reference remains.

## Phase 4: Specification and documentation sync
- [x] 4.1 Add the ADDED `mcp-server-composition` capability with all seven requirements and scenarios.
- [x] 4.2 Rewrite the complete MODIFIED `post-upgrade-hygiene` requirements `Shared Koin Server Resolution` and `Documented Coroutine and Session Design`.
- [x] 4.3 Rewrite the complete MODIFIED `mcp-test-infrastructure` requirements `No Reflection Into SDK Internals` and `Deterministic Server and Fixture Teardown`.
- [x] 4.4 Update impacted `build-output-concurrency` documentation and ensure no normative text describes the deleted wrapper or custom cancellation.

## Phase 5: Verification and handoff artifacts
- [x] 5.1 Run `:updateToolsList`, review its generated tool diff, and keep only changes caused by the integrated upgrade.
- [x] 5.2 Run `:check` and resolve failures caused by the integrated change.
- [x] 5.3 Run `openspec validate integrate-kotlin-mcp-sdk-0-15 --type change --strict --no-interactive` and record the result.
- [x] 5.4 Prepare the authorized PR handoff with release links, evidence matrix, and test evidence.

## Phase 6: Authorized governance
- [x] 6.1 Every commit, push, or PR mutation requires explicit user authorization.
- [ ] 6.2 After the integrated change merges to main with green verification, close Renovate #233 as superseded; NEVER merge PR #233 itself.
