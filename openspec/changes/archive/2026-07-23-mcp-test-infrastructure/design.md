## Context

The project's test fixtures (`src/testFixtures/kotlin/dev/rnett/gradle/mcp/fixtures/mcp/`) include two custom components that interact with the MCP SDK:

1. **`ChannelBasedInMemoryTransport`** (~50 lines) — A custom `AbstractTransport` subclass that creates a linked client/server transport pair for in-memory testing using Kotlin `Channel`. It uses `Channel.UNLIMITED` buffering and requires an explicit `CoroutineScope` parameter.

2. **`McpServerFixture.setServerRoots()`** — Uses Java reflection to access `McpServer._roots` (a private `MutableStateFlow`), calling `setAccessible(true)` to mutate it. This is fragile and breaks encapsulation.

The MCP SDK 0.14.0 now ships an official `kotlin-sdk-testing` module with `ChannelTransport.createLinkedPair()`, eliminating the need for custom in-memory transport infrastructure.

## Goals / Non-Goals

**Goals:**
- Replace `ChannelBasedInMemoryTransport` with SDK's `ChannelTransport.createLinkedPair()`.
- Add `@VisibleForTesting fun setRootsForTesting(roots: Set<McpServer.Root>)` to `McpServer` to eliminate the reflection hack in `McpServerFixture`.
- Keep version catalog entry (`mcp-sdk-testing`) in lockstep with main SDK via `version.ref = mcpSdk`.

**Non-Goals:**
- Refactoring other test fixtures beyond what's needed for these two changes.
- Modifying production behavior of `McpServer` outside the test-only method.
- Changing the concurrency model or channel buffering semantics beyond adopting the SDK defaults.

## Decisions

### Use SDK's ChannelTransport.createLinkedPair() instead of keeping the custom class
The SDK now provides an officially maintained in-memory transport. Adopting it reduces maintenance burden and ensures compatibility with future SDK transport changes. The SDK transport uses `Dispatchers.Default` with a bounded 256-element channel rather than `Channel.UNLIMITED`, which tests must account for.

### Add test-only method to McpServer instead of documenting the reflection pattern
Rather than adding KDoc warning about the reflection-based approach, we add a clean `@VisibleForTesting` method on `McpServer`. This preserves the same internal state mutation (updating `_roots` MutableStateFlow) through a supported API surface.

### Version catalog entry uses version ref instead of version literal
The `mcp-sdk-testing` dependency references `version.ref = "mcpSdk"` so its version stays automatically synchronized with the main MCP SDK dependency (`libs.mcp.sdk`). This avoids drift between production and test-fixtures SDK versions.

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| SDK transport uses bounded channel (256 elements) vs unlimited | Tests that relied on unbounded buffering may need tuning; verify all tests pass after migration |
| SDK transport manages its own coroutine lifecycle (no scope param) | `McpServerFixture` no longer passes a `CoroutineScope` to the transport; fixture cleanup relies on SDK-provided lifecycle management |
| Adding `@VisibleForTesting` to `McpServer` exposes internals | Method is clearly annotated and scoped to test fixtures; minimal public API surface growth |

## Open Questions

None identified — the existing target-state spec at `openspec/specs/mcp-test-infrastructure/spec.md` covers all requirements.
