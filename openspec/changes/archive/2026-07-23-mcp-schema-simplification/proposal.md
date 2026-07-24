## Why

The MCP context layer contains two extension functions (`toInput()` and `toOutput()`) on `CompiledJsonSchemaData` that share approximately 90% of their logic converting schema-kenerator JSON Schema output into MCP SDK `ToolSchema` values. This duplication creates maintenance burden, and the codebase has not yet leveraged MCP SDK 0.14.0's native `$defs` support. Additionally, a non-obvious workaround in `toKotlinxSerialization()` lacks documentation.

## What Changes

- Consolidate `toInput()` and `toOutput()` into a single shared `toToolSchema(): ToolSchema?` function on `CompiledJsonSchemaData`. Both existing extensions become thin delegates: `toInput()` returns `toToolSchema() ?: error("Object schema expected")`, `toOutput()` returns `toToolSchema()`.
- Evaluate and update the conversion to preserve `$defs` from schema-kenerator output through to the resulting `ToolSchema`, leveraging MCP SDK 0.14.0 native support.
- Add an explanatory comment documenting why `toKotlinxSerialization()` injects `"type": "string"` when an enum schema lacks a `type` field — a schema-kenerator quirk.

## Capabilities

### Modified Capabilities
- `mcp-schema-simplification`: Requirement updated — mandates exactly one shared `toToolSchema()` conversion path instead of two separate functions; adds explicit `$defs` preservation requirement.

## Impact

- `src/main/kotlin/dev/rnett/gradle/mcp/mcp/McpContext.kt` — `toInput()`, `toOutput()`, `toToolSchema()`, and `toKotlinxSerialization()` functions.
- No public API changes; `toInput()` and `toOutput()` remain as backward-compatible thin delegates.
- Consumers of these internal conversions are unaffected.
