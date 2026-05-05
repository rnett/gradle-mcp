## Why

AI agents using the dependency source tools (`read_dependency_sources`, `search_dependency_sources`) can currently explore external library sources and Gradle's own source code, but cannot access JDK standard library sources (e.g.,
`java.lang.String`, `java.util.List`). This is a significant gap because JDK classes are the most fundamental dependencies in any JVM project — agents frequently need to understand JDK API behavior, but currently have no way to read or
search that source code through the MCP tools.

## What Changes

- Auto-include JDK sources when resolving dependency sources for JVM-backed source scopes — no separate parameter needed
- Detect the project's configured JDK from Java/Kotlin toolchains via the init script, falling back to the Gradle daemon's JDK
- Create a `JdkSourceService` that locates the JDK's `src.zip`, extracts it into dependency CAS, and lazily builds provider indexes when search requires them
- Cache JDK sources in the dependency CAS under `<cacheDir>/cas/v3/<sha256>/`
- Extend `read_dependency_sources` to expose JDK sources as a synthetic session-view dependency under `jdk/sources/...`
- Extend `search_dependency_sources` to search dependency and JDK CAS indexes through the same manifest-backed session view
- Support the existing dependency filter value `jdk` to select only the synthetic JDK source entry

## Capabilities

### New Capabilities

- `jdk-source-resolution`: Locating the JDK source ZIP (`src.zip`) from the project's configured JDK, extracting it, and making it available through the dependency source tools

### Modified Capabilities

- `dependency-source-search`: Auto-include JDK sources in search results for JVM-backed scopes
- `cached-source-retrieval`: Extend the caching model to include JDK sources alongside Gradle and dependency sources
- `dependency-source-path-layout`: Reserve the `jdk/sources` session-view path for auto-included JDK sources

## Impact

- **Tools**: `read_dependency_sources` and `search_dependency_sources` automatically include JDK sources for JVM-backed scopes; `dependency: "jdk"` selects only JDK sources
- **Init Script**: New `JDK` line type emitted per project with the configured JDK home and version; `SOURCESET` lines include an `isJvm` flag used for JDK inclusion
- **Services**: New `JdkSourceService` class; source-resolution routing updated to auto-include JDK sources
- **Data Model**: `GradleProjectDependencies` gains `jdkHome` and `jdkVersion` fields; source-set models gain `isJvm`
- **Caching**: JDK sources are content-addressed entries in the existing dependency CAS under `<cacheDir>/cas/v3/<sha256>/`
- **Search**: Lucene indexes for JDK sources are built on demand and searched alongside dependency source indexes via the session manifest
- **No breaking changes**: JDK auto-inclusion requires no new parameter
