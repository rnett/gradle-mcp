# Design: Gradle Docs Tool Rework

## Architecture Overview

```
GradleDocsTools (MCP tool, updated)
    query/path/version/projectRoot — no kind param; use tag: in query to filter
    │
    └── GradleDocsService (updated interface + DefaultGradleDocsService)
            │
            ├── DistributionDownloaderService (new)
            │       Downloads gradle-{version}-docs.zip to cache
            │
            ├── ContentExtractorService (new)
            │       Streams entries from ZIP; converts HTML → Markdown in-flight using
            │       kind-specific Jsoup selectors; writes final .md files directly to
            │       converted/. Non-HTML text written as-is. Binaries skipped.
            │       DSL processes both docs/dsl/ and docs/kotlin-dsl/ in one ZIP pass.
            │       SAMPLES unpacks nested ZIPs into converted/samples/unpacked/.
            │       No intermediate extracted/ directory is written.
            │
            ├── LuceneIndexService (new)
            │       Builds a UNIFIED Lucene index from converted/ content; executes search
            │       queries (with filters for specific kinds or across "all").
            │
            └── MarkdownService (existing, updated)
                    Gains convertHtml(html: String): String used by ContentExtractorService.
```

## DocsKind Enum

```kotlin
enum class DocsKind(val dirName: String) {
    USERGUIDE("userguide"),
    DSL("dsl"),
    JAVADOC("javadoc"),
    SAMPLES("samples"),
    RELEASE_NOTES("release-notes")
}
```

`ALL` is not needed — omitting `tag:` from the query searches all sections.

## Distribution Download

**URL pattern**: `https://services.gradle.org/distributions/gradle-{version}-docs.zip`

The `-docs` distribution (~89 MB) contains only documentation — no binaries or source:

- `docs/userguide/*.html` — User Guide HTML pages
- `docs/dsl/*.html` — Gradle Build Language reference (Groovy DSL)
- `docs/kotlin-dsl/**/*.html` — Kotlin DSL API reference (Dokka-generated)
- `docs/javadoc/**/*.html` — Javadoc HTML
- `docs/samples/*.html` — Sample description pages; `docs/samples/zips/*.zip` — nested sample ZIPs
- `docs/release-notes.html` — Release notes for the version

`DistributionDownloaderService` is responsible for:

1. Checking whether the ZIP is already cached.
2. Downloading to a `.part` file and renaming on completion.
3. Exposing the ZIP `Path` for extraction.

## Cache Directory Structure

```
{cacheDir}/reading_gradle_docs/{version}/
  ├── gradle-{version}-docs.zip         (downloaded distribution, kept as source)
  ├── converted/
  │   ├── userguide/*.md                (converted from docs/userguide/*.html)
  │   ├── dsl/*.md                      (converted from docs/dsl/*.html)
  │   ├── kotlin-dsl/**/*.md            (converted from docs/kotlin-dsl/**/*.html)
  │   ├── javadoc/**/*.md               (converted from docs/javadoc/**/*.html)
  │   ├── samples/{sample-base-name}/   (one dir per unique sample)
  │   │   ├── README.md                 (converted from {sample-base-name}.html)
  │   │   ├── groovy-dsl/               (present if groovy-dsl ZIP exists)
  │   │   └── kotlin-dsl/              (present if kotlin-dsl ZIP exists)
  │   ├── release-notes.md
  │   └── .done                         (marker: conversion complete)
  └── index/                            (Unified Lucene index for all kinds)
```

There is no intermediate `extracted/` directory. HTML is converted to Markdown in-flight
during the single ZIP pass. A single `.done` marker in `converted/` covers the full pipeline
step. A separate OS-level file lock (`{version}/.lock`) ensures only one process runs the
pipeline at a time.

## ContentExtractorService

Extracts entries from the distribution ZIP matching a kind-specific prefix:

| Kind          | ZIP prefix(es)                             | Written to (in `converted/`)                                                                                                                                         |
|---------------|--------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| USERGUIDE     | `gradle-{version}/docs/userguide/`         | `converted/userguide/*.md`                                                                                                                                           |
| DSL           | `gradle-{version}/docs/dsl/`               | `converted/dsl/*.md`                                                                                                                                                 |
| DSL           | `gradle-{version}/docs/kotlin-dsl/`        | `converted/kotlin-dsl/**/*.md`                                                                                                                                       |
| JAVADOC       | `gradle-{version}/docs/javadoc/`           | `converted/javadoc/**/*.md`                                                                                                                                          |
| SAMPLES       | `gradle-{version}/docs/samples/`           | `converted/samples/{sample-base-name}/` (one dir per unique sample; `README.md` from description HTML + `groovy-dsl/` and/or `kotlin-dsl/` subdirs from nested ZIPs) |
| RELEASE_NOTES | `gradle-{version}/docs/release-notes.html` | `converted/release-notes.md`                                                                                                                                         |

HTML is converted to Markdown in-flight during the ZIP pass — no raw HTML is written to
disk. Non-HTML text files are written as-is. Binary files are skipped entirely.

`DSL` processes both ZIP prefixes in a single pass, writing to two output directories
under `converted/`. `ContentExtractorService.convertedDirs(version, DSL)` returns both.

For `SAMPLES`, the main ZIP pass writes root-level description HTML files
(`docs/samples/{name}.html`) directly to `converted/samples/{name}/README.md`.
Nested ZIPs are then unpacked into `converted/samples/{name}/groovy-dsl/` or
`converted/samples/{name}/kotlin-dsl/` based on the ZIP filename suffix.
Either or both variant subdirs may be present — neither is required.
Each file in the sample directory tree becomes a separate Lucene document tagged `samples`.

## LuceneIndexService (Unified Indexing)

**Index fields per document**:

| Field     | Stored | Indexed | Notes                                                               |
|-----------|--------|---------|---------------------------------------------------------------------|
| `tag`     | yes    | yes     | `userguide`, `dsl`, `javadoc`, `samples`, `release-notes`           |
| `path`    | yes    | no      | Relative path within `converted/` (with `.html` extension restored) |
| `title`   | yes    | yes     | From first `# Heading` line, fallback to filename                   |
| `body`    | no     | yes     | Full text                                                           |
| `snippet` | yes    | no      | First 500 chars of body (for result display)                        |

`kotlin-dsl` content is indexed with `tag=dsl` (merged with the Groovy DSL section).

**Search**: The query string is passed directly to Lucene's `QueryParser`. Tag filtering is
expressed natively in the query — no special parameter needed:

- **All sections**: `gradle_docs(query="dependency resolution")`
- **Specific section**: `gradle_docs(query="tag:javadoc dependency resolution")`

---

## Updated GradleDocsService Interface

```kotlin
interface GradleDocsService {
    /**
     * Reads a specific page or file and returns it as text.
     * Path is relative to the converted/ root. Section is encoded in the path prefix.
     * HTML → Markdown (.md); non-HTML text → raw fenced block.
     */
    suspend fun getDocsPageAsMarkdown(path: String, version: String?): String

    suspend fun getReleaseNotes(version: String?): String

    /**
     * Searches the index. Use tag:X in the query to filter by section.
     * No separate kind parameter — tag: filtering is part of the query string.
     */
    suspend fun searchDocs(query: String, version: String?): List<DocsSearchResult>

    /**
     * Returns a summary of available documentation sections with page counts.
     */
    suspend fun summarizeSections(version: String?): List<DocsSectionSummary>
}
```

---

## Updated GradleDocsTools Interface

```kotlin
data class QueryGradleDocsArgs(
    val query: String? = null,
    val path: String? = null,
    val version: String? = null,
    val projectRoot: GradleProjectRootInput? = null
)
```

**Dispatch logic**:

1. `path != null` → `getDocsPageAsMarkdown`
2. `query != null` → `searchDocs`
3. else → `summarizeSections`

**Tool description requirements**: The tool description is the primary interface for AI
agents using this tool. It must:

- List every available `tag:` value with a one-line description of what it covers:
    - `tag:userguide` — User Guide (concepts, configuration, build lifecycle)
    - `tag:dsl` — Groovy DSL and Kotlin DSL API reference
    - `tag:javadoc` — Javadoc API reference
    - `tag:samples` — Sample projects (build files, READMEs)
    - `tag:release-notes` — Release notes for the installed version
- Show a concrete example of `tag:` syntax in the query: `"tag:javadoc Project dependencies"`
- Explain that omitting `tag:` searches all sections
- Explain the `path` parameter for reading a specific page by path (relative to `converted/`)

## Migration / Compatibility

- The `releaseNotes: Boolean` and `kind: DocsKind` parameters on `QueryGradleDocsArgs` are
  removed. Use `tag:release-notes` in the query, or `path="release-notes.html"`, instead.
- The skill file `skills/reading_gradle_docs/SKILL.md` must be updated to document `tag:` syntax
  with examples for each section.
