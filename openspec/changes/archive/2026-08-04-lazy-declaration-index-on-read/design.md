## Context

Verified in the current tree:

- `read_dependency_sources` (DependencySourceTools.kt:101-116) passes `searchProviders.filterIsInstance<DeclarationSearch>().firstOrNull()` (line 114) into `resolveSources(..., providerToIndex)` (lines 370-390), which forwards `providerToIndex` to `SourcesService.resolveAndProcess*` (SourcesService.kt:71-100) and to `GradleSourceService`/`JdkSourceService`. A non-null provider forces declaration indexing during resolution even for a plain read.
- `JdkSourceService.resolveSources()` indexes when `providerToIndex != null` (JdkSourceService.kt:128-130) via the private `ensureIndexed(casDir, providerToIndex, force)` (JdkSourceService.kt:257) under the CAS base/index advisory locks.
- Package exploration in the read tool (`DependencySourceTools.kt:128-136`) calls `indexService.listPackageContents` / `listNestedPackageContents`, which require the declaration index — this is the only read case that actually needs the index.
- JDK declaration indexing is estimated at ~20-50s first-time (8-10k `.java` files) and is not a verified suite-wide test cost: fixtures inject `NoJdkSourceService` (e.g., BaseReplIntegrationTest.kt:186).
- `cas-dependency-cache` and `multi-reader-search` specs describe the CAS/locking/index model; neither requirement changes here.

## Goals / Non-Goals

**Goals:**

- Plain reads never trigger declaration indexing.
- Package/symbol resolution still works: the index is materialized on demand, reusing the existing `ensureIndexed` machinery.
- `search_dependency_sources` behavior is unchanged.
- CAS entries stay recoverable from the session-view manifest after `CachedView` eviction (no new cleanup or locking requirements).

**Non-Goals:**

- Changing the CAS layout, locking model, or Lucene index format (`cas-dependency-cache`, `multi-reader-search` stay untouched).
- Changing tool schemas or tool description text (so `:updateToolsList` is not expected; `:verifyToolsList` should still pass).
- Any change to `search_dependency_sources` semantics.

## Decisions

### 1. Plain reads pass no provider

**Decision**: In `DependencySourceTools.kt`, `read_dependency_sources` stops passing `searchProviders.filterIsInstance<DeclarationSearch>().firstOrNull()` (line 114) to `resolveSources` and passes `null` instead, so resolution never builds the declaration index for a plain read. This applies to every read scope of resolveSources (DependencySourceTools.kt:382-388), including gradleOwnSource, which today receives its indexing through the forwarded provider (DependencySourceTools.kt:383).

**Rationale**: The only read cases needing the declaration index are package explorations (path not on disk). Passing `null` makes resolution pure extraction/caching for file and directory reads — exactly the lazy behavior the design targets. `search_dependency_sources` keeps passing its requested provider, so search is unaffected.

### 2. On-demand indexing entry point on `SourcesService`

**Decision**: Add an on-demand entry point on `SourcesService` — e.g., `ensureProviderIndexed(view: SourcesDir, provider: SearchProvider)` — that materializes the requested provider's index for a resolved session view (dependency session views resolved through SourcesService, including their JDK CAS entries), reusing `JdkSourceService`'s `ensureIndexed` machinery (and the same granular advisory-lock pattern) so the index is built once and shared via the CAS entry.

**Rationale**: The session view already references CAS entries; the index lives in the CAS entry's `index/` directory, so indexing is idempotent and lock-guarded. Exposing an explicit entry point keeps the "only index when needed" decision at the call site (the package-exploration branch) rather than threading providers through the whole resolution chain.

### 3. Package exploration triggers the on-demand entry point

**Decision**: In the read tool's package-exploration branch (`DependencySourceTools.kt:128-136`), before calling `listPackageContents`/`listNestedPackageContents`, invoke the scope-appropriate on-demand entry point so the declaration index exists when the package query runs: for `gradleOwnSource`, the `GradleSourceService` entry point (Decision 5); for every other scope, `SourcesService.ensureProviderIndexed` (Decision 2).

**Rationale**: This is the exact point where symbol resolution is required; the cost is paid only when the agent actually explores a package. The dispatch mirrors `resolveSources`'s own scope branches, so every scope the removed provider argument touched gets an on-demand recovery path.

### 4. Optional opt-in JDK-indexing benchmark

**Decision**: Add an optional, JUnit-tagged JDK-indexing benchmark (excluded from the default suite) producing first-read and first-search baselines, so the lazy behavior's savings can be measured.

**Rationale**: The design treats the JDK index cost estimate as plausible but unverified; the benchmark is opt-in and does not affect default suite runtime.

### 5. Gradle-owned sources get their own on-demand indexing entry point

**Decision**: The on-demand indexing path is scope-appropriate. Dependency session-view scopes (including their JDK CAS entries) use `SourcesService.ensureProviderIndexed` (Decision 2). Gradle-owned sources are not session views: `GradleSourceService` maintains its own `gradle-sources/<version>` storage (`GradleSourceService.kt:58-73`) and creates a declaration index only when a provider is passed (`GradleSourceService.kt:79-91`), and `SourcesService` holds no reference to `GradleSourceService`. Add an on-demand entry point on `GradleSourceService` itself — e.g., `ensureIndexed(sources: MergedSourcesDir, provider: SearchProvider)` — that ensures the provider's declaration index for an already-resolved Gradle-owned sources directory under the service's existing exclusive lock, reusing `indexInternal` (`GradleSourceService.kt:156-163`). It SHALL NOT re-download: by the time the package-exploration branch runs, `getGradleSources` has already resolved the sources (cached or downloaded). Plain file/dir reads stay index-free and `search_dependency_sources` stays unchanged in this scope as in all others.

**Rationale**: Removing the shared provider argument (`DependencySourceTools.kt:114`) affects every read scope, and `gradleOwnSource` today gets its declaration index only via the forwarded provider (`DependencySourceTools.kt:383`). Without a replacement, a not-yet-indexed Gradle-owned package read resolves a `MergedSourcesDir` with no declaration index, and `DeclarationSearch.listPackageContents` returns `null` when no index directory exists (`DeclarationSearch.kt:80-81`) — degrading the read from package contents to "Path not found." A service-local entry point keeps ownership of the `gradle-sources` storage, `.completed` marker, and lock file inside `GradleSourceService`, mirroring the JDK model where the on-demand machinery lives on the service that owns the storage.

## Risks / Trade-offs

- **Risk**: A regression where package exploration forgets to trigger on-demand indexing would return stale/missing symbols. → **Mitigation**: The on-demand call sits immediately before the package queries in the same branch; tests cover the "plain read leaves no index markers" and "package exploration triggers indexing" scenarios.
- **Risk**: On-demand indexing during a read blocks the request with the full index cost. → **Mitigation**: This is the same cost as today's behavior for package reads (which already required the index), and plain reads no longer pay at all.
- **Trade-off**: The entry point is a small new API on `SourcesService`; it is internal to the dependency-source services and does not change tool schemas.
- **Risk**: The scope dispatch at the package-exploration branch misses a read scope. → **Mitigation**: resolveSources has exactly four scope branches (gradleOwnSource, sourceSetPath, configurationPath, projectPath — DependencySourceTools.kt:382-388); validation covers a not-yet-indexed Gradle-owned package read plus the dependency/JDK package-read coverage (tasks 4.2, 4.5, 4.6).

## Migration Plan

N/A — behavior-preserving for searches; plain reads become strictly cheaper.

## Open Questions

- None blocking. The naming of the on-demand entry points (`SourcesService.ensureProviderIndexed`, and the `GradleSourceService` entry point) is an implementation detail within the delegated task scope. The carried implementation gap remains task 2.1a: `JdkSourceService.ensureIndexed` is `private` in `DefaultJdkSourceService` (`JdkSourceService.kt:257`) and needs interface exposure for the on-demand entry point; exact signature/visibility is implementation-scoped.
