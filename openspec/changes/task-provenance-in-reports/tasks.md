## 1. Data Model Changes

- [ ] 1.1 Add optional `provenance` field to `TaskResult` in `Models.kt`
- [ ] 1.2 ~~Add provenance parsing utility function (regex-based) for extracting provenance from failure message strings~~ — **Removed**: No rendered output parsing

## 2. Build Execution Integration

- [ ] 2.1 In `BuildExecutionService.handleTaskFinish()`, extract provenance from `TaskOperationDescriptor.getOriginPlugin()` (cast to `BinaryPluginIdentifier` → `getPluginId()`) and store in `TaskResult`
- [ ] 2.2 Ensure provenance is preserved when `RunningBuild.toFinishedBuild()` converts task results

## 3. query_build Tool Updates

- [ ] 3.1 Add provenance display to task detail output in `GradleBuildLookupTools.kt` (`getTasksOutput()`)
- [ ] ~~3.2 Include provenance in build failure summary "Recent Error Context" section in `GradleOutputs.kt`~~ — **Removed**: No rendered output parsing

## 4. gradle Tool Updates

- [ ] 4.1 Ensure `--provenance` flag is supported and passed through for the `tasks` report in `GradleExecutionTools.kt` (note: passthrough already works via `commandLine`/`additionalArguments`; this task is about explicit documentation and
  testing)

## 5. Testing

- [ ] 5.1 Add unit tests for provenance extraction from `TaskOperationDescriptor.getOriginPlugin()` (cast to `BinaryPluginIdentifier` → `getPluginId()`)
- [ ] 5.2 Add unit tests for provenance display in task details
- [ ] 5.3 ~~Add unit tests for provenance in failure summaries~~ — **Removed**: No rendered output parsing
- [ ] 5.4 Verify `--provenance` flag passthrough works correctly
