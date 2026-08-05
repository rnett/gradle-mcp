## 1. Plain Reads Stop Requesting Declaration Indexing

- [x] 1.1 In `DependencySourceTools.kt`, stop passing `searchProviders.filterIsInstance<DeclarationSearch>().firstOrNull()` (line ~114) to `resolveSources` for `read_dependency_sources`; pass `null` instead; this stops indexing in every read scope, including gradleOwnSource (the provider is shared by all resolveSources branches, DependencySourceTools.kt:382-388)
- [x] 1.2 Keep `search_dependency_sources` passing its requested provider (unchanged behavior)

## 2. On-Demand Declaration Indexing Entry Point

- [x] 2.1 Add an on-demand entry point on `SourcesService` (e.g., `ensureProviderIndexed(view: SourcesDir, provider: SearchProvider)`) that materializes the declaration index only when needed, reusing `JdkSourceService`'s `ensureIndexed` machinery (`JdkSourceService.kt:257`) and its granular advisory-lock pattern
  - [x] 2.1a Expose on-demand indexing on `JdkSourceService` so the `SourcesService` entry point can reach the `ensureIndexed` machinery (`ensureIndexed` is currently `private` in `DefaultJdkSourceService`, `JdkSourceService.kt:257`); exact signature/visibility is implementation-scoped
- [x] 2.2 Wire the package-exploration path: when the requested read path is not on disk (DependencySourceTools.kt:128-136), invoke the scope-appropriate on-demand entry point before listPackageContents/listNestedPackageContents — the GradleSourceService entry point (task 2.4) for gradleOwnSource, SourcesService.ensureProviderIndexed (task 2.1) for every other scope
- [x] 2.3 Confirm no new cleanup/locking requirements: CAS entries remain recoverable from the session-view manifest after `CachedView` eviction (`cas-dependency-cache`, `multi-reader-search` specs untouched)
- [x] 2.4 Add an on-demand entry point on GradleSourceService (e.g., ensureIndexed(sources: MergedSourcesDir, provider: SearchProvider)) that ensures the provider's declaration index for an already-resolved gradle-sources directory under the existing exclusive lock, reusing indexInternal (GradleSourceService.kt:156-163); no re-download (resolution already completed before the package-exploration branch runs). Exact signature/visibility is implementation-scoped

## 3. Optional Opt-In Benchmark

- [x] 3.1 Add a JUnit-tagged JDK-indexing benchmark (excluded from the default suite) producing first-read and first-search baselines

## 4. Validation

- [x] 4.1 Verify a plain JDK read (`read_dependency_sources` with `dependency: "jdk"`, existing file path) leaves no declaration index markers
- [x] 4.2 Verify package exploration triggers on-demand indexing and returns correct symbols
- [x] 4.3 Verify `search_dependency_sources` behavior is unchanged — including that a search against a not-yet-indexed scope still triggers indexing (marker file appears under the CAS entry's `index/` dir) and returns identical results
- [x] 4.4 Run `./gradlew :verifyToolsList` (tool description unchanged); run `:updateToolsList` only if description text changes
- [x] 4.5 Verify a plain gradleOwnSource read (existing file or directory path) leaves no declaration index markers under gradle-sources/<version>/metadata/index/
- [x] 4.6 Verify a not-yet-indexed gradleOwnSource package read indexes on demand and returns the same package contents as the pre-change behavior

## 5. Lifecycle

- [x] 5.1 After implementation: apply the change, then archive with spec sync (archive is post-implementation — list as lifecycle expectation only)
