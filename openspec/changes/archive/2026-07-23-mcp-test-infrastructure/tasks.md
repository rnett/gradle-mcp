## 1. Add SDK testing dependency

- [x] 1.1 Add `mcp-sdk-testing` entry to `gradle/libs.versions.toml` referencing `version.ref = "mcpSdk"`
- [x] 1.2 Add `testFixturesApi(libs.mcp.sdk.testing)` dependency to the test-fixtures module in `build.gradle.kts`

## 2. Add test-only roots API to McpServer

- [x] 2.1 Add `@VisibleForTesting fun setRootsForTesting(roots: Set<McpServer.Root>)` to `McpServer` that updates the `_roots` MutableStateFlow

## 3. Update McpServerFixture transport creation

- [x] 3.1 Replace `ChannelBasedInMemoryTransport.createLinkedPair(scope)` with `ChannelTransport.createLinkedPair()` in `McpServerFixture`
- [x] 3.2 Remove the `import dev.rnett.gradle.mcp.fixtures.mcp.ChannelBasedInMemoryTransport` from `McpServerFixture`
- [x] 3.3 Import `io.modelcontextprotocol.kotlin.sdk.shared.ChannelTransport` in `McpServerFixture`

## 4. Update McpServerFixture setServerRoots

- [x] 4.1 Replace reflection-based `setServerRoots()` implementation with delegation to `McpServer.setRootsForTesting()`
- [x] 4.2 Remove imports for `kotlinx.coroutines.flow.MutableStateFlow` and `java.lang.reflect.Field` if no longer needed in fixture

## 5. Delete custom transport class

- [x] 5.1 Delete `src/testFixtures/kotlin/dev/rnett/gradle/mcp/fixtures/mcp/ChannelBasedInMemoryTransport.kt`

## 6. Update test call sites

- [x] 6.1 All existing calls to `fixture.setServerRoots(...)` remain valid since the method signature on `McpServerFixture` is unchanged