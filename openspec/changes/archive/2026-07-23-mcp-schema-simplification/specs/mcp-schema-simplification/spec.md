## MODIFIED Requirements

### Requirement: Single Shared ToolSchema Conversion

**Status**: No change required. The existing spec at `openspec/specs/mcp-schema-simplification/spec.md` already defines the target-state requirement exactly: consolidation into `toToolSchema(): ToolSchema?` with thin delegate `toInput()` and `toOutput()`. This change implements that requirement.

### Requirement: Leverage SDK `$defs` Support

**Status**: No change required. The existing spec already mandates `$defs` pass-through or an explicit documented limitation. Implementation validates against MCP SDK 0.14.0 `ToolSchema` behavior to confirm natural propagation.

### Requirement: Documented schema-kenerator Quirks

**Status**: No change required. The existing spec already requires an explanatory comment on the enum-without-type special case. This change adds the comment in `toKotlinxSerialization()`.
