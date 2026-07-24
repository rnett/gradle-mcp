## 1. Shared Koin Resolution Helper

- [ ] 1.1 Add `Application.resolveMcpServer(application: Application): McpServer` companion function that wraps `application.koinContext.get<McpServer>()` with try/catch logging "Failed to initialize MCP Server"
- [ ] 1.2 Replace duplicated Koin resolution block in `Transport.Stdio.start()` with call to `Application.resolveMcpServer(application)`
- [ ] 1.3 Replace duplicated Koin resolution block inside SSE's `mcp { }` plugin DSL with call to `Application.resolveMcpServer(application)`

## 2. StreamableHttp Transport Implementation

- [ ] 2.1 Create `Transport.StreamableHttp` inner class alongside `Stdio` and `Sse`, storing an `EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>?`
- [ ] 2.2 Implement `StreamableHttp.start()`: create server via `EngineMain.createServer(application.args)`, install `mcpStreamableHttp { server = Application.resolveMcpServer(application) }`, call `server.startSuspend(wait = wait)`
- [ ] 2.3 Implement `StreamableHttp.stop()`: call `server?.stopSuspend()` matching the SSE pattern

## 3. CLI Mode Dispatch

- [ ] 3.1 Add `streamableHttp(args: Array<String>)` companion function analogous to `stdio()` and `server()`, creating `Application(args, Transport.StreamableHttp())` and starting it
- [ ] 3.2 Update `Application.main()` dispatch logic: add check for `mode == "streamable-http"` before existing stdio/server branches
- [ ] 3.3 Ensure JBang CDS dump path still works with default SSE transport (no changes needed, but verify)

## 4. Verification

- [ ] 4.1 Verify `./gradlew build` compiles successfully
- [ ] 4.2 Verify `./gradlew test` passes (existing transport tests should still pass unchanged)