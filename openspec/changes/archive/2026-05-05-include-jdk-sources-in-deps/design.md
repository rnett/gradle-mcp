## Context

The dependency source tools (`read_dependency_sources`, `search_dependency_sources`) currently support three scopes: project dependencies, Gradle's own source code (`gradleOwnSource=true`), and Gradle build tool sources. JDK standard
library sources (e.g., `java.lang.String`, `java.util.List`) are not accessible, even though they are the most fundamental dependencies in any JVM project. The existing `GradleSourceService` pattern — locate a source archive, extract it,
cache it, and index it into Lucene — provides a proven template for adding a new source type.

The JDK ships a `src.zip` file alongside its runtime in `$JAVA_HOME/lib/src.zip` (or `$JAVA_HOME/src.zip` on older distributions). This ZIP contains the Java standard library source code. The project's init script already runs per-project
and has access to the Gradle project model, making it the natural place to detect the project's configured JDK (from Java/Kotlin toolchains).

## Goals / Non-Goals

**Goals:**

- Allow agents to read and search JDK standard library source code seamlessly when working with JVM source scopes
- Auto-detect the JDK home from the project's Gradle configuration (Java/Kotlin toolchains) via the init script, falling back to the Gradle daemon's JDK
- Cache extracted JDK sources per `src.zip` content hash and lazily index them when search requires an index
- Reuse the existing CAS/session-view model and Lucene indexing pipeline for consistency

**Non-Goals:**

- Supporting JDK source download from the internet (only local `src.zip` from the detected JDK home)
- Supporting non-standard JDK layouts beyond `$JAVA_HOME/lib/src.zip` and `$JAVA_HOME/src.zip`
- Providing JDK sources for Android SDK or other non-standard JVM runtimes
- Adding a `jdkSource` parameter to the tools (auto-inclusion makes this unnecessary)

## Decisions

### 1. Auto-include JDK sources for JVM-backed scopes (no `jdkSource` parameter)

**Decision**: When resolving sources for a JVM-backed source scope, automatically include JDK sources alongside dependency sources. No new `jdkSource` parameter is needed. Agents can still pass the existing dependency filter value `jdk` to
request only the synthetic JDK source entry.

**Rationale**: The cost analysis shows that JDK source extraction and indexing is a one-time cost comparable to Gradle source processing. Since `src.zip` is already on disk, the first read pays extraction cost and the first search pays
indexing cost. Auto-inclusion eliminates the need for agents to remember a dedicated JDK flag, while `dependency: "jdk"` keeps JDK-only reads and searches explicit.

**JVM eligibility**: The init script emits model-backed source-set metadata using `SOURCESET | projectPath | name | configurations | isJvm`. Java `SourceSetContainer` entries and buildscript source sets are JVM. Kotlin source sets are JVM
when reflected JVM target/compilation ownership, JVM-indicating configuration attributes, or the Kotlin JVM plugin's `main`/`test` fallback marks them JVM. Ambiguous Kotlin reflection failures are logged and treated as non-JVM rather than
guessed.

**Cost comparison with Gradle sources**:

| Aspect          | Gradle Sources                 | JDK src.zip                 |
|-----------------|--------------------------------|-----------------------------|
| Compressed size | ~15-25MB                       | ~30-60MB (2-3x larger)      |
| Extracted size  | ~80-120MB                      | ~150-200MB (1.5-2x larger)  |
| File count      | ~5,000-8,000                   | ~8,000-10,000 `.java` files |
| Download needed | Yes (from services.gradle.org) | No (local file)             |
| First-time cost | ~30-60s (incl. download)       | ~20-50s (no download)       |
| Cached access   | ~0s (marker check)             | ~0s (marker check)          |
| Disk space      | ~200-250MB                     | ~200-250MB                  |

**Alternative considered**: A `jdkSource: Boolean` parameter (like `gradleOwnSource`). Rejected because JDK sources are a baseline dependency for every JVM project — requiring agents to explicitly request them adds friction for the most
common case. `gradleOwnSource` is opt-in because Gradle sources are only needed when debugging Gradle itself; JDK sources are needed for virtually every JVM project.

### 2. `JdkSourceService` uses CAS model (same as dependency sources)

**Decision**: Create a `JdkSourceService` interface and `DefaultJdkSourceService` implementation that stores JDK sources in the same CAS (Content-Addressable Storage) cache as dependency sources. JDK sources are treated as just another
dependency — they are stored in `cache/cas/v3/<hash>/`, participate in session views, and are cleaned up by the same mark-and-sweep garbage collector.

**Rationale**: JDK sources are a single archive per JDK installation. Unlike Gradle distributions (which are deterministic per version), JDK `src.zip` content varies by distribution/provider for the same version string: vendors may patch
sources, omit modules, or package generated files differently. Using the CAS model with content-based hashing (SHA-256 of `src.zip`) guarantees correctness regardless of distribution. Treating JDK as just another dependency eliminates the
need for separate fallback logic and custom cleanup — JDK sources appear in the same unified source tree as all other dependencies.

### 3. JDK home detection via init script (configured toolchains → daemon JDK)

**Decision**: Detect the JDK home from the Gradle project's configuration via the init script. The init script emits a new `JDK` line type with the project's configured JDK home and version. Java toolchain resolution uses Gradle's normal
`JavaToolchainService` behavior, including provisioning if the build permits it. The detection priority is:

1. Configured Java toolchain (via `JavaPluginExtension.toolchain` + `JavaToolchainService`)
2. Configured Kotlin JVM toolchain metadata, resolved through `JavaToolchainService`
3. Gradle daemon's JDK (via `Jvm.current().javaHome`)

**Rationale**: The project's configured toolchain is the JDK that the source code is compiled against — it's the correct JDK for source resolution. The init script already runs inside the Gradle build and has access to the project model,
making it the natural place to detect this. Using `GradleInvocationArguments.javaHome` would give the JDK used to *run* the Gradle daemon, which may differ from the project's target JDK (especially when toolchains are configured).

**Init script emission format**: `JDK | projectPath | jdkHome | version`

**Alternative considered**: Using `GradleInvocationArguments.javaHome` → `JAVA_HOME` → `System.getProperty("java.home")`. Rejected because this gives the daemon's JDK, not the project's target JDK. When a project configures
`java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }`, the daemon might run on JDK 21 but the project targets JDK 17 — the sources should come from JDK 17.

**Note**: This priority chain is a non-issue. All three levels should be kept as-is — each serves a valid purpose in the fallback chain and removing any would reduce robustness.

### 4. Cache layout: CAS model (same as dependency sources)

**Decision**: Store cached JDK sources in the same CAS (Content-Addressable Storage) cache as dependency sources, under `<cacheDir>/cas/v3/<hash>/`. The hash is the SHA-256 of the `src.zip` file content. JDK sources are treated as just
another dependency in the CAS model — they participate in the same session views, use the same indexing pipeline, and are cleaned up by the same mark-and-sweep garbage collector.

**Rationale**: Using the same CAS model eliminates the need for a separate `jdk-sources/` directory and custom cleanup logic. JDK sources benefit from the existing CAS infrastructure: atomic extraction, advisory locking, session views with
junctions, and automatic garbage collection. The content hash (SHA-256 of `src.zip`) remains the primary cache key, consistent with the CAS model already used for dependency sources. The JDK version string remains descriptive report data
for human readability.

### 5. `src.zip` extraction and source path

**Decision**: Extract `src.zip` into the CAS directory structure without custom cleanup logic. JDK sources are added to session views as a synthetic dependency at the reserved `jdk/sources` path; read and search result paths are explicit (
for example, `jdk/sources/java.base/java/lang/String.java`) rather than transparent fallback paths.

**Rationale**: Since JDK sources are stored in CAS and referenced from manifests, they are cleaned up by the same mark-and-sweep garbage collector as all other dependency sources. The explicit reserved path avoids ambiguous reads and keeps
search filtering aligned with the immutable session view.

## Risks / Trade-offs

- **Risk**: `src.zip` may not exist in all JDK installations (e.g., JRE-only installs, some minimal Docker images). → **Mitigation**: Silently skip JDK source inclusion when `src.zip` is not found. Log a warning but don't fail the
  dependency resolution — the tools still work for dependency sources.
- **Risk**: Large JDK source archives could consume significant disk space (~200-250MB per source archive). → **Mitigation**: JDK sources are stored in the CAS cache and are automatically cleaned up by the mark-and-sweep garbage collector (
  unreferenced entries older than 7 days are pruned).
- **Risk**: JDK `src.zip` content varies by distribution/provider for the same version string (e.g., Oracle JDK vs OpenJDK, Amazon Corretto patches, GraalVM omissions). → **Mitigation**: Use SHA-256 content hash of `src.zip` as the *
  *primary** cache key (not a fallback). The detected version string is descriptive report data only. This is consistent with the CAS model already used for dependency sources.
- **Risk**: Toolchain resolution in the init script may trigger Gradle-managed JDK provisioning when the build permits it. → **Mitigation**: This is accepted as part of honoring the project's configured toolchain; use
  `Jvm.current().javaHome` as the fallback if toolchain resolution fails or is unavailable, and don't fail the build if JDK detection encounters issues.
- **Trade-off**: Auto-including JDK sources means the first `read_dependency_sources` call for a JVM scope may extract JDK sources, and the first `search_dependency_sources` call may index them. This is acceptable because it only happens
  once per `src.zip` content hash and subsequent calls reuse the CAS entry and provider index.
