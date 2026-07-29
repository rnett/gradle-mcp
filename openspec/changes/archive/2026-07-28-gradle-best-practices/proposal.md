## Why

The `gradle` skill ships a handwritten `best_practices.md` snapshot (~110 lines) in its `references/` directory. The file itself warns that agents "MUST use the `gradle_docs` tool" for authoritative guidance, because the snapshot drifts out of date as Gradle evolves. That forces a `gradle_docs` round-trip for nearly every build-quality question, even though a comprehensive offline reference would suffice for most cases.

The project already has the building blocks for a solution: Jsoup for HTML parsing, Flexmark for HTML-to-Markdown conversion, and the knowledge that Gradle's docs distribution (`gradle-{version}-docs.zip`) contains HTML best-practices pages that can be extracted and converted programmatically. What's missing is a build-time mechanism that automates that extraction into a usable reference.

This change adds a lightweight `best-practices-generator` module — a standalone Gradle subproject with just Jsoup and Flexmark as dependencies — and a `generateBestPracticesDoc` task that downloads the docs distribution for the project's own Gradle version, extracts all best-practices HTML pages, converts them to Markdown, splits large pages into per-subsection files, generates a categorized `_index.md`, and writes everything into the `gradle` skill's `references/best-practices/` directory.

## What Changes

- **New `best-practices-generator` module** — a small Kotlin subproject (`best-practices-generator/`) with a single main class. Its only dependencies are Jsoup and Flexmark. No Koin, no Ktor, no Lucene, no project-specific infrastructure.
- **New `GenerateBestPracticesDoc` main class** — downloads `gradle-{version}-docs.zip`, walks the ZIP entries for best-practices pages (paths containing `best_practices`), extracts the content area with Jsoup, converts to Markdown with Flexmark, and writes per-subsection files into a `best-practices/` directory plus a categorized `_index.md` index.
- **New `generateBestPracticesDoc` Gradle task** in the root project — a `JavaExec` whose `classpath` is the output of the `best-practices-generator` subproject, so `zipSkills.dependsOn(generateBestPracticesDoc)` creates no cyclic dependency.
- **Per-subsection file splitting** — pages with more than 3 `##` subsections are split into individual `.md` files; heading levels are promoted by one; `### Tags` sections are extracted for the index. Small pages (≤3 subsections) remain as single files. Large pages stay as whole-page files instead of being split.
- **Categorized `_index.md`** — groups entries by source page (area), each entry has an auto-derived one-line summary (first paragraph, links stripped, 160-char truncation), backtick-wrapped tags, and a "Browse by Tag" inverted cross-reference at the bottom.
- **Index-like page exclusion** — `best_practices.html` and `best_practices_index.html` are excluded from extraction as redundant navigation pages.
- **No metadata artifacts** — generated files contain no `<!-- Source: ... -->` headers or `README.md` indices.
- **Skill integration** — SKILL.md points to `references/best-practices/_index.md` as the entry point. Workflows instruct agents to read `_index.md` first, pick by area/tag, then open the linked detail file.

## Capabilities

### New Capabilities

- `gradle-best-practices`: The generated best-practices reference — a `best-practices/` directory with individual `.md` files per best-practice subsection plus a categorized `_index.md` index covering all official Gradle best practices pages (DSL style, performance, dependencies, tasks, configuration cache, project integrity, security, testing), generated from the Gradle docs distribution.
- `generate-best-practices-doc`: The build-time generation task and standalone generator module — mechanism, extraction source, output format, incrementality, and bundling.
- `gradle-skill-best-practices-integration`: How the `gradle` skill references and consumes the generated best-practices content.

### Modified Capabilities

- None. The existing `gradle_docs` tool and Lucene-based pipeline are unchanged; the generator uses the raw docs distribution directly, independent of the existing index.

## Impact

- **`settings.gradle.kts`**: `include("best-practices-generator")`.
- **`best-practices-generator/build.gradle.kts`** (new): Kotlin JVM subproject with `org.jsoup:jsoup` and `com.vladsch.flexmark:flexmark-html2md-converter` dependencies.
- **`best-practices-generator/src/main/kotlin/.../GenerateBestPracticesDoc.kt`** (new): The standalone main class.
- **`build.gradle.kts`** (root): New `generateBestPracticesDoc` `JavaExec` task referencing the subproject's classpath; `zipSkills.dependsOn(generateBestPracticesDoc)`.
- **`src/main/skills/gradle/references/best_practices.md`**: Replaced by generated content.
- **`src/main/skills/gradle/SKILL.md`**: Updated to reference the generated reference and document the lookup order.
- **Out of scope**: Changes to the existing `gradle_docs` pipeline (Lucene index, DI, Koin), MCP tool schemas, token budgets, verify tasks, or progressive-disclosure document structures.
