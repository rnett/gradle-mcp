# Tasks: gradle-best-practices

## Phase 1: Create the `best-practices-generator` module

- [x] **Task 1.1**: Add `include("best-practices-generator")` to `settings.gradle.kts`.
- [x] **Task 1.2**: Create `best-practices-generator/build.gradle.kts` with `kotlin-jvm` plugin and dependencies on `org.jsoup:jsoup` and `com.vladsch.flexmark:flexmark-html2md-converter` (from the existing version catalog).
- [x] **Task 1.3**: Create the source directory tree: `best-practices-generator/src/main/kotlin/dev/rnett/gradle/mcp/bestpractices/`.

## Phase 2: Implement the generator main class

- [x] **Task 2.1**: Create `GenerateBestPracticesDoc.kt` with a `main(args: Array<String>)` that parses args: `<output-dir> <gradle-version>`.
- [x] **Task 2.2**: Implement docs ZIP download: construct the URL `https://services.gradle.org/distributions/gradle-{version}-docs.zip`, download with `java.net.URL.openStream()`, write to a temp file, then open with `java.util.zip.ZipFile`. (Simple, no library needed — `InputStream.readAllBytes()`).
- [x] **Task 2.3**: Walk ZIP entries. For each entry matching `docs/userguide/*best_practices*`, read bytes as UTF-8 string.
- [x] **Task 2.4**: For each matching entry: parse with Jsoup, select `main.main-content`, remove `script, style, link, meta, wbr, .edit-link`, convert to Markdown with `FlexmarkHtmlConverter`.
- [x] **Task 2.5**: Extract the page title from the HTML `<title>` or the first `<h1>` in the content area.
- [x] **Task 2.6**: After all pages are processed, assemble `best_practices.md`:
  - Generated header with timestamp, Gradle version, "do not edit" notice, and canonical source URL.
  - Table of contents linking to each page section.
  - One section per page, with title heading and converted Markdown body.
  - Footer: "For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`."
- [x] **Task 2.7**: Normalize internal links: rewrite relative `docs.gradle.org` links to absolute URLs with a `gradle_docs(path=...)` hint.
- [x] **Task 2.8**: Write `best_practices.md` to the output directory.
- [x] **Task 2.9**: Add empty-extraction guard: if no pages are found, throw an error with a descriptive message listing the version and entries examined.

## Phase 3: Wire the Gradle task

- [x] **Task 3.1**: Register `generateBestPracticesDoc` (`JavaExec`) in root `build.gradle.kts`. Use `project(":best-practices-generator").sourceSets.main.get().runtimeClasspath` as the classpath. Set `mainClass = "dev.rnett.gradle.mcp.bestpractices.GenerateBestPracticesDoc"`.
- [x] **Task 3.2**: Set `args` = output references dir + `gradle.gradleVersion`.
- [x] **Task 3.3**: Declare up-to-date inputs/outputs:
  - `inputs.property("gradleDocsVersion", gradle.gradleVersion)`
  - `inputs.files(project(":best-practices-generator").sourceSets.main.get().kotlin).withPathSensitivity(PathSensitivity.RELATIVE)`
  - `outputs.file(project.rootDir.resolve("src/main/skills/gradle/references/best_practices.md"))`
- [x] **Task 3.4**: Wire `zipSkills.dependsOn(generateBestPracticesDoc)`. 
- [x] **Task 3.5**: Verify no circular dependency: run `./gradlew zipSkills` and confirm it configures and runs correctly.

## Phase 4: Generated content + skill integration

- [x] **Task 4.1**: Run `./gradlew generateBestPracticesDoc` to produce the initial `best_practices.md`. Review structure and coverage.
- [x] **Task 4.2**: Update `src/main/skills/gradle/SKILL.md`:
  - References section: point to the generated `references/best_practices.md` as the authoritative offline reference.
  - Performance Audit workflow: consult the generated reference first, use `gradle_docs tag:best-practices` for version-specific or deeper queries.
  - Remove the static-snapshot "MUST use gradle_docs" disclaimer.
- [x] **Task 4.3**: Commit the generated `best_practices.md`.

## Phase 5: Verification

- [x] **Task 5.1**: Add unit tests for path filtering (`isBestPracticesPage`) and content extraction (`extractContent`, `convertToMarkdown`) against fixture HTML files. No network required.
- [x] **Task 5.2**: Add a generation smoke test: run `generateBestPracticesDoc` and assert the output is non-empty, contains the generated header, a table of contents, and at least 3 topic sections.
- [x] **Task 5.3**: Add an incrementality test: assert a second run is `UP-TO-DATE`.
- [x] **Task 5.4**: Confirm `zipSkills` bundles the regenerated reference.
- [x] **Task 5.5**: Run `./gradlew check`; run `./gradlew :updateToolsList` if any tool metadata changed (none expected).

## Phase 6: Per-subsection file splitting

- [x] **Task 6.1**: Add `BestPracticesSection` data class with title, markdown, tags, sourcePage fields.
- [x] **Task 6.2**: Implement `splitPageIntoSections()` — split pages with >3 `##` subsections into individual sections; promote heading levels by one; extract and remove `### Tags` section content.
- [x] **Task 6.3**: Refactor `writePages()` to write per-section files instead of per-page files; clean stale files from output directory.
- [x] **Task 6.4**: Generate `_index.md` with a table of topics, files, and tags.
- [x] **Task 6.5**: Update tests for per-section output and index generation.
- [x] **Task 6.6**: Regenerate output and verify per-subsection files exist alongside small whole-page files.

## Phase 7: Remove index-like files and metadata artifacts

- [x] **Task 7.1**: Exclude `best_practices.html` and `best_practices_index.html` from extraction (redundant index/navigation pages).
- [x] **Task 7.2**: Remove `README.md` index generation from `writePages()`.
- [x] **Task 7.3**: Remove `<!-- Source: ... -->` comment headers from generated per-page files.
- [x] **Task 7.4**: Update SKILL.md to reference `references/best-practices/` directory instead of a single file.
- [x] **Task 7.5**: Regenerate and verify only per-topic content files remain.

## Phase 8: Categorized index with discoverability

- [x] **Task 8.1**: Add `extractSummary()` — first-paragraph extraction after H1, markdown link stripping, `gradle_docs` annotation removal, whitespace normalization, 160-char word-boundary truncation with ellipsis.
- [x] **Task 8.2**: Extract `assignSectionFiles()` for consistent collision-safe filename assignment shared between index generation and file writing.
- [x] **Task 8.3**: Rewrite `generateIndex()` to group entries by `sourcePage` (area) with one-line summaries, backtick-wrapped tags, and an inverted "Browse by Tag" cross-reference section.
- [x] **Task 8.4**: Update SKILL.md Resources entry and both workflow steps (Performance Audit, Documentation Research) to direct agents to read `_index.md` first, pick by area/tag, then open the detail file.
- [x] **Task 8.5**: Add tests for `extractSummary` (link stripping, annotation removal, truncation) and categorized index format (area grouping, summaries, tags, Browse by Tag).
- [x] **Task 8.6**: Regenerate and verify categorized `_index.md` — 7 area headings, ~37 entries with summaries and tags, Browse by Tag section at bottom.
