# Spec: Tool Interface

## Tool Name

`gradle_docs` (unchanged)

## Arguments

| Argument      | Type                      | Required | Default | Description                                                                 |
|---------------|---------------------------|----------|---------|-----------------------------------------------------------------------------|
| `query`       | `String?`                 | no       | null    | Full-text search query; use `tag:X` prefix to filter by section             |
| `path`        | `String?`                 | no       | null    | Relative path to a specific page/file to read                               |
| `version`     | `String?`                 | no       | null    | Gradle version (e.g. `"8.10"`); auto-detected from `projectRoot` if omitted |
| `projectRoot` | `GradleProjectRootInput?` | no       | null    | Project root for version auto-detection                                     |

The `kind` parameter has been removed. Section filtering is done via `tag:` in the query string.

## Dispatch Priority

Arguments are evaluated in priority order. The first matching condition determines behavior:

1. `path != null` → return the file at that path as text
2. `query != null` → full-text search, with optional `tag:` filter embedded in the query
3. _(none of the above)_ → return a summary of available documentation sections

---

## ⚠️ Tag-Based Section Filtering

**IMPORTANT**: To search within a specific section of the documentation, include a
`tag:` prefix in your query. This is the primary way to filter results.

### Available Tags

**Primary section tags** — every document has exactly one:

| Tag                 | Content                                                                       |
|---------------------|-------------------------------------------------------------------------------|
| `tag:userguide`     | Gradle User Guide — task authoring, plugins, configuration, CLI, etc.         |
| `tag:dsl`           | Gradle Build Language Reference — Groovy DSL types AND Kotlin DSL API, merged |
| `tag:javadoc`       | Gradle Java API (Javadoc)                                                     |
| `tag:samples`       | Sample projects — description pages, build scripts, README files              |
| `tag:release-notes` | Release notes for the specified version                                       |

**Sub-tags** — applied in addition to the primary tag for specific content groupings:

| Tag                  | Content                                        | Primary tag     |
|----------------------|------------------------------------------------|-----------------|
| `tag:best-practices` | Best practices guides (`best_practices*.html`) | `tag:userguide` |

Sub-tags allow narrow searches: `tag:best-practices` finds only best-practices pages,
while `tag:userguide` still finds them alongside all other User Guide content.

Omitting a `tag:` filter searches across **all** sections simultaneously.

### Query Examples

```
"configuration cache"                           → search everything
"tag:userguide configuration cache"             → User Guide only
"tag:best-practices"                            → best practices pages only
"tag:dsl dependencies"                          → DSL reference only (Groovy + Kotlin)
"tag:javadoc Project dependencies"              → Javadoc only
"tag:samples kotlin multiproject"               → sample projects only
"tag:release-notes"                             → release notes full text
```

Tags are standard Lucene field queries on the `tag` field. They can be combined with
any valid Lucene query syntax: `tag:userguide configuration cache -classpath`.

---

## Path Resolution

Paths are relative to the `converted/` root. The path itself encodes the section —
no additional parameter is needed for reading:

| User Guide page | `userguide/command_line_interface.md` |
| Groovy DSL type | `dsl/org.gradle.api.Project.md` |
| Kotlin DSL type | `kotlin-dsl/gradle/org.gradle/-project/index.md` |
| Javadoc class | `javadoc/org/gradle/api/Project.md` |
| Release notes | `release-notes.md` |
| Sample (description) | `samples/sample_building_java_applications/README.md` |
| Sample (build script, kotlin) | `samples/sample_building_java_applications/kotlin-dsl/app/build.gradle.kts` |
| Sample (build script, groovy) | `samples/sample_building_java_applications/groovy-dsl/app/build.gradle.kts` |

Paths returned by search results are always usable directly as the `path` argument.

### Content rendering

- **`.md` files**: returned as-is (already clean Markdown).
- **Non-HTML text files** (e.g., `README`, `*.gradle.kts`, `*.toml`): returned wrapped
  in a fenced code block with the appropriate language hint.
- **Binary files**: return an error.

---

## Response Format

### Read Page

Contents of the file: Markdown for `.md` files, fenced code block for text files.

### Search Results

```
Search results for '{query}' in Gradle {version}:

### {title}
Tag: {tag}   Path: `{path}`

{snippet} (Context-aware match snippet)
```

### Summary (no args)

```
Gradle {version} documentation sections:

- userguide: {N} pages — User Guide (configuration, tasks, plugins, CLI, ...)
- dsl:        {N} pages — Build Language Reference (Groovy DSL + Kotlin DSL API)
- javadoc:    {N} pages — Java API reference
- samples:    {N} samples — example projects with build scripts
- release-notes: 1 page

Use tag:X in your query to search within a section. Example: gradle_docs(query="tag:userguide configuration cache")
```

---

## Error Cases

| Condition                          | Response                                                |
|------------------------------------|---------------------------------------------------------|
| Version not found / download fails | Error message with download URL and advice              |
| Path not found                     | Error message with suggestion to search first           |
| Empty search results               | `"No results found for '{query}' in Gradle {version}."` |
| Path resolves to binary            | Error: binary content cannot be rendered                |

---

## Removed Arguments

- `releaseNotes: Boolean` — removed. Use `query="tag:release-notes"` or `path="release-notes.html"`.
- `kind: DocsKind` — removed. Use `tag:X` in the query string instead.

---

## Argument Descriptions (for MCP schema)

```kotlin
@Description("""
    Full-text search query. Supports standard Lucene syntax.
    Use tag:X to filter by section: tag:userguide, tag:dsl, tag:javadoc, tag:samples, tag:release-notes.
    Omit tag: to search all sections. Examples:
      "configuration cache"
      "tag:userguide configuration cache"
      "tag:dsl Project.dependencies"
      "tag:samples kotlin multiproject"
""")
val query: String? = null

@Description("""
    Path to a specific file, relative to the converted docs root. The section is encoded in the path prefix.
    Examples:
      'userguide/command_line_interface.md'
      'dsl/org.gradle.api.Project.md'
      'kotlin-dsl/gradle/org.gradle/-project/index.md'
      'javadoc/org/gradle/api/Project.md'
      'samples/sample_building_java_applications/kotlin-dsl/app/build.gradle.kts'
    Paths from search results can be used directly.
""")
val path: String? = null

@Description("Gradle version (e.g. '9.4'). Auto-detected from projectRoot if omitted.")
val version: String? = null

@Description("Absolute path to the Gradle project root, used for version auto-detection.")
val projectRoot: GradleProjectRootInput? = null
```

---

## Tool Description (for MCP schema)

```
Search and read official Gradle documentation (User Guide, DSL, Javadoc, samples, release notes).

━━━ SECTION FILTERING — USE tag: IN YOUR QUERY ━━━
To search within a specific section, include tag:X in the query:
  tag:userguide     — User Guide (tasks, plugins, configuration, CLI)
  tag:dsl           — Build Language Reference (Groovy DSL types + Kotlin DSL API, merged)
  tag:javadoc       — Java API (Javadoc)
  tag:samples       — Example projects (build scripts, READMEs)
  tag:release-notes — Release notes
Sub-tags (more specific, applied in addition to primary tag):
  tag:best-practices — Best practices guides (subset of userguide)
Omit tag: to search all sections at once.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modes (evaluated in order):
  path=...    Read a specific file. Path prefix is the section (e.g. userguide/foo.html).
  query=...   Full-text search. Include tag:X to filter by section.
  (no args)   Show a summary of available documentation sections.

Paths from search results can be passed directly to path=.
Paths encode the section — no separate kind parameter needed.

Examples:
  gradle_docs(query="configuration cache")
  gradle_docs(query="tag:userguide configuration cache")
  gradle_docs(query="tag:dsl dependencies")
  gradle_docs(query="tag:javadoc Project.dependencies")
  gradle_docs(query="tag:samples kotlin multiproject")
  gradle_docs(path="userguide/command_line_interface.md")
  gradle_docs(path="dsl/org.gradle.api.Project.md")
  gradle_docs(path="samples/sample_building_java_applications/kotlin-dsl/app/build.gradle.kts")
  gradle_docs(query="tag:release-notes", version="9.4")
```
