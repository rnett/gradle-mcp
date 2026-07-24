## 1. Consolidate `toToolSchema()` conversion

- [x] 1.1 Extract shared logic from `toInput()` and `toOutput()` into a new private `fun CompiledJsonSchemaData.toToolSchema(): ToolSchema?` that returns `null` for non-object schemas
- [x] 1.2 Replace `toInput()` body with `toToolSchema() ?: error("Object schema expected")`, preserving exact error message
- [x] 1.3 Replace `toOutput()` body with direct `toToolSchema()` return, keeping nullable signature
- [x] 1.4 Verify callers of `toInput()` and `toOutput()` remain unaffected (no behavioral change)

## 2. Evaluate `$defs` pass-through

- [x] 2.1 Inspect MCP SDK 0.14.0 `ToolSchema` constructor to confirm it accepts and preserves `$defs` alongside `properties` and `required`
- [x] 2.2 If `ToolSchema` naturally propagates `$defs`: no code change needed; add a one-line comment in `toToolSchema()` documenting this behavior
- [x] 2.3 If `ToolSchema` filters unknown keys: extract `$defs` explicitly from the source `JsonObject` and pass it to the `ToolSchema` constructor

## 3. Document enum quirk

- [x] 3.1 Add an adjacent comment above the `enum`/`type` injection branch in `toKotlinxSerialization()` explaining that schema-kenerator emits enum schemas without a `type` field, which the MCP SDK schema model rejects
