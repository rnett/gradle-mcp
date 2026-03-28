## 1. Data Model Refinement

- [x] 1.1 Update `TestResult` to include `suiteName` and separate it from `testName`.
- [x] 1.2 Update `TestCollector` to populate the new `suiteName` field.
- [x] 1.3 Update existing test fixtures and mocks to match the refined `TestResult` model.

## 2. Enhanced Build Summaries

- [x] 2.1 Implement `toOutputString` logic for suite-based test grouping.
- [x] 2.2 Add "Recent Error Context" and "Active Tasks" to the build output summary.
- [x] 2.3 Update `GradleBuildLookupTools` to show detailed summaries in `summary` mode when a build ID is provided.

## 3. Improved Task and Output Feedback

- [x] 3.1 Implement "active operation" awareness in `inspect_build` and task lookup.
- [x] 3.2 Add timeout warning and running/executed task feedback to `captureTaskOutput`.
- [x] 3.3 Improve error messages for missing task paths with suggested executed tasks.

## 4. Advanced Pagination

- [x] 4.1 Update `paginate` and `paginateText` to support "tail" mode metadata.
- [x] 4.2 Refactor `inspect_build` console output to use common pagination logic.

## 5. Precise Symbol Search

- [x] 5.1 Update `DECLARATION` search to support `name` and `fqn` field filtering.
- [x] 5.2 Implement non-tokenized FQN matches and regex-based searching in `fqn` field.
- [x] 5.3 Update `DependencySourceTools` tool description with detailed search mode instructions.

## 6. Verification and Cleanup

- [x] 6.1 Run and verify all updated test suites (`FinishedBuildTest`, `GradleBuildLookupPrefixTest`, etc.).
- [x] 6.2 Add new integration test for suite grouping (`TestReportingTest`).
- [x] 6.3 Final review of documentation and tool descriptions.
