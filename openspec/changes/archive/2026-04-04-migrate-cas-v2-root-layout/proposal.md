## Why

The current CAS (Content-Addressable Storage) layout uses versioning *inside* the hash directory (e.g., `.../cas/<hash>/v1/`). This causes cross-version lock contention, complex invalidation, and potential state pollution across different
server versions.

## What Changes

- **Root-Level Versioning**: Move the version prefix to the top level of the CAS: `cache/cas/v2/<hash>/`.
- **Lock Isolation**: Move advisory lock files into the versioned root: `cache/cas/v2/<hash>.lock`.
- **Infrastructure Refactor**: Update `SourceStorageService` and `SourcesDir` to point to the new layout.
- **Migration**: Increment the schema version to `v2` to force a fresh extraction/normalization cycle, implicitly invalidating the old `v1` layout.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `cas-dependency-cache`: Update the physical layout and locking strategy to support root-level versioning.

## Impact

- `SourceStorageService`: Primary logic for directory and lock path calculation.
- `CASDependencySourcesDir`: Data model for CAS directory paths.
- `SourcesService`: Usage of CAS directories during normalization and indexing.
- Integration Tests: All tests relying on physical CAS paths or manual setup.
