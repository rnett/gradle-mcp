## Context

Verified in the current tree:

- Fork concurrency is the only parallelism lever for the real-Gradle suites: `integrationTest` and `treeSitterTest` set `maxParallelForks = if (isCI) 3 else 8` (`build.gradle.kts:209/230`), while JUnit in-JVM parallelism is `same_thread` (`src/test/resources/junit-platform.properties:1-3`) and the unit-test `tasks.test` block only sets fixed parallelism via `systemProperty`.
- The session-view cache is per `SourcesService` instance: Caffeine bounded to 128 keys with a 30-minute idle TTL (`SourcesService.kt:159-168`, `MAX_CACHED_SESSION_VIEWS = 128L`, `CACHED_SESSION_VIEW_TTL = 30.minutes`). Every source-view miss launches a nested build of the `mcpDependencyReport` task (`GradleDependencyService.kt:368-375`).
- Real-server fixture classes create a fresh `GradleProvider` (+ `SourcesService`) per test instance: `ConfigurationCacheIntegrationTest.kt` and `GradleVersionResolutionIntegrationTest.kt` (e2e) extend `BaseMcpServerTest` and override `createProvider()`; `BaseReplIntegrationTest.kt` is `PER_CLASS` and overrides `createProvider()` returning `DefaultGradleProvider(...).withTestGradleDefaults()`.
- Change A's Phase 0 probes provide the baseline (daemon count, suite wall time, `mcpDependencyReport` launch count) that gates this change's fork decision.
- The active change `multi-language-tree-sitter-support` edits `build.gradle.kts` test JVM args (Task 5.2 adds `--enable-native-access=ALL-UNNAMED`) but not `maxParallelForks`, so the two changes touch different lines in the same file.
- External ownership is motivated by the per-method close chain: `BaseMcpServerTest.kt:125` (`cleanup()` closes the per-method server), `McpServerFixture.kt:103` (`close()` → `closeServer` closes every component), `GradleExecutionTools.kt:21` and `GradleBuildLookupTools.kt:34` (`close()` closes the injected provider), and `GradleProvider.kt:104` (`close()` cancels the provider scope behind an AtomicBoolean with no reopen) — together they make a per-method real provider terminal after the first method.

## Goals / Non-Goals

**Goals:**

- Cut repeated `mcpDependencyReport` launches by making the session-view cache hit across test methods in real-server fixture classes.
- Keep the fork-cap decision data-driven: 8 local / 3 CI initially; 4 local only if Phase 0 numbers justify it.
- Keep CI behavior unchanged.

**Non-Goals:**

- Changing JUnit in-JVM parallelism (`same_thread` stays).
- Merging suites or deleting tests.
- Touching production source code.

## Decisions

### 1. Fork cap stays 8 local / 3 CI; reduction is baseline-gated

**Decision**: Keep `maxParallelForks = if (isCI) 3 else 8` for `integrationTest`/`treeSitterTest` (`build.gradle.kts:209/230`). Add a baseline probe task (reusing Change A's Phase 0 probes) that measures suite wall time and peak daemon count. Only if the numbers justify it, reduce local forks to 4 and record the baseline in the change notes; CI stays 3 regardless.

**Rationale**: With daemon identity stabilized (Change A), peak daemon count tracks the fork count, so reducing forks is a real resource lever — but it trades wall time. The decision must be data-driven, not assumed; the design deliberately avoids an arbitrary fork reduction without measurements.

**Rejected alternative**: Reducing forks preemptively to 4 without a baseline. Rejected because wall-time regression risk is unquantified and the real build-volume win comes from the cache hits (Decision 2), not from fewer forks.

**Implementation outcome**: The `baselineProbe` task was added and run (both real-Gradle suites execute, report written to `build/probes/baseline-*.txt`). Measured baseline: combined suite wall time 1m52s; peak live-daemon count could not be sampled on this Windows host (process enumeration unavailable, reported as 0 — see the probe's note; daemon counts must be compared across equivalent environments). The numbers do not justify a fork reduction, so local `maxParallelForks` stays 8 and CI stays 3.

### 2. Class-scoped shared provider + SourcesService with external ownership

**Decision**: Refactor the real-server fixture classes to share one class-scoped `BuildManager`, `GradleProvider`, and `SourcesService` owned OUTSIDE the per-method server fixture:

- Add an externally-owned-component exclusion to `McpServerFixture` (e.g. `excludeFromClose: Set<KClass<out McpServerComponent>>`, default empty = today's behavior); its `close()` skips those components when calling `closeServer`. Sharing classes exclude `GradleExecutionTools` and `GradleBuildLookupTools`, whose `close()` implementations close the injected provider and its `BuildManager`. All other components keep their per-method close.
- `ConfigurationCacheIntegrationTest`: hold the shared `BuildManager` + real provider + real `SourcesService` in a companion-object holder (lazy creation; JUnit creates a fresh test instance per method), inject them into each per-method Koin module, and close provider + build manager exactly once in a companion `@AfterAll` (`@JvmStatic`). Per-method project fixtures and the mid-test server recreation stay unchanged; the recreation now survives because the shared components are excluded from fixture close.
- `GradleVersionResolutionIntegrationTest`: share the real `SourcesService` at class scope. The provider stays the inherited relaxed mock — `createProvider()` is not overridden there and no real builds run (`GradleDependencyService` is mocked) — so no provider ownership change applies.
- `BaseReplIntegrationTest`: already `PER_CLASS` with one class-scoped server; the Koin-`single` provider and `SourcesService` are already class-scoped and are closed by the single `server.close()` in the existing `@AfterAll`. No lifecycle change; document that subclasses inherit it.
- Keep `testing-standards` mandates intact for classes that run real builds: real `DefaultGradleProvider` (not relaxed mocks), `createProvider()` overridden.

**Rationale**: The session-view cache is per `SourcesService` instance, so sharing absorbs repeated scopes. But `BaseMcpServerTest.cleanup()` closes the per-method server, `closeServer` closes every component, and `GradleExecutionTools.close()` closes the injected provider — a terminal close (`DefaultGradleProvider.close()` cancels the scope behind an AtomicBoolean with no reopen). External ownership plus the close exclusion keeps the provider usable across methods while Change A's deterministic-close mandate moves to class teardown (`@AfterAll`, exactly once). `SourcesService` has no `close()`, so sharing it creates no lifecycle conflict.

**Rejected alternatives**: class-scoped-server restructure (broken by the intentional mid-test server recreation, larger churn); REPL-only scoping (forfeits the main win); production close change (leaks provider scopes in production; violates non-goal).

### 3. Sequencing with the active `multi-language-tree-sitter-support` change

**Decision**: Note in tasks that `build.gradle.kts` is shared with the active change. Coordinate edits: the active change touches test JVM args (lines ~253-259 `tasks.withType<Test>().configureEach`), this change touches `maxParallelForks` (lines 209/230); no textual conflict, but apply in one branch to avoid merge noise.

**Rationale**: Both changes are active in the same file; sequencing avoids double-handling at archive time.

## Risks / Trade-offs

- **Risk**: Class-scoped providers shared across methods could mask per-method isolation bugs. → **Mitigation**: Per-method provider remains allowed where isolation is genuinely required (spec scenario); the change only removes *unnecessary* per-method recreation in real-server fixtures.
- **Risk**: A class-scoped provider left unclosed leaks build/coroutine scopes. → **Mitigation**: Closed in `@AfterAll`; Change A's provider-lifecycle mandate applies.
- **Trade-off**: Keeping local forks at 8 caps the wall-time win from concurrency tuning; this is intentional — the win comes from cache hits, and fork reduction is gated on data.

## Migration Plan

N/A — test-infrastructure-only change.

## Open Questions

- None. The fork-count decision is explicitly deferred to the Phase 0 baseline data (a task gate, not a spec ambiguity).
