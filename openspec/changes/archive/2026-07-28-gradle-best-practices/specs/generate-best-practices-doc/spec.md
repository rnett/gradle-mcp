# Capability: generate-best-practices-doc

## Purpose

Defines a build-time mechanism — a standalone Gradle subproject and `JavaExec` task — that downloads the Gradle documentation distribution, extracts all best-practices HTML pages, converts them to Markdown, splits large pages into per-subsection files, generates a categorized `_index.md`, and writes everything into the `gradle` skill's `references/best-practices/` directory.

## ADDED Requirements

### Requirement: Standalone generator module

There SHALL be a `best-practices-generator` Gradle subproject with a single main class (`GenerateBestPracticesDoc`). Its only dependencies SHALL be `org.jsoup:jsoup` for HTML parsing and `com.vladsch.flexmark:flexmark-html2md-converter` for HTML-to-Markdown conversion. It SHALL NOT depend on Koin, Ktor, Lucene, or any other class or module from the main `gradle-mcp` project.

#### Scenario: Decoupled build

- `WHEN` the `best-practices-generator` project is compiled
- `THEN` it SHALL compile with only Jsoup and Flexmark on its classpath (plus the Kotlin stdlib)
- `AND` it SHALL NOT pull in any dependency from the main project (no transitive DI, Ktor, or Lucene).

### Requirement: Extracts best-practices pages from the docs distribution

The generator SHALL download `gradle-{version}-docs.zip` from `https://services.gradle.org/distributions/` for a given concrete Gradle version, walk all ZIP entries, and select every HTML page whose path contains both `userguide/` and `best_practices`.

#### Scenario: Best-practices page selection

- `WHEN` the generator iterates ZIP entries for version `X.Y`
- `THEN` it SHALL select entries whose path matches the pattern `docs/userguide/*best_practices*`
- `AND` it SHALL skip entries outside the `userguide/` directory and entries whose path does not contain `best_practices`.

#### Scenario: Empty extraction guard

- `WHEN` no entries match the best-practices pattern
- `THEN` the generator SHALL fail with a descriptive error message
- `AND` SHALL NOT overwrite any existing output file.

#### Scenario: Index-like page exclusion

- `WHEN` the generator iterates ZIP entries
- `THEN` it SHALL exclude `best_practices.html` and `best_practices_index.html` as redundant navigation pages
- `AND` SHALL NOT extract content from these index-like aggregation pages.

### Requirement: Converts HTML to Markdown

For each selected entry, the generator SHALL:
1. Parse the HTML with Jsoup.
2. Select the content area (`main.main-content` element).
3. Remove navigation, sidebar, script, style, and edit-link elements.
4. Convert the cleaned HTML to Markdown via Flexmark's `FlexmarkHtmlConverter`.
5. Extract the page title from the HTML `<title>` or first `<h1>`.

#### Scenario: Content extraction

- `WHEN` converting a best-practices HTML page
- `THEN` the output Markdown SHALL contain the page's substantive content
- `AND` SHALL NOT contain navigation elements, sidebar links, edit links, scripts, or styles.

### Requirement: Per-subsection splitting

Pages with more than 3 `##` level subsections SHALL be split into individual Markdown files at each `##` heading boundary. Heading levels within split sections SHALL be promoted by one (`##` → `#`, `###` → `##`). Pages with ≤3 subsections remain as single whole-page files without splitting. The `### Tags` section content SHALL be extracted for the index and removed from generated files.

#### Scenario: Large page is split

- `WHEN` a page has more than 3 `##` subsections
- `THEN` the generator SHALL produce multiple `.md` files, one per subsection
- `AND` headings SHALL be promoted by one level
- `AND` `### Tags` sections SHALL be removed from the generated markdown but tags preserved for the index.

#### Scenario: Small page stays whole

- `WHEN` a page has 3 or fewer `##` subsections
- `THEN` the generator SHALL write it as a single `.md` file
- `AND` SHALL NOT apply heading promotion or splitting.

### Requirement: Produces per-subsection directory output

The generator SHALL write files into a `best-practices/` directory under the references output path:
- Individual `.md` files for each best-practice subsection (from large pages) or whole small pages.
- A categorized `_index.md` file as the entry point with area grouping, summaries, tags, and "Browse by Tag" cross-reference.
- No monolithic `best_practices.md`, no `README.md` indices, no `<!-- Source: ... -->` metadata headers in generated files.

#### Scenario: Generated directory structure

- `WHEN` the generator finishes successfully
- `THEN` the `best-practices/` directory SHALL exist with multiple `.md` files including `_index.md`
- `AND` SHALL NOT contain `best_practices.md`, `README.md`, or metadata artifacts.

### Requirement: Categorized index generation

The generator SHALL produce an `_index.md` that groups entries by source page (area). Each entry includes an auto-derived one-line summary (first paragraph after H1, markdown links stripped, `gradle_docs` annotation removal, whitespace normalization, 160-char word-boundary truncation), backtick-wrapped tags, and an inverted "Browse by Tag" section at the bottom listing all tags alphabetically with associated slugs.

#### Scenario: Index discoverability

- `WHEN` the `_index.md` is generated
- `THEN` it SHALL start with a description directing agents to read it first and pick by area/tag
- `AND` SHALL group entries under source-page headings with summaries and tags
- `AND` SHALL include a "Browse by Tag" section at the bottom.

### Requirement: Incremental via Gradle's up-to-date checks

The `generateBestPracticesDoc` task SHALL declare its inputs (Gradle version, generator source files) and outputs (the `best-practices/` directory). Gradle's standard `UP-TO-DATE` checking SHALL skip execution when inputs are unchanged and the output exists.

#### Scenario: Second run is UP-TO-DATE

- `WHEN` `generateBestPracticesDoc` runs a second time with the same Gradle version and unchanged generator sources
- `THEN` the task SHALL be marked `UP-TO-DATE`
- `AND` SHALL NOT download the docs distribution or rewrite the output.

#### Scenario: Version change triggers regeneration

- `WHEN` the Gradle version changes (e.g., wrapper upgrade)
- `THEN` the task SHALL execute again, downloading the new version's docs distribution and regenerating the output.

### Requirement: Bundled into skills archive

The `zipSkills` task SHALL depend on `generateBestPracticesDoc`, and the generated files in the `best-practices/` directory SHALL be included in the `skills.zip` archive under the `gradle` skill's `references/` directory.

#### Scenario: Skill bundling

- `WHEN` `zipSkills` runs
- `THEN` the `best-practices/` directory contents SHALL be present in `skills.zip` under the `gradle/references/` path.
