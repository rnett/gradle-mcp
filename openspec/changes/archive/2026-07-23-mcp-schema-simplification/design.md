## Context

`McpContext.kt` defines three schema-conversion functions on `CompiledJsonSchemaData`:

1. **`toInput()`** (line ~187): Converts to `ToolSchema`, fails with `error("Object schema expected")` if top-level type is not `"object"`.
2. **`toOutput()`** (line ~215): Converts to `ToolSchema?`, returns `null` if not an object schema.
3. **`toKotlinxSerialization()`** (line ~226): Recursively converts schema-kenerator `JsonNode` tree to `kotlinx.serialization` `JsonElement`, including a workaround that injects `"type": "string"` when an `enum` key exists without a `type` key.

`toInput()` and `toOutput()` are nearly identical — each calls `toKotlinxSerialization()`, extracts `.jsonObject`, checks for `"object"` type, and constructs `ToolSchema(properties = ..., required = ...)`. Only the failure mode differs (`error` vs `return null`).

The MCP SDK upgraded to 0.14.0 with PR #526, which added native `$defs` support in `ToolSchema`. Previously, `$defs` from schema-kenerator were silently dropped during conversion.

## Goals / Non-Goals

**Goals:**
- Reduce duplication by implementing a single shared `toToolSchema(): ToolSchema?` function.
- Ensure `$defs` definitions pass through to the resulting `ToolSchema` when present.
- Document the enum-without-type workaround with an explanatory comment.

**Non-Goals:**
- No behavioral changes to callers — `toInput()` and `toOutput()` remain as thin public delegates.
- No changes to `toKotlinxSerialization()` logic other than adding a comment (unless `$defs` handling requires it).
- No migration of callers away from `toInput()`/`toOutput()`.

## Decisions

### Decision 1: Shared function returns `ToolSchema?`, callers disambiguate

The shared `toToolSchema()` returns `null` for non-object schemas. `toInput()` applies `?: error("Object schema expected")`; `toOutput()` passes `null` through directly.

**Rationale**: Preserves existing semantics exactly. `error()` vs `null` encoding distinguishes programming errors (input schema must always be object) from legitimate absence (output may be optional). Using `!!` would lose the descriptive message.

### Decision 2: `$defs` pass-through via direct JsonObject propagation

`ToolSchema` in SDK 0.14.0 accepts `$defs` as part of its JSON Schema properties. Since `toKotlinxSerialization()` already produces a `JsonObject` from the source, `$defs` will naturally appear in the output if schema-kenerator emits them. We extract the `properties` field explicitly (as currently done) while leaving `$defs` and other JSON Schema keys untouched in the `ToolSchema` constructor — they propagate automatically since `ToolSchema` carries the full `JsonObject`.

**Verification needed**: Confirm that `ToolSchema` constructor accepts and preserves arbitrary JSON Schema keys beyond `properties` and `required`. If it does, no code change is needed for `$defs` pass-through. If it filters unknown keys, explicit extraction and passing of `$defs` is required.

### Decision 3: Comment on enum quirk, no logic change

Add a one-line KDoc-style comment above line ~233 explaining that schema-kenerator emits enum schemas without a `type` field, which the MCP SDK schema validator rejects unless `"type": "string"` is injected.

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| Breaking existing callers if `toToolSchema()` behavior diverges | Keep `toInput()` and `toOutput()` as exact semantic delegates; test round-trips. |
| `$defs` silently dropped if `ToolSchema` filters unknown keys | Verify SDK `ToolSchema` implementation; add explicit `$defs` handling if needed. |
| Refactoring hides bugs if edge cases differ between input/output paths | Extract once, verify both call sites cover same scenarios. |
