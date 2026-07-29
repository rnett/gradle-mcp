## Context

The `gradle` skill ships a handwritten `best_practices.md` (~110 lines) in `src/main/skills/gradle/references/` that warns agents it's incomplete and they "MUST use the `gradle_docs` tool." This forces a network round-trip for routine build-quality questions.

The codebase already has all the technical pieces:

- `org.jsoup:jsoup:1.22.2` — HTML parsing and content-area selection.
- `com.vladsch.flexmark:flexmark-html2md-converter:0.64.8` — standalone HTML-to-Markdown conversion.
- Gradle's docs distribution is downloadable from `https://services.gradle.org/distributions/gradle-{version}-docs.zip` and contains best-practices HTML pages under `docs/userguide/` with `best_practices` in the path.
- The project already has a subproject pattern: `repl-worker` and `repl-shared` are separate modules included via `settings.gradle.kts`.
- `build.gradle.kts` already has a `zipSkills` task that bundles `src/main/skills` into `skills.zip`.

What's missing is a build-time mechanism to extract, convert, and bundle those best-practices pages automatically.

**Key constraint:** `zipSkills` writes to `build/generated/resources/skills`, which is registered as a main-resources source directory via `sourceSets.main.resources.srcDir(...)`. This makes `processResources` depend on `zipSkills`, which makes `sourceSets.main.runtimeClasspath` transitively depend on `zipSkills`. A `JavaExec` using the main `runtimeClasspath` that `zipSkills` depends on would create a cycle. A separate module avoids this entirely: its classpath has no connection to the main source set's resources.

## Goals / Non-Goals

**-Goals:**

- A `generateBestPracticesDoc` Gradle task that produces a `best-practices/` directory with per-subsection Markdown files and a categorized `_index.md` from the official Gradle docs distribution.
- Output lands at `src/main/skills/gradle/references/best-practices/` so `zipSkills` bundles it automatically.
- Incremental on the Gradle version — normal Gradle up-to-date checks are sufficient; no custom incrementality model.
- Zero changes to the existing docs pipeline (DI, Koin, Lucene index, `gradle_docs` tool).
- No circular task dependencies with `zipSkills`.

**Non-Goals:**

- No token budgets or size enforcement.
- No separate `verifyBestPracticesDoc` task — the generated files are committed; regeneration is triggered by version changes.
- No Jsoup content-selector logic for every `DocsKind` — only the `userguide` content selector is needed (all best-practices pages are under `docs/userguide/`).
- No `listPathsByTag` API, no Lucene index changes, no DI changes.
- No runtime health-scoring algorithm or diagnostics tooling — the capability is reference content.

## Decisions

### Decision 1: Separate `best-practices-generator` module

A new Gradle subproject at `best-practices-generator/` holds the generator code. This is the cleanest way to avoid the classpath cycle with `zipSkills` and keeps the generator decoupled from the main project's runtime.

**Why not a standalone task within the main project?** The main project's `runtimeClasspath` transitively depends on `zipSkills` (via `processResources` output). Any `JavaExec` using `runtimeClasspath` that `zipSkills` depends on creates a cycle. While a reduced classpath (excluding resources) could work, it's fragile: any future change that adds a classpath resource to the pipeline would silently break. A separate module is the robust, conventional Gradle solution — exactly what `repl-worker` and `repl-shared` demonstrate.

**Why not buildSrc?** buildSrc classes are available to every build script, which is unnecessary. The generator runs only as a JavaExec task; a separate module is the natural boundary.

**Consequences:**
- Module has its own `build.gradle.kts` with just `kotlin-jvm` plugin + Jsoup + Flexmark dependencies.
- Main class lives at `dev.rnett.gradle.mcp.bestpractices.GenerateBestPracticesDoc`.
- Root `settings.gradle.kts` gains `include("best-practices-generator")`.
- Root `build.gradle.kts` task uses `classpath = project(":best-practices-generator").sourceSets.main.get().runtimeClasspath`.

### Decision 2: Main class walks ZIP entries directly (no Lucene index)

The generator downloads `gradle-{version}-docs.zip`, opens it with `java.util.zip.ZipFile`, and iterates all entries. For each entry whose path contains `userguide/` and `best_practices`:

1. Read entry bytes as UTF-8 string (HTML).
2. Parse with Jsoup, select the `main.main-content` element (the standard `userguide` content area).
3. Clean up: remove nav elements, breadcrumbs, edit links, script/style elements.
4. Convert to Markdown with Flexmark's `FlexmarkHtmlConverter`.
5. Accumulate into an ordered list of `(title, markdown)` pairs.

After all entries are processed, the generator writes per-subsection Markdown files into a `best-practices/` directory and a categorized `_index.md`:
- Per-section files with promoted heading levels and extracted tags.
- A categorized `_index.md` grouping entries by source page (area) with summaries, tags, and a "Browse by Tag" cross-reference.
- Small pages (≤3 subsections) are kept as single whole-page files within the directory.

Internal links are normalized: relative links to other docs pages are rewritten to absolute `docs.gradle.org` URLs with a `gradle_docs(path=...)` hint.

**Why no Lucene or DI?** The full extraction and indexing pipeline exists for the `gradle_docs` MCP tool (search across hundreds of pages). For best-practices generation, we only need ~5–15 pages from the `userguide/` directory. Walking a ZIP with `java.util.zip` is simpler, faster, and has zero project dependencies beyond Jsoup and Flexmark.

**Consequences:** The generator is fully standalone: no Koin, no Ktor, no service interfaces, no `GradleDocsIndexService`, no `ContentExtractorService`. It's a single Kotlin file with a `main()` function, Jsoup, and Flexmark.

### Decision 3: Pinned concrete version, defaulting to `gradle.gradleVersion`

The task input is a concrete Gradle version string (never `current`). The default is `gradle.gradleVersion` — the version of Gradle that runs the build (as determined by the wrapper). This means:

- The generated best-practices reference always matches the Gradle version used to build the project.
- No network call is needed to resolve version aliases.
- Gradle's standard up-to-date checking works: `inputs.property("gradleDocsVersion", version)` captures the version; a Gradle wrapper upgrade automatically triggers regeneration.
- A fresh checkout with a new wrapper version triggers one download + generation on first build, then `UP-TO-DATE` thereafter.

**Why not `current`/`latest`?** Resolving `current` requires a network call to `services.gradle.org/versions/current`, which would need to happen during task configuration or at execution time. Using the build's own Gradle version is trivially correct: the project should follow its own Gradle version's best practices.

**Overridable via `-PgradleDocsVersion=X.Y`** for maintainers who want to test or pin a specific version.

### Decision 4: Per-subsection file splitting with heading promotion

Pages with more than 3 `##` subsections are split into individual Markdown files; heading levels are promoted by one (so the original `##` becomes `#`, etc.); and `### Tags` sections are extracted for the index and removed from the generated files. Small pages (≤3 subsections) remain as single whole-page files.

The splitting algorithm works as follows:
1. Count `##` headings in the converted Markdown (both ATX and setext style).
2. If count > 3, split at each `##` boundary; promote each heading level by one (`##` → `#`, `###` → `##`, etc.).
3. Extract any `### Tags` section content, parse linked tags like `[tags](...)` to backtick-wrapped forms, and remove the Tags section from the generated markdown.
4. Pages with ≤3 subsections skip splitting entirely — they are written as single files with no heading promotion.

**Why split?** A single large file is harder for agents to navigate and reason about. Splitting gives each topic its own file while keeping related topics discoverable through the categorized `_index.md`. Small pages stay intact because splitting them would add more overhead than value.

**Why promote headings?** Each sub-section file starts with an `#` H1 title, making it a self-contained document. This matches how agents consume standalone reference files.

### Decision 5: Categorized `_index.md` with area grouping and tag cross-reference

The `_index.md` groups entries by `sourcePage` (the original Gradle doc page name, treated as an "area"). Each entry includes:
- A link to the detail file with the section title.
- An auto-derived one-line summary (first paragraph after H1, markdown links stripped, `gradle_docs` annotation removal, whitespace normalization, 160-char word-boundary truncation with ellipsis).
- Backtick-wrapped tags extracted from the section's `### Tags` metadata.

At the bottom, an inverted "Browse by Tag" section lists all tags alphabetically with references to the slugs of sections that carry each tag.

**Why this layout?** Agents typically know what *type* of best practice they need (performance, dependencies, etc.) but not the exact page name. The categorized index lets them scan by area or browse by tag without needing to guess file names. Auto-summaries give enough context to decide which file to open next.

**Why auto-derive summaries?** Manually writing summaries defeats the purpose of automation. The deterministic cleaning pipeline (link stripping, annotation removal, truncation) produces consistent output verified by focused unit tests.

### Decision 6: Exclude index-like pages

The files `best_practices.html` and `best_practices_index.html` are excluded from extraction. These are redundant navigation/aggregation pages that duplicate content available through the granular best-practices pages.

**Why exclude?** Including them would add noise: duplicate content headers, navigation elements, and index listings that provide no unique information beyond what the individual topic pages already contain.

### Decision 7: Gradle's own build cache + up-to-date are sufficient for incrementality

No custom incrementality model. The task declares:

- `inputs.property("gradleDocsVersion", version)` — the concrete Gradle version.
- `inputs.files(sourceSets.main.get().kotlin)` with `PathSensitivity.RELATIVE` — generator source changes trigger regeneration (only for the separate module's source).
- `outputs.files("src/main/skills/gradle/references/best-practices/")` — the generated directory.

Gradle's `UP-TO-DATE` and build cache handle everything: same version + same generator code + file exists = no work. Version bump = regeneration.

This is simpler than the earlier proposal's reduced-classpath + verify-task + two-phase approach.

### Decision 8: Task wiring

The root `build.gradle.kts` registers the task:

```kotlin
val generateBestPracticesDoc by tasks.registering(JavaExec::class) {
    val generatorProject = project(":best-practices-generator")
    classpath = generatorProject.sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.rnett.gradle.mcp.bestpractices.GenerateBestPracticesDoc")
    args = listOf(
        project.rootDir.resolve("src/main/skills/gradle/references").absolutePath,
        gradle.gradleVersion
    )
    inputs.property("gradleDocsVersion", gradle.gradleVersion)
    inputs.files(generatorProject.sourceSets.main.get().kotlin).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(project.rootDir.resolve("src/main/skills/gradle/references/best_practices.md"))
}

zipSkills.dependsOn(generateBestPracticesDoc)
```

No cycle risk: the classpath comes from a separate subproject, not from the main project's `runtimeClasspath`. The `processResources` → `zipSkills` → `generateBestPracticesDoc` dependency is a linear chain with no back-edge.

### Decision 9: Fail on empty extraction

If the ZIP contains no best-practices pages (pathological: wrong version, renamed pages), the generator fails with a clear error rather than overwriting the committed reference with an empty file.

This is one `if (pages.isEmpty()) error(...)` check in the main class. No atomic-write dance required — the existing file is simply not overwritten if the generator fails early.

## Risks / Trade-offs

- **[Risk] Download on first build.** The first build with a new Gradle version triggers a docs ZIP download. This is a one-time cost per version, identical to how the existing `gradle_docs` pipeline works.
- **[Resolved] Large single file.** Splitting into per-subsection files eliminates the risk of a single massive document. Small pages (≤3 subsections) remain as whole-page files; large pages are split.
- **[Risk] Auto-summaries may be imperfect.** The deterministic cleaning pipeline produces good results but edge cases exist. Mitigated by focused unit tests for `extractSummary()` covering link stripping, annotation removal, whitespace normalization, and truncation.
- **[Risk] `main.main-content` selector changes.** If Gradle changes its docs HTML layout, the Jsoup selector may stop matching. Mitigated by the empty-extraction guard: if no pages produce content, the build fails clearly.
- **[Trade-off] Separate module overhead.** Adds a `build.gradle.kts`, a small `src/` tree, and a `settings.gradle.kts` entry. This is a few dozen lines total and follows the existing `repl-worker`/`repl-shared` pattern.

## Verification

- **Unit (no network):** test the `best_practices` page detection (path filtering) and content extraction (Jsoup selector + Flexmark conversion) against fixture HTML files.
- **Per-section splitting:** test `splitPageIntoSections()` with a page that has >3 subsections — verify it produces multiple `BestPracticesSection` entries, promotes heading levels, extracts tags, and removes the Tags section from markdown.
- **Summary extraction:** test `extractSummary()` for link stripping, `gradle_docs` annotation removal, whitespace normalization, and 160-char truncation.
- **Index generation:** test `generateIndex()` verifies area grouping, per-entry summaries and tags, and "Browse by Tag" inverted index at the bottom.
- **File writing:** run `generateBestPracticesDoc` and assert the `best-practices/` directory contains per-section `.md` files plus `_index.md`; no stale `best_practices.md`, no `README.md`.
- **Small pages:** assert that small pages (≤3 subsections) are written as single whole-page files.
- **Index-like exclusion:** assert `best_practices.html` and `best_practices_index.html` produce zero sections.
- **Incrementality:** assert a second run is `UP-TO-DATE`.
- **Bundling:** confirm `zipSkills` includes the generated files.
- **Regression:** `./gradlew check` passes; run `./gradlew :updateToolsList` if any tool metadata changed (none expected).
