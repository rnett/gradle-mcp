# Spec: Distribution Downloading and Extraction

## Distribution URL

```
https://services.gradle.org/distributions/gradle-{version}-docs.zip
```

The `-docs` distribution is used (not `-bin` or `-all`) because it contains only
documentation — no binaries, source jars, or wrapper files.

**Verified contents of `gradle-9.4.0-rc-2-docs.zip`**:

| Path in ZIP                                   | Count                          | Generator                                         |
|-----------------------------------------------|--------------------------------|---------------------------------------------------|
| `gradle-{version}/docs/userguide/*.html`      | ~329 files                     | AsciiDoc → Gradle site template                   |
| `gradle-{version}/docs/dsl/*.html`            | ~340 files                     | DocBook XSLT → Gradle site template               |
| `gradle-{version}/docs/kotlin-dsl/**/*.html`  | ~8,445 files                   | Dokka (deeply nested: `gradle/org.gradle.api.*/`) |
| `gradle-{version}/docs/javadoc/**/*.html`     | ~1,992 files                   | JDK 21 Javadoc tool                               |
| `gradle-{version}/docs/samples/*.html`        | ~48 description files          | AsciiDoc → Gradle site template                   |
| `gradle-{version}/docs/samples/zips/*.zip`    | ~96 nested ZIPs (2 per sample) | Gradle sample ZIPs                                |
| `gradle-{version}/docs/release-notes.html`    | 1 file                         | Custom Gradle HTML                                |
| `gradle-{version}/docs/release-notes-assets/` | misc assets                    | (skip — binaries/CSS)                             |

The archive is approximately 89 MB for recent versions.

**Note on `kotlin-dsl/` structure**: files are nested under `gradle/org.gradle.{package}/`
rather than directly under `kotlin-dsl/`. The top-level also contains `images/`, `scripts/`,
`styles/`, `ui-kit/` asset directories and `index.html`/`navigation.html` — these should
be processed (HTML converted; binaries skipped) without special handling.

## DistributionDownloaderService

### Interface

```kotlin
interface DistributionDownloaderService {
    /** Returns the path to the cached ZIP, downloading if necessary. */
    suspend fun getDistributionZip(version: String): Path

    /** Returns true if the ZIP is already cached. */
    fun isCached(version: String): Boolean
}
```

### Behavior

1. **Cache check**: If `{cacheDir}/gradle-docs/{version}/gradle-{version}-docs.zip` exists
   and is complete (not a `.part` file), return immediately.
2. **Download**: Stream the ZIP from the distribution URL to a `.part` temp file in the
   same directory.
3. **Atomic rename**: On successful download, rename `.part` → final filename.
4. **Error handling**: If download fails, delete the `.part` file and throw with the URL
   and status code in the message.
5. **Concurrency**: Coordinated by the version-level file lock (see below).

### Configuration

- **Cache dir**: `GradleMcpEnvironment.cacheDir` (injected, same as today).
- **HTTP client**: Reuse the existing Ktor `HttpClient` from DI.
- **Timeout**: Use a generous read timeout (e.g., 5 minutes) for large downloads.

### ZIP Retention Policy

Keep the ZIP after processing — it is the only source for re-processing if needed.

---

## ContentExtractorService

### Interface

```kotlin
interface ContentExtractorService {
    /**
     * Ensures content for the version is extracted and converted.
     * Streams from ZIP, converts HTML → Markdown in-flight, writes directly to converted/.
     * No intermediate files are written.
     */
    suspend fun ensureProcessed(version: String)

    /**
     * Returns the output directories for the given kind under converted/.
     * Most kinds return a single directory. DSL returns two (dsl/ and kotlin-dsl/).
     */
    fun convertedDirs(version: String, kind: DocsKind): List<Path>
}
```

### Processing Logic

**ZIP entry prefix → output directory mapping**:

| DocsKind      | ZIP entry prefix                           | Output under `converted/` |
|---------------|--------------------------------------------|---------------------------|
| USERGUIDE     | `gradle-{version}/docs/userguide/`         | `userguide/`              |
| DSL           | `gradle-{version}/docs/dsl/`               | `dsl/`                    |
| DSL           | `gradle-{version}/docs/kotlin-dsl/`        | `kotlin-dsl/`             |
| JAVADOC       | `gradle-{version}/docs/javadoc/`           | `javadoc/`                |
| SAMPLES       | `gradle-{version}/docs/samples/`           | `samples/`                |
| RELEASE_NOTES | `gradle-{version}/docs/release-notes.html` | `release-notes.md`        |

**Steps**:

1. Check for completion marker `converted/.done`. If present, return immediately.
2. Call `DistributionDownloaderService.getDistributionZip(version)` to get the ZIP path.
3. **Cleanup**: If `converted/` exists but `.done` is missing, delete `converted/` recursively to ensure a clean state.
4. Open the ZIP and iterate all entries in a single pass:
   a. Match entry against all kind prefixes.
   b. Strip the matching prefix to get the relative output path.
   c. If HTML: convert in-memory using the kind-specific Jsoup strategy
   (see `specs/conversion.md`). Write result to `converted/{relPath}.md`.
   **Special case for SAMPLES**: root-level description HTML files
   (`docs/samples/{name}.html`, i.e., no `/` in the relative path after stripping
   the `docs/samples/` prefix) are written to
   `converted/samples/{name}/README.md` instead of `converted/samples/{name}.md`.
   d. If non-HTML text: write as-is to `converted/{relPath}`.
   e. If binary (`.jar`, `.class`, `.png`, `.gif`, `.svg`, `.zip` outer-level entries): skip.
4. **For `SAMPLES`**: nested ZIPs under `samples/zips/` are binary entries in the outer ZIP.
   After the main pass, re-open the outer ZIP and for each nested sample ZIP:
   a. Derive the sample base name and variant.
    - If filename ends with `-kotlin-dsl.zip`: base = `filename.removeSuffix("-kotlin-dsl.zip")`, variant = `kotlin-dsl`.
    - If filename ends with `-groovy-dsl.zip`: base = `filename.removeSuffix("-groovy-dsl.zip")`, variant = `groovy-dsl`.
    - Otherwise, skip (not a sample variant ZIP).
      b. Iterate the nested ZIP entries; write each file directly into
      `converted/samples/{base}/{variant}/{entry-path}`.
      Verified contents of `sample_building_java_applications-kotlin-dsl.zip`:
      `app/src/…/App.java`, `app/build.gradle.kts`, `settings.gradle.kts`, `gradlew`,
      `gradle.properties`, `gradle/libs.versions.toml`, `README`, etc.
      c. Apply zip-slip guard to nested entry paths.

   A sample may have a groovy-dsl ZIP, a kotlin-dsl ZIP, or both — neither is required.
   Each present variant produces a `{variant}/` subdirectory under `converted/samples/{base}/`.
5. Write `converted/.done` marker.

**Exclusions**: the following entries are skipped regardless of kind:

- `gradle-{version}/docs/userguide/userguide_single.html` — single-page combined User Guide;
  redundant with individual pages and would dominate search results.

**Security**: Validate all resolved output paths are under `converted/` to prevent
zip-slip attacks, for both the outer ZIP and nested sample ZIPs.

**Concurrency**: Coordinated by the version-level file lock (see below).

### convertedDirs

Returns a list of `Path` values for the given kind:

- All kinds except `DSL`: single element — `{cacheDir}/gradle-docs/{version}/converted/{kind.dirName}/`
- `DSL`: two elements — `converted/dsl/` and `converted/kotlin-dsl/`

---

## Cache Directory Structure

```
{cacheDir}/gradle-docs/{version}/
  ├── gradle-{version}-docs.zip         (source, kept for re-processing)
  ├── converted/                        (final output — Markdown + raw text files)
  │   ├── userguide/
  │   │   └── command_line_interface.md
  │   ├── dsl/
  │   │   └── org.gradle.api.Project.md
  │   ├── kotlin-dsl/
  │   │   └── gradle/org.gradle/-project/index.md
  │   ├── javadoc/
  │   │   └── org/gradle/api/Project.md
  │   ├── samples/
  │   │   └── sample_building_java_applications/
  │   │       ├── README.md             (converted from sample_building_java_applications.html)
  │   │       ├── groovy-dsl/           (present if groovy-dsl ZIP exists)
  │   │       │   └── app/build.gradle.kts
  │   │       └── kotlin-dsl/           (present if kotlin-dsl ZIP exists)
  │   │           └── app/build.gradle.kts
  │   ├── release-notes.md
  │   └── .done
  ├── index/                            (Unified Lucene index)
  │   └── .done
  └── .lock                             (version-level file lock)
```

---

## Concurrency — Version-Level File Lock

All preparation work for a version (download → extract+convert → index) is coordinated
by a single OS-level file lock:

```
{cacheDir}/gradle-docs/{version}/.lock
```

```
ensurePrepared(version):
  1. if index/.done exists: return   // fast path — no lock needed
  2. acquire FileChannel.lock() on {version}/.lock
  3. re-check index/.done            // another instance may have finished while we waited
  4. run pipeline steps in order, each checking its own .done before proceeding:
       a. DistributionDownloaderService.getDistributionZip(version)
       b. ContentExtractorService.ensureProcessed(version)
       c. LuceneIndexService.buildIndex(version)
  5. release lock
```

`LuceneIndexService.search()` never acquires the lock — it calls `ensurePrepared`, which
uses the fast path once the index exists.

---

## Version Resolution

Version resolution for distribution download uses the same logic as today:

- If `version` arg is non-null and not `"current"`, use it directly.
- If `version` is null, auto-detect from `projectRoot` via `GradlePathUtils.getGradleVersion`.
- If still unresolved, fetch `https://docs.gradle.org/current/userguide/userguide.html`
  and extract the version from the canonical link.

The resolved version **must** be a concrete version string (e.g., `"8.10.2"`) before
initiating a distribution download.

---

## Page Reading

Paths passed to `getDocsPageAsMarkdown` are relative to the `converted/` root.

1. **Ensure prepared**: call `ensurePrepared(version)`.
2. **Resolve path**: look up `{cacheDir}/gradle-docs/{version}/converted/{path}`.
    - For paths originally pointing to HTML (e.g. `userguide/foo.html`), the stored file
      has a `.md` extension — resolve `userguide/foo.md`.
    - For non-HTML paths (e.g. `samples/unpacked/foo/build.gradle.kts`), the path is unchanged.
    - If the file does not exist, return a not-found error.
3. **Return content**:
    - `.md` files: return as-is.
    - Non-HTML text files: return wrapped in a fenced code block with an appropriate language hint.
