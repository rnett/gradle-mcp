## 1. Init Script Changes

- [x] 1.1 Add `mcp.excludeBuildscript` property reading to `dependencies-report.init.gradle.kts`.
- [x] 1.2 Update `buildscriptConfigs` filtering logic in the init script to respect `mcp.excludeBuildscript` unless explicitly overridden by `mcp.sourceSet` or `mcp.configuration`.
- [x] 1.3 Update `outputSourceSets` in the init script to always emit a virtual `buildscript` source set containing the buildscript configuration names.

## 2. Dependency Service Changes

- [x] 2.1 Update `DefaultGradleDependencyService.downloadAllSources` to pass `-Pmcp.excludeBuildscript=true` to Gradle.
- [x] 2.2 Update `DefaultGradleDependencyService.downloadProjectSources` to pass `-Pmcp.excludeBuildscript=true` to Gradle.
- [x] 2.3 Update `DefaultGradleDependencyService.getSourceSetDependencies` to handle the `buildscript` virtual source set gracefully without trying to find it in the real project's `SourceSetContainer`.

## 4. Tool Handler and Skill Updates

- [x] 4.1 Update tool descriptions in `DependencySourceTools.kt` to mention the virtual `buildscript` source set approach.
- [x] 4.2 Update `PROJECT_DEPENDENCY_SOURCE_TOOLS.md` to document how to access buildscript dependencies using `sourceSetPath: ":buildscript"`.
- [x] 4.3 Update `searching_dependency_sources` skill (SKILL.md) to explain the new default behavior and how to search plugins.
- [x] 4.4 Update `gradle_expert` skill (SKILL.md) to reflect the virtual source set approach for plugin research.
- [x] 4.5 Run `./gradlew :updateToolsList` to synchronize documentation.

## 4. Verification

- [x] 4.1 Verify `inspect_dependencies` still shows `buildscript` configurations by default.
- [x] 4.2 Verify `search_dependency_sources` no longer shows buildscript dependencies by default.
- [x] 4.3 Verify `search_dependency_sources` with `sourceSetPath: ":buildscript"` works correctly.
- [x] 4.4 Run `./gradlew test` to ensure no regressions.
