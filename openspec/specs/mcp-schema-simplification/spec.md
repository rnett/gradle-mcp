# Capability: mcp-schema-simplification

## Purpose

Defines the standards for converting schema-kenerator JSON Schema output into MCP SDK `ToolSchema` values, mandating a single shared conversion path, review of `$defs` support, and documentation of schema-kenerator quirks.

## Requirements

### Requirement: Single Shared ToolSchema Conversion

The conversion from schema-kenerator's `CompiledJsonSchemaData` to the MCP SDK's `ToolSchema` SHALL be implemented exactly once as `fun CompiledJsonSchemaData.toToolSchema(): ToolSchema?`, which returns `null` when the schema's top-level `type` is not `"object"`. The existing `toInput()` and `toOutput()` extensions in `McpContext.kt` SHALL be thin delegates over `toToolSchema()` and SHALL NOT duplicate the conversion logic.

- **`toInput()`**: SHALL preserve its current failure semantics as `toToolSchema() ?: error("Object schema expected")`. A non-object input schema is a programming error and MUST fail loudly with the existing message.
- **`toOutput()`**: SHALL return `toToolSchema()` directly; a non-object output schema yields a `null` output schema.

#### Scenario: Object schema conversion

- **WHEN** a `CompiledJsonSchemaData` describes a JSON object with `properties` and `required`
- **THEN** `toToolSchema()` SHALL return a `ToolSchema` carrying those `properties` and that `required` list
- **AND** `toInput()` and `toOutput()` SHALL return the same `ToolSchema`.

#### Scenario: Non-object schema conversion

- **WHEN** a `CompiledJsonSchemaData` has a top-level `type` other than `"object"`
- **THEN** `toToolSchema()` SHALL return `null`
- **AND** `toOutput()` SHALL return `null`
- **AND** `toInput()` SHALL throw `IllegalStateException("Object schema expected")`.

### Requirement: Leverage SDK `$defs` Support

The conversion SHALL be re-evaluated against the MCP SDK 0.14.0 `ToolSchema`, which supports JSON Schema `$defs` (SDK PR #526). Where schema-kenerator emits `$defs` for complex or nested types, the conversion SHALL preserve those definitions in the produced `ToolSchema` rather than silently dropping them, unless a deliberate limitation is documented.

#### Scenario: Schema referencing `$defs`

- **WHEN** the source schema defines or references `$defs`
- **THEN** the resulting `ToolSchema` SHALL retain the definitions required to resolve those references
- **OR** the conversion SHALL carry a comment documenting that `$defs` pass-through is intentionally unsupported.

### Requirement: Documented schema-kenerator Quirks

Non-obvious normalization performed while converting schema-kenerator's `JsonNode` tree to `kotlinx.serialization` (`toKotlinxSerialization()`) SHALL carry an explanatory comment.

- **Enum Without `type`**: The branch that injects `"type": "string"` when a node has an `enum` key but no `type` key SHALL be commented as a schema-kenerator quirk: schema-kenerator emits enum schemas without a `type` field, which the MCP SDK schema model rejects.

#### Scenario: Enum quirk is documented

- **WHEN** a developer reads the `enum`/`type` special case in `toKotlinxSerialization()`
- **THEN** an adjacent comment SHALL explain that it compensates for schema-kenerator emitting `enum` without `type`.
