## Context

The consumers of this MCP server are AI agents rather than people operating an interactive dependency browser. The design therefore focuses on giving agents structured evidence for two questions: why a dependency resolved to a version, and who directly depends on a component.

`GradleDependency` already models the forward dependency graph through `children` and carries `reason`, `latestVersion`, `isDirect`, `fromConfiguration`, and `commonComponentId`. It does not expose a reverse edge. Answering who depends on a component requires a genuine graph inversion rather than exposing data already present on each node.

## Goals / Non-Goals

Goals:

- Expose direct reverse dependency edges from `inspect_dependencies` when explicitly requested.
- Preserve the existing provenance fields and teach agents how to use them for version-selection questions.
- Keep reverse-edge construction bounded, deduplicated, and safe for diamond and cycle shapes.
- Avoid reverse-edge computation for callers that do not need it.

Non-goals:

- No transitive-closure API for blast-radius queries.
- No new standalone dependency tool.
- No publication verification.
- No Develocity integration.

## Decisions

### 1. Represent consumers as lightweight edges

Add a lightweight `ConsumerEdge` shape containing `id`, `group`, `name`, `version`, `variant: String?`, `fromConfiguration`, and `path`. The `variant` field is null when the direct parent has no variant. `ConsumerEdge.path` identifies the direct parent edge, using that parent's identity/path rather than a root-to-parent traversal path. Do not embed full `GradleDependency` objects in `consumers`, because recursively nesting forward and reverse graph nodes would increase payload size and create serialization blowup.

### 2. Return direct parents only

Each dependency's `consumers` list contains only direct parents, with one edge per distinct direct parent rather than one edge per root path. Diamond and cycle shapes therefore affect traversal safety but do not multiply consumer edges for the same direct parent. Agents compute transitive blast radius through client-side traversal of successive reverse edges. This keeps the server response composable and avoids introducing a separate transitive-closure contract.

### 3. Gate inversion behind an opt-in parameter

Add `includeConsumers: Boolean = false` to `inspect_dependencies`. When disabled, the response omits the reverse edge and does not perform graph inversion, regardless of `onlyDirect`. When enabled, the invocation behaves as if `onlyDirect=false` so the full resolved graph is parsed and rendered before consumers are computed once and memoized for all nodes in the response. If the caller explicitly passes `onlyDirect=true` with `includeConsumers=true`, `includeConsumers` wins and the response includes `"onlyDirect overridden to false for consumers inversion"`.

### 4. Deduplicate by stable parent identity and configuration

During a single inversion pass, deduplicate consumer edges by `(commonComponentId ?: syntheticId(group, name, version, variant)) + fromConfiguration + variant`. When `commonComponentId` is null, `syntheticId` folds the parent's own GAV, variant, and id so distinct ordinary parents in the same configuration remain separate. Including `variant` in both `ConsumerEdge` and the deduplication identity also preserves and externally distinguishes two direct parents that differ only by variant. The traversal and accumulation must terminate safely when the resolved graph contains diamond or cycle shapes.

### 5. Route provenance questions through the dependency skill

Add a decision table to `src\main\skills\advanced-gradle-dependencies\`:

| Agent question | Route |
| --- | --- |
| Why this version? | Use `dependencyInsight`, then interpret the selected `version` (`selected.version`) with `reason`, `isDirect`, and `fromConfiguration`. Treat `latestVersion` only as an optional advisory "newer available" signal. |
| Who depends on X? | Make one `inspect_dependencies { filter=X, includeConsumers:true }` call, then read `consumers`; `onlyDirect=false` is implied. |
| What is the blast radius? | Filter the target and traverse direct `consumers` edges client-side. |

## Risks / Trade-offs

- Inverting a large graph adds CPU and memory cost when `includeConsumers` is enabled. Default-off behavior and per-invocation memoization contain this cost.
- Embedding full dependency nodes on reverse edges would cause nesting and payload growth. Lightweight edges avoid that blowup at the cost of requiring follow-up lookup for full node details.
- Diamond graphs can produce duplicate paths, while cycles can cause unbounded traversal. Stable parent-identity deduplication and cycle-safe processing are required during inversion.

## Migration Plan

1. Add the optional consumer-edge model and `includeConsumers` tool parameter without changing the default response behavior.
2. Implement one-pass, per-invocation reverse-edge computation and deduplication.
3. Update the `advanced-gradle-dependencies` skill routing table.
4. Run `:updateToolsList` to synchronize generated tool documentation after the tool metadata changes.

## Open Questions

- At what graph size should performance measurements or a documented threshold be required for `includeConsumers`?

## Verification

- Add unit tests for graph inversion, direct-parent semantics, deduplication, diamond graphs, cycle safety, a transitive target under the default `onlyDirect=true`, null-`commonComponentId` distinct parents, and a diamond whose two direct parents differ only by variant.
- Add an integration test proving that `includeConsumers=true` implies full-graph processing and emits reverse edges, while the default-disabled path avoids reverse-edge construction regardless of `onlyDirect`.
- Verify conflict-resolution provenance with a graph where version 2.0 is selected over 1.0, the selected `version` reflects 2.0, and `reason` reports conflict resolution. Verify separately that `latestVersion` is optional advisory update-check data and is not used to explain selection.
- Run `:updateToolsList` and verify the generated tool documentation is synchronized.
