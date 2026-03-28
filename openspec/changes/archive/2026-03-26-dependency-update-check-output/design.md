## Context

**[UPDATE CHECK SKIPPED] noise**: The annotation is appended when `checkingUpdates && !dep.updatesChecked`. The `updatesChecked` flag is set to `false` for transitive deps when `onlyDirect=true` because the init script never adds them to
`targetComponents` — their update check is skipped by design, not due to failure. The renderer cannot currently distinguish "intentionally excluded" from "checked but failed".

**Verbose updatesOnly output**: `formatUpdatesSummary` groups results by project path, then lists each matching configuration and source set per dep. For deciding whether to upgrade a dependency, the configuration and source set are rarely
actionable — the relevant information is "this dep in this project has an update". The extra columns make the output hard to scan.

## Goals / Non-Goals

**Goals:**

- Suppress `[UPDATE CHECK SKIPPED]` for dependencies intentionally excluded from update checking.
- Simplify `formatUpdatesSummary` to show dep → version + project paths only.

**Non-Goals:**

- Changing when updates are actually checked (scope of `onlyDirect` is unchanged).
- Changing the `inspect_dependencies` tool parameters.

## Decisions

### D1: Compute updatesChecked inline in the renderer using a private helper

**Decision**: Add a `checksEnabled: Boolean` field and a private `isUpdateCheckComplete(group, name)` helper to `McpDependencyReportRenderer`. The helper encodes the three-state logic:

- `checksEnabled=false` → return `false` (no annotation possible; renderer also gates on `checkUpdatesEnabled` — the Kotlin-side flag)
- dep not in `updatesCheckedDeps` → return `true` (excluded from scope; not a genuine failure)
- dep in `updatesCheckedDeps` but not in `latestVersions` → return `false` (genuine failure → annotate)
- dep in `latestVersions` → return `true` (check succeeded)

`updatesCheckedDeps` holds the set of `group:module` coordinates that were in `targetComponents` (i.e., submitted for update checking). `checksEnabled` is set to `checkUpdates` in `generateReportFor`.

**Note**: This approach differs from the originally-described "pre-processing mutation" (which would have set `updatesChecked=true` explicitly in a separate pass). The inline computation is equivalent and avoids allocating an additional
set. The helper is called from both `renderDependencyResult` and `renderRenderableDependency`, eliminating the DRY violation that a two-liner duplication would introduce.

**Why**: The helper gives the logic a name, eliminates duplication between the two render paths, and preserves semantic accuracy: when `checkUpdates=false`, `checksEnabled=false` ensures the field returns `false` (not `true`), keeping the
`updatesChecked` field semantically consistent with its meaning ("was this dep checked?").

**Edge case**: A direct dep whose update resolution failed correctly retains `updatesChecked=false` because it is in `updatesCheckedDeps` (was targeted) but absent from `latestVersions` (check failed). Project-type dependencies (
`group="project"`) are never in `updatesCheckedDeps` (only `ModuleComponentIdentifier` entries are added), so `isUpdateChecked` returns `true` for them — correctly suppressing the annotation for sub-project dependencies.

### D2: Remove configuration/source-set columns from formatUpdatesSummary

**Decision**: Show only: `group:artifact: currentVersion → latestVersion` with a list of project paths below each entry. Drop configuration and source set columns.

**Why**: The question "should I upgrade this dep?" doesn't require knowing which source set it appears in. That granularity is already available via `inspect_dependencies` for a specific dep. Removing it makes the output scannable.

## Risks / Trade-offs

- **[D2] Information loss**: Users who were using the configuration/source set detail in the updatesOnly output will no longer see it. They can still get it via `inspect_dependencies` for a specific dep.

## Migration Plan

Internal changes only. The `updatesOnly` output format change removes information rather than restructuring it.
