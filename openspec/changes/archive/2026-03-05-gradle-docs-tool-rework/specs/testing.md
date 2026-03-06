# Spec: Test Plan

## Test Files

| File                                   | Tests                                                                                                                                                   |
|----------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DistributionDownloaderServiceTest.kt` | Download, caching, error handling                                                                                                                       |
| `ContentExtractorServiceTest.kt`       | ZIP processing, inline HTML→Markdown conversion, kind filtering, zip-slip guard; uses real HTML files from test resources to validate conversion output |
| `LuceneIndexServiceTest.kt`            | Index build, search correctness, query parsing                                                                                                          |
| `GradleDocsServiceTest.kt`             | Updated: orchestration, page reads, version resolution                                                                                                  |
| `GradleDocsToolsTest.kt`               | Updated: `kind` dispatch, removed `releaseNotes`                                                                                                        |
| `MarkdownServiceTest.kt`               | New: `convertHtml(String)` method                                                                                                                       |

## Test Resources

Real HTML files copied from a Gradle 9.x `-docs` distribution are stored in
`src/test/resources/docs-html-samples/`. These may be slightly out of date but must
reflect the actual structure of each kind. See `specs/conversion.md` for the full list.

---

## DistributionDownloaderServiceTest

### Unit tests (mocked HTTP client)

- **`cachedZipReturnedImmediately`**: If ZIP exists on disk, no HTTP call is made.
- **`downloadsToPartFile`**: HTTP response is written to `.part` file during download.
- **`renamedAfterSuccess`**: `.part` file renamed to final name after successful download.
- **`partFileDeletedOnFailure`**: `.part` file is cleaned up when download returns non-2xx.
- **`throwsOnHttpError`**: Exception message contains URL and status code.
- **`concurrentDownloadDeduplication`**: Two concurrent calls for same version result in
  exactly one HTTP request (file lock test — simulate with two threads each acquiring the lock).

### Integration tests (optional, network-dependent, disabled by default)

- **`downloadsRealDistribution`**: Downloads a known small release (e.g., `8.0`) and
  verifies ZIP is non-empty. Annotate with `@Tag("integration")` or skip via environment
  variable.

---

## ContentExtractorServiceTest

All tests use a synthetic in-memory ZIP constructed in the test.

- **`htmlConvertedToMarkdown`**: ZIP contains `gradle-X/docs/userguide/a.html`. After
  `ensureProcessed(version)`, `converted/userguide/a.md` exists and `converted/userguide/a.html` does not.
- **`javadocHtmlConverted`**: ZIP contains `gradle-X/docs/javadoc/org/gradle/api/Project.html`.
  After processing, `converted/javadoc/org/gradle/api/Project.md` exists.
- **`dslProcessesBothPrefixes`**: ZIP contains both `gradle-X/docs/dsl/org.gradle.api.Project.html`
  and `gradle-X/docs/kotlin-dsl/gradle/org.gradle/index.html`. After `ensureProcessed(version)`,
  both appear as `.md` files: one under `converted/dsl/`, the other under `converted/kotlin-dsl/`.
- **`sampleDescriptionPageConverted`**: ZIP contains `gradle-X/docs/samples/sample_java.html`.
  After `ensureProcessed(version)`, `converted/samples/sample_java/README.md` exists (not
  `converted/samples/sample_java.md` — root-level sample HTML gets the special path treatment).
- **`nestedSampleZipUnpackedKotlin`**: ZIP contains a nested `gradle-X/docs/samples/zips/sample_java-kotlin-dsl.zip`
  (with `README` and `build.gradle.kts`).
  After processing: `converted/samples/sample_java/kotlin-dsl/README` and
  `converted/samples/sample_java/kotlin-dsl/build.gradle.kts` both exist.
- **`nestedSampleZipUnpackedGroovy`**: ZIP contains only a groovy-dsl nested ZIP
  (`sample_java-groovy-dsl.zip`). After processing: `converted/samples/sample_java/groovy-dsl/`
  exists; no `kotlin-dsl/` directory is created.
- **`sampleBothVariantsCoexist`**: ZIP contains both `-groovy-dsl` and `-kotlin-dsl` ZIPs for
  the same sample. After processing, both `converted/samples/sample_java/groovy-dsl/` and
  `converted/samples/sample_java/kotlin-dsl/` exist with independent contents.
- **`zipSlipRejectedInOuterZip`**: ZIP entry with `../../../etc/passwd` path is silently skipped.
- **`zipSlipRejectedInNestedSampleZip`**: Nested sample ZIP with a path-traversal entry is silently skipped.
- **`doneMarkerPreventsReProcessing`**: Second call to `ensureProcessed` does not re-open the ZIP.
- **`relativePathsPreserved`**: Nested directory structure within a kind is preserved.

---

## LuceneIndexServiceTest

All tests use a temp directory populated with synthetic HTML/text files.

- **`indexBuiltFromHtmlFiles`**: Index built from a directory containing 2 HTML files.
  `search("keyword")` returns both files that contain the keyword.
- **`titleExtractedFromH1`**: HTML without `<title>` uses `<h1>` text as title in result.
- **`nonHtmlFilesIndexed`**: A `.gradle.kts` file in `converted/samples/sample_java/kotlin-dsl/` is indexed and searchable by content.
- **`indexUsesConvertedContent`**: Indexer reads from `converted/` — verified by placing known text in a file under `converted/` and confirming search returns it.
- **`binaryFilesSkipped`**: A `.class` file in the dir does not cause an error and is not
  returned in results.
- **`doneMarkerPreventsRebuild`**: Second `ensureIndexed` call with done marker present
  does not acquire the file lock or re-open IndexWriter.
- **`emptyQueryReturnsEmpty`**: Blank query string returns empty list.
- **`malformedQueryFallsBackToTermQuery`**: Query with unmatched `[` bracket does not
  throw; falls back to term search.
- **`snippetTruncatedAt500Chars`**: Document with long body has snippet ≤ 500 chars.
- **`maxResultsRespected`**: Index with 30 documents; `search(..., maxResults=5)` returns
  exactly 5.

---

## GradleDocsServiceTest (updated)

- **`searchDocsUsesLucene`**: `searchDocs("query", "8.10")` invokes
  `LuceneIndexService.search`, not HTTP requests.
- **`readPageReadsFromConvertedDir`**: `getDocsPageAsMarkdown("userguide/about_manual.html", version)`
  reads from `converted/userguide/about_manual.md` directly — no network, no conversion on read.
- **`readPageReturnedDirectly`**: Content served straight from `converted/` — no additional processing.
- **`readDslGroovyPage`**: `getDocsPageAsMarkdown("dsl/org.gradle.api.Project.html", version)`
  resolves to `converted/dsl/org.gradle.api.Project.md`.
- **`readDslKotlinPage`**: `getDocsPageAsMarkdown("kotlin-dsl/gradle/org.gradle/index.html", version)`
  resolves to `converted/kotlin-dsl/gradle/org.gradle/index.md`.
- **`readSampleReadme`**: `getDocsPageAsMarkdown("samples/sample_building_java_applications/README.md", version)`
  resolves to `converted/samples/sample_building_java_applications/README.md` and returns its Markdown content.
- **`readSampleSourceFile`**: `getDocsPageAsMarkdown("samples/sample_building_java_applications/kotlin-dsl/app/build.gradle.kts", version)`
  returns raw file content as a fenced code block.
- **`readNonHtmlReturnsRawText`**: Any non-HTML text file returns raw content in a fenced block.
- **`binaryFileReturnsError`**: Path pointing to a `.png` or `.jar` returns an error, not content.
- **`releaseNotesReadsFromConvertedFile`**: `getDocsPageAsMarkdown("release-notes.md", version)`
  reads from `converted/release-notes.md`.
- **`summarizeSectionsCountsFiles`**: `summarizeSections(version)` returns one entry per
  top-level directory under `converted/` with an accurate file count.
- **`versionResolutionUnchanged`**: Existing version-from-canonical-link behavior is
  preserved (test migrated from old `GradleDocsVersionDetectionTest.kt`).

---

## GradleDocsToolsTest (updated)

- **`pathDispatchesToReadPage`**: `path` arg calls `getDocsPageAsMarkdown(path, version)`.
- **`queryDispatchesToSearch`**: `query` arg calls `searchDocs(query, version)` — no kind param.
- **`noArgsDispatchesToSummarize`**: No args calls `summarizeSections`.
- **`releaseNotesBooleanArgRemoved`**: `QueryGradleDocsArgs` has no `releaseNotes` or `kind` field.
- **`tagFilterInQueryFiltersResults`**: `query="tag:userguide foo"` returns only userguide results.
- **`noTagReturnsAllSections`**: `query="dependencies"` returns results from multiple sections.
- **`tagDslIncludesKotlinDslContent`**: `query="tag:dsl keyword"` returns results from both `dsl/` and `kotlin-dsl/` converted content (both indexed with `tag=dsl`).

---

## MarkdownServiceTest (updated)

- **`convertHtmlString`**: `markdownService.convertHtml("<h1>Hello</h1><p>World</p>")`
  returns expected markdown (existing logic, new entry point).
- Existing `downloadAsMarkdown` tests unchanged.

---

## Test Infrastructure Notes

- Use `MockEngine` (Ktor test) for all HTTP mocking.
- Use `@TempDir` (JUnit 5) for all file system tests.
- Lucene tests use `ByteBuffersDirectory` (in-memory) where possible for speed.
- Integration tests (real network) are tagged `@Tag("integration")` and excluded from
  the default `check` task. Run with `./gradlew check -Pintegration`.
