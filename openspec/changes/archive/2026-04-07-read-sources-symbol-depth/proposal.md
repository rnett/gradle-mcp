## Why

When browsing dependency sources with `read_dependency_sources`, directory and package listings only show file/directory names at the depth limit with no indication of what lies beneath. This forces redundant follow-up calls just to
discover whether a directory has 2 files or 200 symbols, degrading navigation efficiency.

## What Changes

- `walkDirectory` will include a symbol/entry count suffix for directories that are at the max-depth cutoff and therefore not expanded (e.g., `collections/  (42 items)`).
- Package content listings via `formatPackageContents` will expand 2 sub-package levels by default instead of 1, and for sub-packages at the depth limit, show a symbol count rather than a bare name.
- The default depth of `walkDirectory` remains 2, but is now meaningful because entries at that level carry item counts.

## Capabilities

### New Capabilities

- `read-sources-symbol-depth`: Directory and package listings in `read_dependency_sources` are 2 levels deep and annotate unexpanded entries with their symbol/item count.

### Modified Capabilities

<!-- No existing spec-level requirement changes. -->

## Impact

- `DependencySourceTools.kt` — `walkDirectoryImpl` and `formatPackageContents` (and their callers)
- May touch `SourceIndexService` / `PackageContents` if symbol counts require a new query
- No public API/tool parameter changes; purely output format improvement
