<!--
class: authored-local
skill: using-gradle
-->
# Gradle Internals & Documentation Research

Researches official Gradle documentation, release notes, internal APIs, and Gradle Build Tool source code.

## Official Documentation via `gradle_docs`

Use `gradle_docs` for authoritative documentation. Always scope with tags:

| Tag | Section |
|-----|---------|
| `tag:userguide` | Official Gradle User Guide |
| `tag:dsl` | Gradle DSL Reference (Groovy and Kotlin DSL) |
| `tag:javadoc` | Gradle Java API Reference |
| `tag:samples` | Official Gradle samples and examples |
| `tag:release-notes` | Version-specific release insights |
| `tag:best-practices` | Official best practices and performance guidelines |

### Exploring Sections

```json
{
  "query": "tag:userguide",
  "path": "."
}
```

Navigate the documentation tree.

### Scoped Search

```json
{
  "query": "tag:userguide dependency management"
}
```

Search within a specific documentation section.

### Version-Specific Research

```json
{
  "query": "tag:release-notes",
  "version": "8.6"
}
```

Check breaking changes and new features for a specific version.

### DSL Reference

```json
{
  "query": "tag:dsl",
  "path": "dsl/org.gradle.api.Project.html"
}
```

Read specific DSL pages directly.

## Gradle Build Tool Source Research

Use `gradleSource: true` with `search_dependency_sources` and `read_dependency_sources` to explore Gradle's own implementation.

### Searching Gradle Source

```json
{
  "gradleOwnSource": true,
  "query": "DefaultProject",
  "searchType": "DECLARATION"
}
```

Find class definitions, method signatures, and implementation details within Gradle's source.

### Reading Gradle Source Files

```json
{
  "gradleOwnSource": true,
  "path": "org.gradle.api.internal.project/DefaultProject.kt"
}
```

Read specific source files from the Gradle Build Tool.

### Full-Text Search in Gradle Source

```json
{
  "gradleOwnSource": true,
  "query": "configuration cache serialization"
}
```

Search for concepts, error messages, or patterns across Gradle's codebase.

## Internal API Research Workflow

1. **Start with docs**: Use `gradle_docs(query="tag:dsl <API>")` to understand the public contract.
2. **Verify with source**: Use `search_dependency_sources(gradleOwnSource=true, query="<ClassName>")` to find the implementation.
3. **Read the source**: Use `read_dependency_sources(gradleOwnSource=true, path="...")` for full context.
4. **Check release notes**: Use `gradle_docs(query="tag:release-notes", version="X.Y")` for version-specific behavior.

## Troubleshooting

- **Page not found**: Use `path="."` to explore available sections first.
- **No results for a version**: The `version` parameter must match an available Gradle version. Omit for the project's detected version.
- **Source not indexed**: Some internal modules may not be fully indexed. Try broader search terms.
