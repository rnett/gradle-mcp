## 1. Init Script JDK Detection

- [x] 1.1 Add `JDK` line type emission to `dependencies-report.init.gradle.kts` — detect project's Java toolchain via `JavaPluginExtension.toolchain` + `JavaToolchainService`, falling back to Kotlin toolchain, then `Jvm.current().javaHome`
- [x] 1.2 Handle toolchain resolution failures gracefully — fall back to daemon JDK, don't fail the build
- [x] 1.3 Emit `JDK | projectPath | jdkHome | version` for each project with JVM-backed source metadata or buildscript classpath dependencies
- [x] 1.4 Emit model-backed JVM source-set metadata via `SOURCESET | projectPath | name | configurations | isJvm`

## 2. Dependency Report Data Model

- [x] 2.1 Add `jdkHome: String?` and `jdkVersion: String?` fields to `GradleProjectDependencies` in `GradleDependencyModels.kt`
- [x] 2.2 Parse `JDK` line type in `parseStructuredOutput()` in `GradleDependencyService.kt`
- [x] 2.3 Populate `jdkHome` and `jdkVersion` in the parsed `GradleProjectDependencies`
- [x] 2.4 Add and parse `isJvm` on source-set dependency models; default older source-set lines to non-JVM except buildscript

## 3. JdkSourceService Interface and Implementation

- [x] 3.1 Create `JdkSourceService` interface in `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/JdkSourceService.kt` with `resolveSources()` method returning `CASDependencySourcesDir?` (nullable for graceful degradation)
- [x] 3.2 Implement `DefaultJdkSourceService` with `src.zip` location logic checking `<javaHome>/lib/src.zip` then `<javaHome>/src.zip`
- [x] 3.3 Implement JDK source identification: compute SHA-256 hash of `src.zip` as the primary cache key; keep init-script `jdkVersion` as descriptive report metadata
- [x] 3.4 Implement source extraction using `ArchiveExtractor` with `skipSingleFirstDir = false`, caching to CAS directory `<cacheDir>/cas/v3/<hash>/sources/` (content-addressed by SHA-256 hash, same as other dependencies)
- [x] 3.5 Implement CAS completion marker creation at `<cacheDir>/cas/v3/<hash>/.base-completed` (same as other dependencies)
- [x] 3.6 Implement safe refresh logic: reuse completed CAS entries for `fresh`, rebuild completed entries for `forceDownload` under the base lock, repair incomplete entries under lock, and refresh dependent views/indexes
- [x] 3.7 ~~Implement conservative cleanup of non-essential directories after extraction~~ — REMOVED: JDK sources use CAS GC (same mark-and-sweep as other dependencies), no custom cleanup needed
- [x] 3.8 Implement `resolveSources()` returning `null` when `src.zip` is not found (graceful degradation, not exception)

## 4. Auto-Inclusion in Dependency Source Tools

- [x] 4.1 Update `read_dependency_sources` to expose JDK sources in the same session view as regular dependencies under `jdk/sources/...` (no separate fallback)
- [x] 4.2 Update `search_dependency_sources` to search the unified manifest-backed index (JDK sources are in the same session view as other dependencies)
- [x] 4.3 Ensure `gradleOwnSource=true` skips JDK source auto-inclusion (Gradle sources only)
- [x] 4.4 Ensure non-JVM source sets don't auto-include JDK sources

## 5. DI Registration

- [x] 5.1 Register `JdkSourceService` / `DefaultJdkSourceService` in `DI.kt` module
- [x] 5.2 Pass `JdkSourceService` to `DependencySourceTools` constructor
- [x] 5.3 Update `components()` function in `DI.kt` to include `JdkSourceService`

## 6. Indexing Integration

- [x] 6.1 Ensure `DefaultJdkSourceService.resolveSources()` returns a `CASDependencySourcesDir` with correct `sources/`, `index/`, and CAS markers
- [x] 6.2 Verify that the existing `IndexService` and `SearchProvider` infrastructure works with `CASDependencySourcesDir` returned by `JdkSourceService`

## 7. Testing

- [x] 7.1 Write unit tests for init script JDK detection (Java toolchain, Kotlin toolchain, daemon fallback, resolution failure)
- [x] 7.2 Write unit tests for `JDK` line parsing in `GradleDependencyService`
- [x] 7.3 Write unit tests for `src.zip` location logic (standard path, legacy path, not found → null)
- [x] 7.4 Write unit tests for JDK source identification (SHA-256 hash computation and init-script JDK report metadata)
- [x] 7.5 Write integration test for `JdkSourceService.resolveSources()` end-to-end (extraction, caching, and lazy provider indexing)
- [x] 7.6 Write tests for auto-inclusion behavior in `DependencySourceTools` (JDK in unified source tree, search in unified index, gradleOwnSource precedence, non-JVM skip, src.zip missing → graceful skip)

## 8. Documentation

- [x] 8.1 Update tool descriptions in `DependencySourceTools.kt` to document auto-inclusion of JDK sources for JVM-backed scopes
- [x] 8.2 Run `./gradlew :updateToolsList` to sync tool metadata
