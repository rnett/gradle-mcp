# Proposal: Minimize MCP SDK Wrappers

## Why
The tool bridge carried project-owned context wrappers, roots capability machinery, and per-call serialization state around SDK primitives. Removing those layers gives each tool a small typed call contract, makes progress ownership and cleanup explicit, and leaves project-root selection deterministic.

## What Changes
- Replace `McpContext` and `McpToolContext` with typed tool inputs, `ProgressReporter` handler dependencies, and return-carried `ToolCallResult<O>`.
- Keep `CallToolRequest` inside the SDK adapter, with typed decoding and a registration-time output converter owned by `McpServerComponent.tool<I, O>`.
- Extract `ProgressNotificationPipeline` as the per-call progress and notification owner, preserving bounded, sampled, animated, correlated delivery and deterministic close semantics.
- Remove MCP roots support from production, fixtures, tests, and current manual/generated documentation.
- Resolve `GradleProjectRootInput` only from an explicit normalized path, then `GRADLE_MCP_PROJECT_ROOT`, otherwise fail with a clear `IllegalArgumentException`.
- Preserve component grouping, lifecycle, schema, decoding, exception, result-conversion, transport, Gradle connection, and isolated-Koin boundaries.

## Capabilities
### Modified Capabilities
- `mcp-sdk-wrapper-reduction`: Flatten the typed handler bridge, remove roots and wrapper state, retain `GradleConnectionService`, and preserve established server boundaries.
- `mcp-context-progress`: Define `ProgressNotificationPipeline` lifecycle, routing, suppression, bounded concurrency, and post-close behavior.
- `skill-and-tool-descriptions`: Replace root-discovery wording while preserving the complete structured-description standard.
- `tool-description-tuning`: Replace root auto-detection wording while preserving the general auto-detection standard.

## Impact
Implementation updates the tool bridge and handlers, progress infrastructure, project-root resolution, test fixtures and tests, and current manual/generated tool documentation. The SDK version, component model, schema machinery, Gradle connection service, transport ownership, and archived artifacts remain unchanged.
