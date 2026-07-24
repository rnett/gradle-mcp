## Context

Five dependency upgrades landed (MCP SDK 0.7.2→0.14.0, gradle-tooling-api 9.5.0→9.6.1, ktor 3.5.1→3.5.1+, kotlin-compiler-embeddable upgraded): these changed type annotations, added/deprecated APIs, and opened opportunities to clean up accumulated technical debt. Four specific work items were identified that are low-risk, self-contained, and beneficial for long-term maintainability.

## Goals / Non-Goals

**Goals:**
- Eliminate dead code (deprecated aliases with zero callers)
- Remove or retain nullability suppressions based on actual 9.6.1 annotations
- Deduplicate shared Koin resolution logic to serve as a foundation for the `streamable-http-transport` proposal
- Document non-obvious architectural decisions at their declaration site

**Non-Goals:**
- No behavioral changes — existing functionality must remain identical
- No new dependencies or configuration changes
- No source-level API modifications (comments-only additions excluded)
- No build test execution or verification within this change

## Decisions

### Decision 1: Delete deprecated aliases outright

The two deprecated aliases (`advisoryLockFile`, `completionMarker`) in `CASDependencySourcesDir` have been confirmed to have zero callers after migration. They are safe to delete without deprecation warnings in consumer code.

**Rationale**: Dead code creates cognitive load. Since MCP SDK consumers are AI agents operating in isolated sessions, there is no need to maintain backward compatibility shims beyond what the current SDK already provides.

### Decision 2: Three-way decision rule for suppression re-validation

For each `@Suppress("UNNECESSARY_SAFE_CALL")` and `@Suppress("SENSELESS_COMPARISON")` tied to gradle-tooling-api nullability, apply:

| 9.6.1 annotation | Runtime reality | Action |
|---|---|---|
| Nullable | Nullable | Keep safe call, remove `@Suppress` |
| Non-null | Non-null | Remove safe call, remove `@Suppress` |
| Unchanged (non-null declared, nullable runtime) | Same | Keep both |

**Rationale**: This avoids premature removal of working workarounds while eliminating genuinely stale suppressions.

### Decision 3: Extract `resolveMcpServer()` as an `internal` extension function

The duplicated Koin resolution try/catch will be extracted into `Application.resolveMcpServer(): McpServer` — an `internal` member function on `Application`. It logs `"Failed to initialize MCP Server"` and rethrows on failure, preserving exact current behavior.

**Rationale**: Using an `internal` member keeps it scoped to the module, matches the surrounding code style, and is directly accessible from both Stdio and Sse transport blocks (same class). A private helper method would also work, but internal visibility allows targeted testing if needed later.

### Decision 4: Inline comments at point of use for documentation

All explanatory comments will be placed adjacent to their declarations — no separate documentation files.

**Rationale**: Comments near declarations ensure the rationale travels with the code through refactors and history. Separate docs risk diverging from implementation.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| Removing a deprecated alias breaks an unexpected internal caller | Verified zero callers via symbol search; safe if grep confirms |
| Re-validating suppressions removes a necessary workaround | Three-way decision rule prevents premature removal; compiler warnings will flag issues |
| Extracting `resolveMcpServer()` introduces subtle behavior change | Exact copy-paste of try/catch semantics ensures 1:1 behavioral equivalence |
| Documentation comments introduce noise | Comments are sparse and only explain non-obvious decisions |

## Migration Plan

No deployment or migration steps required. All changes are compile-time code hygiene within the same version boundary.

## Open Questions

None. All four work items are fully specified by the target-state spec at `openspec/specs/post-upgrade-hygiene/spec.md`.
