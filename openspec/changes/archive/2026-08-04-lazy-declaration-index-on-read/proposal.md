## Why

`read_dependency_sources` always passes the `DeclarationSearch` provider to source resolution (`DependencySourceTools.kt:114`), so a first plain read can trigger a full JDK declaration-index build (~8-10k files, est. 20-50s) even when no search was requested. JDK indexing is not a verified suite-wide test cost (fixtures inject `NoJdkSourceService` — e.g., `BaseReplIntegrationTest.kt:186`) but it is a real production first-use cost: a plain read should never pay for an index it does not need.

## What Changes

- `read_dependency_sources` stops passing `DeclarationSearch` as the provider for plain reads — pass no provider instead
- Add an on-demand indexing entry point on `SourcesService` (e.g., `ensureProviderIndexed(view, DeclarationSearch)`) that materializes the declaration index only when the read actually needs symbol/package resolution (requested path not on disk → before `listPackageContents`/`listNestedPackageContents`), reusing `JdkSourceService`'s `ensureIndexed` machinery (`JdkSourceService.kt:257`); add a parallel on-demand entry point on GradleSourceService for gradleOwnSource reads (Gradle-owned sources are not session views), so every read scope affected by removing the shared provider argument recovers its declaration index on demand
- Plain file/dir reads index nothing; explicit `search_dependency_sources` behavior is unchanged; CAS entries remain recoverable from the session-view manifest after `CachedView` eviction
- Optional: a JUnit-tagged JDK-indexing benchmark excluded from the default suite to produce first-read/first-search baselines
- Tool description unchanged → `:verifyToolsList` suffices (run `:updateToolsList` only if description text changes)

## Capabilities

### Modified Capabilities
- `jdk-source-resolution`: "JDK Source Indexing" defers indexing until symbol/package resolution is required; "JdkSourceService Interface" wording updated for the on-demand indexing entry point

## Impact

- **Tools**: `DependencySourceTools.kt` — read path passes no provider (line ~114); package-exploration path (lines ~128-136) triggers on-demand indexing before `listPackageContents`/`listNestedPackageContents`
- **Services**: SourcesService.kt — new on-demand ensureProviderIndexed(view, provider) entry point; GradleSourceService.kt — new on-demand entry point for the gradle-sources storage (gradleOwnSource scope)
- **Reused machinery**: `JdkSourceService.ensureIndexed` (`JdkSourceService.kt:257`) and the existing `IndexService`/`SearchProvider` infrastructure
- **Specs untouched**: `cas-dependency-cache` and `multi-reader-search` (no requirement changes)
- **No test-infrastructure changes**
