## 1. Foundation & Services

- [x] 1.1 Create the `GradleVersionService` interface in `src/main/kotlin/dev/rnett/gradle/mcp/`.
- [x] 1.2 Implement `DefaultGradleVersionService` with logic to fetch the latest stable version from `https://services.gradle.org/versions/current`.
- [x] 1.3 Add Koin registration for the new `GradleVersionService`.
- [x] 1.4 Implement in-memory caching for version resolution within `DefaultGradleVersionService`.

## 2. Docs Tool Refactoring

- [x] 2.1 Update `GradleDocsService` to depend on `GradleVersionService`.
- [x] 2.2 Refactor `DefaultGradleDocsService.resolveVersion` to delegate `"current"` resolution to the new service.
- [x] 2.3 Ensure `ensurePrepared` and `indexer.ensureIndexed` consistently use concrete version strings.
- [x] 2.4 Remove legacy HTML scraping logic for version resolution from `DefaultGradleDocsService`.

## 3. UI & Feedback Improvements

- [x] 3.1 Update `GradleDocsTools.kt` to fetch and display the resolved version in the tool output header when `"current"` is used.
- [x] 3.2 Audit and update other tool files (e.g., source tools) if they use `"current"` literals for caching.

## 4. Verification & Testing

- [x] 4.1 Add unit tests for `DefaultGradleVersionService` covering successful resolution, network errors, and caching.
- [x] 4.2 Create an integration test to verify that calling documentation tools with `"current"` creates a versioned cache directory (e.g., `.../8.6.1/`) rather than a literal `"current/"` directory.
- [x] 4.3 Run full `gradle check` to ensure no regressions in existing docs/source tools.
