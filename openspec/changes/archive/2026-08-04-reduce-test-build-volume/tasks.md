## 1. Fork Concurrency Probe and Decision Gate

- [x] 1.1 Keep local `maxParallelForks = 8` for `integrationTest`/`treeSitterTest` (`build.gradle.kts:209/230`) and CI at 3
- [x] 1.2 Add a baseline probe task (reusing Change A's Phase 0 probes) measuring suite wall time and peak daemon count for the integration suites
- [x] 1.3 Decision gate: only if the Phase 0 numbers justify it, reduce local forks to 4 and record the baseline in the change notes; CI stays 3
- [x] 1.4 Coordinate with the active change `multi-language-tree-sitter-support`: it edits `build.gradle.kts` test JVM args (not `maxParallelForks`) — apply both edits in one branch to avoid merge noise

## 2. Class-Scoped Real Providers and SourcesService

- [x] 2.1 Refactor `ConfigurationCacheIntegrationTest` to hold a class-scoped `BuildManager` + real `GradleProvider` + real `SourcesService` in a companion-object holder (lazy creation); inject them into each per-method Koin module; close provider + build manager exactly once in a companion `@AfterAll` (`@JvmStatic`); per-method fixture close must NOT close the shared components. Session-view cache hit target unchanged (Caffeine, 128 keys / 30-min TTL — `SourcesService.kt:159-168`)
- [x] 2.2 Extend `McpServerFixture` with the externally-owned-component exclusion (`excludeFromClose: Set<KClass<out McpServerComponent>>`, default empty = current behavior); its `close()` skips those components when calling `closeServer`; verify a fixture close with the exclusion leaves the shared provider usable for the next method (regression probe for the close chain)
- [x] 2.3 `GradleVersionResolutionIntegrationTest` — share the real `SourcesService` at class scope; the provider stays the inherited relaxed mock (no real builds run there — `GradleDependencyService` is mocked), so no provider ownership change applies
- [x] 2.4 `BaseReplIntegrationTest` — confirm/keep the existing class-scoped server + provider lifecycle (already `PER_CLASS`, single `server.close()` in the existing `@AfterAll`); document subclass inheritance; no mechanism change
- [x] 2.5 Keep the testing-standards real-provider mandate for classes that run real builds: real `DefaultGradleProvider` (not relaxed mocks), `createProvider()` overridden; per-method providers remain allowed only where isolation is genuinely required

## 3. Validation

- [x] 3.1 Run `ConfigurationCacheIntegrationTest` in full — including the mid-test server-recreation test — and verify second-and-later methods succeed
- [x] 3.2 Verify the `mcpDependencyReport` launch count drops vs baseline (daemon log / probe counters)
- [x] 3.3 Run the affected suites (`integrationTest`, targeted e2e/REPL classes) — no regressions

## 4. Lifecycle

- [x] 4.1 After implementation: apply the change, then archive with spec sync (archive is post-implementation — list as lifecycle expectation only)
