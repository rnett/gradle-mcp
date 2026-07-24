## Why

The test fixtures maintain custom infrastructure (`ChannelBasedInMemoryTransport` using reflection, and `McpServerFixture.setServerRoots()` accessing `_roots` via reflection) that duplicates what the MCP SDK now provides officially via its `kotlin-sdk-testing` module. This adds unnecessary maintenance burden, risks breakage on SDK updates, and violates encapsulation through reflection.

## What Changes

- Replace hand-rolled `ChannelBasedInMemoryTransport` (~50 lines) with SDK's `ChannelTransport.createLinkedPair()` from `io.modelcontextprotocol:kotlin-sdk-testing`.
- Add `mcp-sdk-testing` dependency to version catalog (referencing `mcpSdk` version ref) with `testFixturesApi` scope.
- Update `McpServerFixture.kt` to use `ChannelTransport.createLinkedPair()` instead of `ChannelBasedInMemoryTransport`.
- Delete `ChannelBasedInMemoryTransport.kt`.
- Add `@VisibleForTesting fun setRootsForTesting(roots: Set<McpServer.Root>)` to project's `McpServer` class to replace reflection-based `setServerRoots()` in `McpServerFixture`.

## Capabilities

### New Capabilities
- `mcp-test-infrastructure`: Standards for MCP server/client test fixtures — mandates SDK-provided in-memory transport and prohibits reflection into SDK internals.

### Modified Capabilities
<!-- None — this is a new capability spec -->

## Impact

- **Test fixtures**: `ChannelBasedInMemoryTransport.kt` (deleted), `McpServerFixture.kt` (transport creation updated).
- **Production code**: `McpServer.kt` gains a `@VisibleForTesting` method.
- **Dependencies**: New test-fixtures dependency on `kotlin-sdk-testing`; no production runtime impact.
