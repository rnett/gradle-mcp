# 1. Tool Logic Implementation

- [x] 1.1 Create `GradleBuildLookupTools.kt` scaffolding for `QueryBuildArgs` and `WaitBuildArgs`
- [x] 1.2 Implement `QueryBuild` logic with auto-expansion for single matches
- [x] 1.3 Implement console regex filtering with line numbering
- [x] 1.4 Implement `WaitBuild` logic with mandatory tail output
- [x] 1.5 Update `ToolNames.kt` and `GradleBuildLookupTools.kt` to replace `inspect_build`

## 2. Infrastructure & Metadata

- [x] 2.1 Audit all tool/parameter `@Description` annotations
- [x] 2.2 Consolidate `TaskOutcome` and `TestOutcome` into `BuildComponentOutcome`
- [x] 2.3 Run `./gradlew :updateToolsList` to sync metadata

## 3. Documentation & Verification

- [x] 3.1 Update `AGENTS.md` and `docs/tools/EXECUTION_TOOLS.md`
- [x] 3.2 Update `BackgroundBuildStatusWaitTest`
- [x] 3.3 Verify regex-filtering and tail-first console pagination
- [x] 3.4 Verify auto-expansion UX with single and multiple matches
