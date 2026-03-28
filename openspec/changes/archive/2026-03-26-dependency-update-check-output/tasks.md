## 1. Fix [UPDATE CHECK SKIPPED] Noise

- [x] 1.1 In `dependencies-report.init.gradle.kts`, add `checksEnabled` field and `isUpdateChecked(group, name)` private helper to `McpDependencyReportRenderer`; replace the duplicated two-liner in both render paths
- [ ] 1.2 Verify via integration test that running `inspect_dependencies` with default `onlyDirect=true` and `checkUpdates=true` produces no `[UPDATE CHECK SKIPPED]` annotations on transitive deps (requires a real Gradle project; not
  covered by unit tests). **Deferred**: the init-script path is not unit-testable; track as a follow-up integration test task.

## 2. Simplify updatesOnly Output

- [x] 2.1 In `GradleDependencyTools.kt`, update `formatUpdatesSummary` to remove per-configuration and per-source-set columns — show only `group:artifact: currentVersion → latestVersion` and the list of project paths
- [x] 2.2 Verify the simplified output with a multi-project build that has multiple updatable deps

## 3. Tests & Verification

- [x] 3.1 Add unit tests for the Kotlin renderer's `[UPDATE CHECK SKIPPED]` suppression logic (tests the `checksUpdates && !dep.updatesChecked` path directly)
- [x] 3.2 Add unit tests for the simplified `formatUpdatesSummary` output format, including deduplication, empty-result, and `checksUpdates=false` suppression cases
- [x] 3.3 Run `./gradlew test` and confirm all tests pass

## 4. Post-Change Housekeeping

- [x] 4.1 Run `./gradlew :updateToolsList` to regenerate `docs/tools/PROJECT_DEPENDENCY_TOOLS.md` after tool description changes
