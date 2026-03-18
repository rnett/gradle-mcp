## 1. Interface Updates

- [x] 1.1 Add `dependency` parameter to `ReadDependencySourcesArgs` in `DependencySourceTools.kt`.
- [x] 1.2 Add `dependency` parameter to `SearchDependencySourcesArgs` in `DependencySourceTools.kt`.
- [x] 1.3 Add `dependency` parameter to `InspectDependenciesArgs` in `GradleDependencyTools.kt`.
- [x] 1.4 Update tool descriptions for `read_dependency_sources`, `search_dependency_sources`, and `inspect_dependencies` to document the new `dependency` parameter with examples (noting it targets only the specific dependency, not its
  transitives).

## 2. Core Logic Implementation

- [x] 2.1 Update `SourcesService` interface to include an optional `dependencyFilter` parameter in `downloadAllSources`, `downloadProjectSources`, `downloadConfigurationSources`, and `downloadSourceSetSources`.
- [x] 2.2 Update `GradleDependencyService` to pass the `dependencyFilter` to the Gradle task as `-Pmcp.dependencyFilter=...`.
- [x] 2.3 Modify `dependencies-report.init.gradle.kts` to:
    - Parse `mcp.dependencyFilter` property.
    - Implement a matcher (G:A:V) within the init script.
    - Filter `targetComponents` in `McpDependencyReportTask.generateReportFor` before `gatherLatestVersions` and `gatherSources`.
- [x] 2.4 Implement dependency matching logic in `DefaultSourcesService` as a fallback or additional verification, parsing the filter string (supporting `G:A:V:Variant`, `G:A:V`, `G:A`, or `G`).
- [x] 2.5 Update `DefaultSourcesService` methods to apply the dependency filter *before* `processDependencies` is called.
- [x] 2.6 Refactor `DefaultSourcesService` to simplify single-dependency resolution:
    - Delete `SingleDependencyCache.kt` and remove `.single_dependency` caching logic entirely.
    - If `filteredDeps.size == 1` and filter is set, extract and index into `globalSourcesDir` directly and return `SingleDependencySourcesDir`.
    - Rely on fast Gradle resolution rather than caching the resolved version at the project level.
- [x] 2.7 Update `search` and `listPackageContents` in `DefaultSourcesService` or `DependencySourceTools` to correctly read from the isolated `globalSourcesDir` index when a single dependency is targeted.
- [x] 2.8 Remove `FileLockManager.withLocks` utility and the complex multi-locking attempt in `IndexService.mergeIndices` to prevent coroutine deadlocks.
- [x] 2.9 Add validation in `DefaultSourcesService` to throw an `IllegalArgumentException` with an informative message if the filter matches zero dependencies while the unfiltered scope was not empty.

## 3. Skill & Documentation Updates

- [x] 3.1 Update `searching_dependency_sources` skill to document the `dependency` parameter, providing examples of its use for targeted exploration.
- [x] 3.2 Explicitly note in the skill that the `dependency` filter targets only the specific library, NOT its transitive dependencies.
- [x] 3.3 Update `managing_gradle_dependencies` skill to document the `dependency` filter for `inspect_dependencies`.

## 4. Verification & Testing

- [ ] 4.1 Update existing tests in `DependencySourceToolsTest.kt` and `DefaultSourcesServiceTest.kt` to reflect the new architecture. Remove any `.single_dependency` mock checks.
- [ ] 4.2 Verify that providing a non-matching dependency filter returns the expected error message through the tool's error handling mechanism.
- [ ] 4.3 Add tests in `GradleDependencyToolsTest.kt` to verify the `dependency` parameter correctly filters `inspect_dependencies` output.
- [ ] 4.4 Run a full build and check with `./gradlew check` to ensure no regressions were introduced.
