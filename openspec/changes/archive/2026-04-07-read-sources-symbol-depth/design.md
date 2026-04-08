## Context

`read_dependency_sources` has two listing modes:

1. **Filesystem listing** — `walkDirectory(dir, maxDepth=2)` renders a tree. At the depth limit directories are silently omitted; the caller gets no indication of how much content lies beneath.
2. **Package listing** — `formatPackageContents` shows immediate sub-packages (flat names, one level) and direct symbols. Sub-packages have no indication of depth or how many symbols they contain.

Both modes leave the caller guessing when navigating unfamiliar codebases.

## Goals / Non-Goals

**Goals:**

- Filesystem listing: annotate directories at the depth cutoff with their entry count (files + subdirs).
- Package listing: expand 2 levels of sub-packages by default; annotate the deepest unexpanded sub-packages with their direct symbol count.
- Keep implementation changes surgical and contained to `DependencySourceTools` and a new `listNestedPackageContents` helper on `SourceIndexService`.

**Non-Goals:**

- Adding a user-configurable depth parameter to the tool.
- Changing the default filesystem listing depth (remains 2).
- Showing symbol counts for intermediate (expanded) levels.

## Decisions

### Decision 1 — Item count for filesystem directories at depth limit

**Choice**: In `walkDirectoryImpl`, when appending a directory that is at `depth + 1 == maxDepth` (i.e., it will be listed but its children won't be walked), call `listDirectoryEntries().size` to count its direct children and append
`(N items)`.

**Alternative**: Walk without any count annotation. Rejected — this is what we have today and it's the problem.

**Why**: `listDirectoryEntries()` is already on `Path` and cheap; no new dependency needed.

### Decision 2 — Nested package listing via sequential `listPackageContents` calls

**Choice**: After fetching the top-level `PackageContents`, call `listPackageContents` once per sub-package to get their direct symbols. Present each sub-package with its symbols listed under it. For the sub-packages of those sub-packages (
depth 3+), show the sub-package name and its symbol count only (no further expansion).

**Alternative**: Add a new recursive Lucene query that fetches all nested content in a single pass. Rejected — more complex, harder to test, and the sequential approach over existing `listPackageContents` is sufficient given typical package
tree breadths.

**Why**: Reuses the existing `listPackageContents` API without new Lucene query complexity. The number of sub-packages at level 1 is typically small (<20), so N+1 calls are acceptable.

### Decision 3 — Symbol count at deepest package level

**Choice**: For sub-packages at the deepest listed level that have further sub-packages of their own (but are not expanded), show their direct symbol count using the same `listPackageContents` call (the `symbols.size` field is already
populated). Sub-packages that have 0 direct symbols show the sub-package count instead.

**Why**: Gives useful orientation without requiring additional index queries beyond what Decision 2 already fetches.

## Risks / Trade-offs

- [Risk] Sequential `listPackageContents` calls could be slow for packages with many (>50) direct sub-packages. → Mitigation: add a guard (e.g., cap at 30 sub-packages for recursive expansion; fall back to flat list with total count).
- [Risk] `listDirectoryEntries()` traverses symlinked directories on Windows (junction points). → Mitigation: count is a display hint only; incorrect counts from broken symlinks are non-fatal.
