## Why

Currently, buildscript dependencies (plugins) are always included in the search index and available for reading by default. This can clutter search results with internal plugin implementation details when the user is primarily interested in their own project's dependencies.

## What Changes

- Modified `search_dependency_sources` and `read_dependency_sources` to exclude buildscript dependencies by default.
- Introduced a virtual `buildscript` source set. To access buildscript dependencies, users will specify `sourceSetPath` (e.g., `:buildscript` or `:app:buildscript`).
- Updated the search indexing and dependency resolution logic to only process buildscript dependencies when explicitly requested via this virtual source set or a specific buildscript configuration.

## Capabilities

### Modified Capabilities
- `build-classpath-support`: Changed to make buildscript inclusion require explicit targeting via the virtual `buildscript` source set or specific buildscript configurations.

## Impact

- **Tooling**: `search_dependency_sources` and `read_dependency_sources` will return fewer results by default, focusing strictly on project dependencies.
- **User Experience**: Users will use the existing `sourceSetPath` parameter (e.g., `sourceSetPath: ":buildscript"`) to search or read plugin source code instead of having it mixed in.
- **Performance**: Search indexing will be faster by default as buildscript dependencies won't be indexed unless the virtual source set is targeted.
