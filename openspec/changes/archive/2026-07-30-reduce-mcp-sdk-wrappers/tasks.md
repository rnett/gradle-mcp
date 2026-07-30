## 1. Governance and baseline
- [x] 1.1 Create proposal, design, capability delta, and tasks for `reduce-mcp-sdk-wrappers`, including the SDK fact matrix, request-context adoption, session-local roots, roots-only fixture capability, retained irreducibles, rejected registration flattening, and correlation behavior.
- [x] 1.2 Establish focused baseline coverage for existing SDK integration and roots behavior.

## 2. Context and result reduction
- [x] 2.1 Narrow `McpContext` to `Json`, `ServerSession?`, `ClientConnection`, progress token, and captured `RequestHandlerExtra?`; route queued notifications through the captured extra with connection fallback.
- [x] 2.2 Delete unused elicitation, logging, elicitation-schema, auxiliary-content, and `AuxiliaryResults` APIs without compatibility shims.
- [x] 2.3 Capture session, request handler extra, and progress token once in the SDK tool handler; collapse result handling to read `context.isError` directly while preserving output and exception policy.
- [x] 2.4 Resolve roots through `ctx.session` while retaining the no-capability guard before `listRoots()`.

## 3. Fixtures and regression coverage
- [x] 3.1 Remove vestigial elicitation capability setup and keep roots as the fixture's default capability while preserving the `Dispatchers.Default` request hop.
- [x] 3.2 Add deterministic tests for no-roots capability behavior, captured-extra async delivery, null-extra fallback, and preserved `isError`.
- [x] 3.3 Preserve and run existing progress, roots isolation, cancellation, and teardown coverage without weakening assertions.
- [x] 3.4 Make fixture server construction and teardown share one component-list resolution, and add a factory-binding identity regression test.

## 4. Verification and cleanup
- [x] 4.1 Compile main, test fixtures, test, and integration-test source sets and run focused relevant tests.
- [x] 4.2 Run full `test integrationTest`, `:updateToolsList`, and `:check`; review generated metadata changes.
- [x] 4.3 Strictly validate `reduce-mcp-sdk-wrappers`, run stale-symbol searches and `git diff --check`, and remove the session scratch report if it is not tracked.
- [x] 4.4 Re-run focused lifecycle/fixture tests, affected compilations, full suites, tool metadata generation, quality checks, strict validation, stale-symbol searches, and whitespace validation after the review fix.
