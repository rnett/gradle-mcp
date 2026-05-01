## Why

Gradle 9.5.0 introduces task provenance information, telling users which plugin registered a task. The MCP server currently does not surface this provenance information in its task query tools, missing an opportunity to help users quickly
locate the source of tasks in complex builds with many plugins and subprojects.

## What Changes

- **Task report provenance**: The `query_build` tool's task output will include provenance information when available, sourced from the Tooling API's `TaskOperationDescriptor.getOriginPlugin()` (cast to `BinaryPluginIdentifier` →
  `getPluginId()`), showing which plugin registered each task
- **Tasks report provenance**: Support for the `--provenance` option in task listing via the `gradle` tool

## Capabilities

### New Capabilities

- `task-provenance-reporting`: Surface Gradle task provenance information (which plugin registered a task) in task queries and build reports via the Tooling API

### Modified Capabilities

## Impact

- **`query_build` tool**: Task output will include provenance information when available
- **`gradle` tool**: Task listing will support `--provenance`
- **No breaking changes**: Provenance is additive — existing consumers that don't use it will see no difference
