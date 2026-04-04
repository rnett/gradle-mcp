## 1. Path Layout Refactor

- [x] 1.1 Update `relativePrefix` in `GradleDependencyModels.kt` from `deps/$name/$version` to `$group/$name`.
- [x] 1.2 Update comment and deduplication logic in `SourcesService.kt` to reflect new layout.
- [x] 1.3 Add `.distinct()` to `resolveIndexDirs` in `SourceStorageService.kt` to ensure unique source cache index paths.

## 2. Remove "All Sources" Scope

- [x] 2.1 Delete `SourceScope.All` from `SourcesService.kt`.
- [x] 2.2 Remove `resolveAndProcessAllSources` interface definition and implementation.
- [x] 2.3 Update `DependencySourceTools.kt` to remove the fallback to all sources and throw `IllegalArgumentException` if `projectPath` is null.
- [x] 2.4 Update descriptions for `projectPath` parameter in `ReadDependencySourcesArgs` and `SearchDependencySourcesArgs`.

## 3. KMP Target Sources (Target Isolation)

- [x] 3.1 Add `v1Target` (or `targetSources`) property to `CASDependencySourcesDir.kt`.
- [x] 3.2 Update `dependencies-report.init.gradle.kts` and `GradleDependencyModels.kt` to extract and propagate "Available-At" metadata (mapping `externalVariant.owner.id` from common variants to platform targets) to explicitly detect
  relationships between common and target platform artifacts.
- [x] 3.3 Implement target extraction logic in `SourceStorageService.kt` (e.g., during `createSessionView` or `processCasDependency`) to copy only platform-specific files into `v1Target` by diffing against a common sibling artifact.
- [x] 3.4 Update `createSessionView` in `SourceStorageService.kt` to junction to `v1Target` instead of `normalizedDir` for a platform artifact if its common sibling is also present in the resolution map.
- [x] 3.5 Update `dependencies-report.init.gradle.kts` to exclude `*DependenciesMetadata` configurations.
- [x] 3.6 Add test to `ViewMergingIntegrationTest.kt` to ensure KMP target isolation eliminates redundant sources in the session view.

## 4. Test Updates and Final Verification

- [x] 4.1 Update path assertions in `DeclarationSearchIntegrationTest.kt` and `FullTextSearchIntegrationTest.kt`.
- [x] 4.2 Update path collision tests in `ViewMergingIntegrationTest.kt`.
- [x] 4.3 Replace `resolveAndProcessAllSources` with `resolveAndProcessProjectSources` in `SourcesServiceCachingTest.kt`.
- [x] 4.4 Run `./gradlew :updateToolsList` to sync auto-generated docs.
- [x] 4.5 Verify all tests pass with `./gradlew test integrationTest`.

## 5. Resolutions to Review Findings (2026-04-03)

- [x] 5.1 Implement E2E search validation for KMP common sources in `KmpSearchIntegrationTest.kt` (Finding 1).
- [x] 5.2 Remove skipping logic for common artifacts in `dependencies-report.init.gradle.kts` (Finding 2).
- [x] 5.3 Ensure `CONFIGURATION:` headers are always printed and handle `isInternal` correctly to fix ghost dependencies (Findings 3 & 10).
- [x] 5.4 Update indexing logic to use `normalized-target` when a common sibling exists instead of `normalized` (Finding 5).
- [x] 5.5 Sort dependencies so common artifacts process first to prevent worker starvation and deadlocks (Finding 6).