# Tasks: Gradle Docs Tool Rework

Ordered implementation checklist. Complete tasks in order; each builds on the previous.

> **Concurrency model**: A single OS-level file lock (`FileChannel.lock()` on
> `{version}/.lock`) covers the entire pipeline — download, extraction, conversion, and
> indexing — for a given version. Multiple MCP instances share the same cache directory;
> the lock ensures only one runs the pipeline at a time. Individual `.done` markers per
> step allow resumption after an interrupted run.
>
> The lock is managed by a private `ensurePrepared(version: String)` function in
> `DefaultGradleDocsService`. All public service methods call `ensurePrepared` before
> accessing content. `ContentExtractorService` and `GradleDocsIndexService` do NOT manage
> the lock themselves — they are always called while the lock is already held (or after
> their `.done` marker confirms work is complete).

---

## 1. Add dependencies

- [x] In `gradle/libs.versions.toml`, add:
    - `lucene` version (latest stable 9.x) with aliases: `lucene-core`, `lucene-analysis-common`, `lucene-queryparser`.
    - Check whether `flexmark` is already present (the existing `MarkdownService` already
      imports `FlexmarkHtmlConverter`). Add only if missing.
- [x] In `build.gradle.kts`, add Lucene libraries to the `implementation` block.
- [x] Run `./gradlew dependencies` to confirm resolution.

---

## 2. Create `DocsKind` enum

- [x] Create `src/main/kotlin/dev/rnett/gradle/mcp/DocsKind.kt`.
- [x] Define `enum class DocsKind(val dirName: String)` with values:
  `USERGUIDE("userguide")`, `DSL("dsl")`, `JAVADOC("javadoc")`, `SAMPLES("samples")`,
  `RELEASE_NOTES("release-notes")`.
  Note: `ALL` is no longer needed — omitting `tag:` in the query searches everything.

---

## 3. Add `convertHtml(html: String)` to `MarkdownService` and create `HtmlConverter`

`MarkdownService.convertHtml` is a **flexmark-only** wrapper — no Jsoup pre-processing.
Per-kind Jsoup logic (content root selection, element stripping) lives in `HtmlConverter`.

- [x] Add `fun convertHtml(html: String): String` to the `MarkdownService` interface.
- [x] Implement in `DefaultMarkdownService`: run **only** the flexmark conversion on the
  provided HTML string. Do NOT carry over the broad `nav/header/footer` stripping from
  `downloadAsMarkdown` — that stripping stays in `downloadAsMarkdown` (for the existing
  HTTP-fetch path) but is not part of `convertHtml`.
- [x] Refactor `downloadAsMarkdown` to call `convertHtml` after its existing Jsoup cleanup.
- [x] Create `HtmlConverter` (plain class, no interface needed — stateless, no dependencies):
    - `fun convert(html: String, kind: DocsKind): String`
    - Implements per-kind Jsoup strategy from `specs/conversion.md`: select content root,
      strip kind-specific elements, then call `markdownService.convertHtml(root.html())`.
- [x] Write `HtmlConverterTest` using a snapshot-style harness and real HTML files from
  `src/test/resources/docs-html-samples/`.
- [x] Update `MarkdownServiceTest`.

---

## 4. Add test resources

- [x] Download `gradle-9.x-docs.zip` (or use already-downloaded copy from exploration).
- [x] Extract and copy sample files into `src/test/resources/docs-html-samples/`.
- [x] Generate initial snapshots in `src/test/resources/docs-md-expected/`.

---

## 5. Implement `DistributionDownloaderService`

- [x] Create interface + `DefaultDistributionDownloaderService`.
- [x] Use `-docs` distribution URL: `https://services.gradle.org/distributions/gradle-{version}-docs.zip`.
- [x] Implement: cache check, `.part` download, atomic rename, error cleanup, mutex.
- [x] Write `DistributionDownloaderServiceTest` (mocked HTTP).

---

## 6. Implement `ContentExtractorService`

- [x] Create interface + `DefaultContentExtractorService`.
- [x] Expose `convertedDirs(version, kind): List<Path>` — returns two paths for `DSL`
  (`converted/dsl/` + `converted/kotlin-dsl/`), one for all other kinds.
- [x] Implement `ensureProcessed(version)`: single ZIP pass — for each entry, convert HTML
  in-memory using the kind-specific Jsoup strategy (see `specs/conversion.md`) and write
  the `.md` result directly to `converted/`. Write non-HTML text and images as-is.
  Skip web noise (JS/CSS). No intermediate `extracted/` directory is written.
- [x] `DSL` processes both `docs/dsl/` and `docs/kotlin-dsl/` prefixes in the same pass.
- [x] For `SAMPLES` during the main pass: root-level HTML files (`docs/samples/{name}.html`,
  i.e., no `/` in the relative path after stripping the `docs/samples/` prefix) are written
  to `converted/samples/{name}/README.md` (not `converted/samples/{name}.md`).
- [x] For `SAMPLES` nested ZIPs: re-open the outer ZIP after the main pass; for each nested
  sample ZIP under `docs/samples/zips/`:
    - Split the ZIP filename (without extension) on the last `-groovy-dsl` or `-kotlin-dsl`
      suffix to get base name and variant (e.g., `sample_foo-kotlin-dsl` → base=`sample_foo`,
      variant=`kotlin-dsl`). Either or both variants may be present — neither is required.
    - Write all nested ZIP entries into `converted/samples/{base}/{variant}/{entry-path}`.
- [x] Apply zip-slip guard to all paths (outer and nested ZIPs).
- [x] Write `converted/.done` on completion.
- [x] Write `ContentExtractorServiceTest` with synthetic ZIPs.

---

## 7. Implement `GradleDocsIndexService` (Unified)

- [x] Create interface + `DefaultGradleDocsIndexService`.
- [x] Extract `LuceneReaderCache` and `LuceneUtils` to `dev.rnett.gradle.mcp.lucene`.
- [x] Implement `ensureIndexed(version)`:
    - Call `ensureProcessed(version)` (handles download + extract+convert).
    - Walk `converted/` recursively; all files are text.
    - Extract title from first `# Heading` line, fall back to filename.
    - Store `path` matching processed file structure (e.g., `.md`).
    - Index `tag`, `path`, `title`, `body`.
    - Use `UnifiedHighlighter` for dynamic snippets in `search`.
- [x] Implement `search(query, version, maxResults)` — pass query directly to `QueryParser`;
  `tag:X` filtering is handled natively by Lucene, no special parsing needed.
- [x] Write `GradleDocsIndexServiceTest` with temp dir and synthetic docs.

---

## 8. Update `GradleDocsService` interface and `DocsSearchResult`

- [x] Add `val tag: String` to `DocsSearchResult` (the primary section tag of the result).
- [x] Introduce `DocsPageContent` sealed class for `Markdown` and `Image`.
- [x] Replace current `GradleDocsService` methods with:
    - `getDocsPageContent(path: String, version: String?): DocsPageContent`
    - `getReleaseNotes(version: String?): String`
    - `searchDocs(query: String, version: String?): List<DocsSearchResult>` — no kind param; tag: in query
    - `summarizeSections(version: String?): List<DocsSectionSummary>`

---

## 9. Update `DefaultGradleDocsService`

- [x] Update constructor to accept new dependencies.
- [x] Implement `summarizeSections`: walk `converted/` top-level dirs, count files per section,
  return summary list.
- [x] Implement `getDocsPageContent`: call `ensureProcessed`, resolve path under
  `converted/` using exactly the path returned by search, return file contents or base64 image.
- [x] Implement `getReleaseNotes`: helper calling `getDocsPageContent("release-notes.md", version)`.
- [x] Implement `searchDocs`: call `indexer.search(...)`.
- [x] Update `GradleDocsServiceTest`.

---

## 10. Update `GradleDocsTools`

- [x] In `QueryGradleDocsArgs`: remove `val releaseNotes: Boolean` and `val kind`. Only
  `query`, `path`, `version`, `projectRoot` remain.
- [x] Update dispatch logic: `path` → read (returns text or image); `query` → search; no args → summarize.
- [x] Update tool description to prominently document `tag:` syntax, list all available tags,
  and mention image support.
- [x] Update `GradleDocsToolsTest`.

---

## 11. Finalize DI and verification

- [x] Bind new services in `DI.kt`.
- [x] Update `UpdateTools.kt` and `BaseReplIntegrationTest.kt`.
- [x] Run `:updateToolsList`.
- [x] Update `skills/reading_gradle_docs/SKILL.md`: prominently document `tag:` syntax, list all
  available tags, and provide examples for each section including images.
- [x] Run `./gradlew check`.
