## 1. Interface and Model Updates

- [x] 1.1 Update `SourcesService` interface methods (`downloadAllSources`, `downloadProjectSources`, `downloadConfigurationSources`, `downloadSourceSetSources`) to include a `fresh: Boolean` parameter.
- [x] 1.2 Update `ReadDependencySourcesArgs` in `DependencySourceTools.kt` to include `fresh: Boolean = false`.
- [x] 1.3 Update `SearchDependencySourcesArgs` in `DependencySourceTools.kt` to include `fresh: Boolean = false`.
- [x] 1.4 Update the tool descriptions in `DependencySourceTools.kt` for `read_dependency_sources` and `search_dependency_sources` to include a strong recommendation to set `fresh = true` if the project dependencies have changed since the
  last refresh.
- [x] 1.5 Update the tool descriptions to recommend reading the source root directory as a quick check for current library availability.

## 2. Implementation Updates

- [x] 2.1 Update `DefaultSourcesService.kt` to check if `fresh` is `false` and the `SourcesDir.sources` directory exists for each download method. If so, return the `SourcesDir` directly.
- [x] 2.2 Update `DependencySourceTools.kt` tool calls to pass the `fresh` argument from the user input down to the `SourcesService`.
- [x] 2.3 Implement persistent timestamp storage in `DefaultSourcesService.kt` (e.g., a `.last_refresh` file).
- [x] 2.4 Update `DependencySourceTools.kt` to retrieve and format the last refresh timestamp from `SourcesService` and prepend it to the tool output.

## 3. Verification and Testing

- [x] 3.1 Create a new test in `DependencySourceToolsTest.kt` to verify that when `fresh = false` and sources are already cached, the Gradle dependency resolution is skipped.
- [x] 3.2 Create `DefaultSourcesServiceTest.kt` to verify `fresh` parameter logic and persistent timestamp storage in `DefaultSourcesService`.
- [x] 3.3 Verify that the tool output correctly displays the last refresh timestamp and elapsed time.
- [x] 3.4 Verify that when `fresh = true` (default), the full resolution still occurs.
- [x] 3.5 Verify that when `fresh = false` but no cache exists, the full resolution still occurs.
- [x] 3.6 Run `./gradlew :check` to ensure overall project integrity and documentation updates.
