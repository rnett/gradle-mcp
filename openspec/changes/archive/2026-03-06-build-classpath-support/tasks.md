## 1. Init Script Update

- [x] 1.1 Update `McpDependencyReportTask` in `dependencies-report.init.gradle.kts` to traverse `buildscript.configurations` in addition to `project.configurations`.
- [x] 1.2 Update `McpDependencyReportRenderer` in the init script to prefix buildscript configuration names with `buildscript:`.
- [x] 1.3 Ensure repositories from `buildscript` are also output with a `buildscript:` prefix or similar to avoid confusion.

## 2. Core Service Implementation

- [x] 2.1 Update `DefaultGradleDependencyService.kt` to handle parsing of `buildscript:` prefixed configurations.
- [x] 2.2 Update `SourcesService` to automatically include all reported configurations, including buildscript ones, in its source extraction logic.

## 3. Verification

- [x] 3.1 Verify `inspect_dependencies()` now reports `buildscript:classpath` and its dependencies.
- [x] 3.2 Verify `search_dependency_sources()` finds symbols from buildscript dependencies (e.g., Gradle plugins).
- [x] 3.3 Verify `read_dependency_sources()` can read sources from a `buildscript:` prefixed dependency.
- [x] 3.4 Run full project check via `./gradlew check` to ensure no regressions.
