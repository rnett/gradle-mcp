## Why

The integration test suite repeatedly re-runs the `mcpDependencyReport` build: every source-view cache miss launches a nested Gradle build, and several real-server fixture classes create a fresh `GradleProvider` + `SourcesService` per test method, so the per-instance session-view cache (Caffeine, bounded to 128 keys, 30-minute idle TTL — `SourcesService.kt:159-168`) never hits across methods. Fork concurrency is also the only parallelism lever (JUnit in-JVM parallelism is `same_thread` — `src/test/resources/junit-platform.properties:1-3`), and with daemon identity stabilized by Change A, peak daemons track the fork count.

## What Changes

- Keep local `maxParallelForks` for `integrationTest`/`treeSitterTest` at 8 initially (`build.gradle.kts:209/230`); add a baseline probe task that, if the numbers justify it, reduces local forks to 4. CI stays 3
- Use class-scoped shared real `GradleProvider` + `SourcesService` in real-server fixture classes so the session-view cache hits across test methods; close class-scoped providers in `@AfterAll`
- Document sequencing with the active `multi-language-tree-sitter-support` change (it edits test JVM args in `build.gradle.kts` — e.g., `--enable-native-access=ALL-UNNAMED` — not `maxParallelForks`, so no conflict, but coordinate the shared file)

## Capabilities

### Modified Capabilities
- `test-environment-optimization`: Adds a requirement that real-Gradle suites bind fork concurrency per environment (local vs CI) and share real providers at class scope so repeated `mcpDependencyReport` builds are cut

## Impact

- **Build config**: `build.gradle.kts:209/230` (fork-cap decision gate; CI stays 3)
- **Test fixtures**: real-server fixture classes share class-scoped components: ConfigurationCacheIntegrationTest.kt shares provider + SourcesService; GradleVersionResolutionIntegrationTest.kt (e2e) shares only SourcesService (its provider stays the inherited relaxed mock — no real builds run); BaseReplIntegrationTest.kt keeps its existing class-scoped lifecycle
- **No production code changes**
