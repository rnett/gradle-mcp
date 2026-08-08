## 1. Dependency consumer edges

- [x] 1.1 Add the lightweight consumer-edge model with `id`, `group`, `name`, `version`, `variant: String?`, `fromConfiguration`, and `path` fields, with null `variant` when absent and `path` identifying the direct parent edge rather than a root-to-parent traversal path.
- [x] 1.2 Add `includeConsumers: Boolean = false` to `inspect_dependencies`; when enabled, make the invocation behave as if `onlyDirect=false` so inversion uses the full graph, and emit `"onlyDirect overridden to false for consumers inversion"` when the caller explicitly also passes `onlyDirect=true`.
- [x] 1.3 Preserve the current low-cost behavior when `includeConsumers=false`: do not invert the graph and omit `consumers` regardless of `onlyDirect`.
- [x] 1.4 Implement a single-pass graph inversion that records one lightweight edge per distinct direct parent, deduplicates by `(commonComponentId ?: syntheticId(group, name, version, variant)) + fromConfiguration + variant`, folds the parent's GAV, variant, and id into `syntheticId`, and handles diamond and cycle shapes safely.
- [x] 1.5 Memoize the inverted consumer map for the duration of each `inspect_dependencies` invocation and attach it to matching dependency nodes.

## 2. Agent provenance routing

- [x] 2.1 Add a decision table to `advanced-gradle-dependencies` that routes version questions to `dependencyInsight`, the selected `version`, and `reason`, while treating `latestVersion` only as optional advisory update-check data.
- [x] 2.2 Route direct-consumer questions to one `inspect_dependencies { filter=X, includeConsumers:true }` call and the returned `consumers` edges without requiring the agent to pass `onlyDirect=false`.
- [x] 2.3 Document client-side traversal of direct consumer edges for blast-radius questions without adding a transitive-closure API.

## 3. Verification and documentation

- [x] 3.1 Add unit tests for inversion, direct-parent path and multiplicity semantics, cycle safety, a transitive target whose direct parent is returned with `includeConsumers=true` under the default `onlyDirect=true`, and distinct same-configuration parents whose `commonComponentId` values are null.
- [x] 3.2 Add unit coverage for a diamond graph where the target has two direct parents that differ only by variant, asserting two deduplicated edges distinguishable through `edge.variant`.
- [x] 3.3 Add integration coverage for enabled and default-disabled `includeConsumers` behavior, the explicit `onlyDirect=true` override note, and conflict-resolution provenance, asserting the selected `version` with `reason` rather than using `latestVersion` to explain selection.
- [x] 3.4 Run the targeted unit and integration tests for the dependency inspection changes.
- [x] 3.5 Run `:updateToolsList` and verify the generated tool documentation is synchronized.
