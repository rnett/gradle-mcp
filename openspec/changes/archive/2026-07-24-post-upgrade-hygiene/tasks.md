## 1. Remove dead deprecated aliases

- [x] 1.1 Verify zero callers for `advisoryLockFile` and `completionMarker` in `SourcesDir.kt` via symbol search
- [x] 1.2 Delete `advisoryLockFile` and `completionMarker` from `CASDependencySourcesDir`

## 2. Re-validate nullability suppressions

- [x] 2.1 Check gradle-tooling-api 9.6.1 annotations for members suppressed by `@Suppress("UNNECESSARY_SAFE_CALL")` in `ProblemsAccumulator.kt` (`documentationLink?.url` and `details?.details`)
- [x] 2.2 Apply three-way decision rule per site in `ProblemsAccumulator.kt` — adjust safe calls and/or suppress accordingly
- [x] 2.3 Check gradle-tooling-api 9.6.1 annotations for member suppressed by `@Suppress("SENSELESS_COMPARISON")` in `Problems.kt` (`parent?.fqName`)
- [x] 2.4 Apply three-way decision rule in `Problems.kt` — adjust safe call and/or suppress accordingly

## 3. Extract shared Koin McpServer resolution

- [x] 3.1 Identify the duplicated try/catch blocks in `Application.kt` (Stdio transport path and Sse transport path)
- [x] 3.2 Create `Application.resolveMcpServer(): McpServer` — logs `"Failed to initialize MCP Server"` and rethrows on failure
- [x] 3.3 Replace both inline resolution blocks with calls to `resolveMcpServer()`
- [x] 3.4 Verify behavioral equivalence (same error message, same exception propagation)

## 4. Add design rationale documentation comments

- [x] 4.1 Add explanatory comment on `McpServer.scope` — document why it is not a child of the SDK session scope (cancellation decoupling for `notifications/cancelled`)
- [x] 4.2 Add note on the `onConnect` block in `McpServer.init` — document that SSE sessions bypass `connect()` and are created via `createSession()`
- [x] 4.3 Add cross-reference comment on the `enum` special case in `toKotlinxSerialization()` — note schema-kenerator quirk, link to `mcp-schema-simplification` spec
