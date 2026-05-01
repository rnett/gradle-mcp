## 1. Rename `gradleSource` to `gradleOwnSource`

- [ ] 1.1 Rename `gradleSource` parameter to `gradleOwnSource` in `ReadDependencySourcesArgs`
- [ ] 1.2 Rename `gradleSource` parameter to `gradleOwnSource` in `SearchDependencySourcesArgs`
- [ ] 1.3 Rename `gradleSource` parameter to `gradleOwnSource` in `resolveSources` private method
- [ ] 1.4 Update all references to `gradleSource` in tool description strings to `gradleOwnSource`
- [ ] 1.5 Update `docs/tools/PROJECT_DEPENDENCY_SOURCE_TOOLS.md` to reflect the rename

## 2. `gradleOwnSource` Parameter Descriptions

- [ ] 2.1 Update `@Description` on `gradleOwnSource` parameter in `ReadDependencySourcesArgs` to add negative trigger: "Do NOT use for running Gradle builds or tasks — use the `gradle` tool instead"
- [ ] 2.2 Update `@Description` on `gradleOwnSource` parameter in `SearchDependencySourcesArgs` to add negative trigger: "Do NOT use for running Gradle builds or tasks — use the `gradle` tool instead"

## 3. `gradle` Tool Description

- [ ] 3.1 Add "STRONGLY PREFERRED for all Gradle task execution" to the `gradle` tool description header
- [ ] 3.2 Add cross-reference to `query_build` for post-build diagnostics, test results, and task output
- [ ] 3.3 Add "When NOT to use" guidance pointing to `gradleOwnSource` tools for reading Gradle source code

## 4. `projectRoot` Parameter Description

- [ ] 4.1 Rewrite `GradleProjectRootInput` `@Description` to clarify auto-detection vs. explicit specification: "Auto-detected from MCP roots when only one project is open; specify explicitly for multi-root workspaces or when auto-detection
  fails"

## 5. Cross-References in Source Tools

- [ ] 5.1 Add cross-reference to `gradle` tool in `searchDependencySources` tool description: "To run Gradle tasks or builds, use the `gradle` tool"
- [ ] 5.2 Add cross-reference to `gradle` tool in `readDependencySources` tool description: "To run Gradle tasks or builds, use the `gradle` tool"

## 6. Update Tools List

- [ ] 6.1 Run `./gradlew :updateToolsList` to sync documentation after metadata changes
