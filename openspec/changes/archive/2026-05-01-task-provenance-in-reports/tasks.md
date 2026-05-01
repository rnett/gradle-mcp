## 1. Data Model Changes

- [x] 1.1 Add optional `provenance` field to `TaskResult` in `Models.kt`

## 2. Build Execution Integration

- [x] 2.1 In `BuildExecutionService.handleTaskFinish()`, extract provenance from `TaskOperationDescriptor.getOriginPlugin()` (cast to `BinaryPluginIdentifier` → `getPluginId()`) and store in `TaskResult`
- [x] 2.2 Ensure provenance is preserved when `RunningBuild.toFinishedBuild()` converts task results

## 3. query_build Tool Updates

- [x] 3.1 Add provenance display to task detail output in `GradleBuildLookupTools.kt` (`getTasksOutput()`)

## 4. gradle Tool Updates

- [x] 4.1 Ensure `--provenance` flag is supported and passed through for the `tasks` report in `GradleExecutionTools.kt` (note: passthrough already works via `commandLine`/`additionalArguments`; this task is about explicit documentation and
  testing)

## 5. Testing

- [x] 5.1 Add unit tests for provenance extraction from `TaskOperationDescriptor.getOriginPlugin()` (cast to `BinaryPluginIdentifier` → `getPluginId()`)
- [x] 5.2 Add unit tests for provenance display in task details
- [x] 5.4 Verify `--provenance` flag passthrough works correctly
