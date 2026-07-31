<!--
class: authored-local
skill: using-gradle
-->
# Dependency & Plugin Source Research

Searches and reads source code for dependencies, plugins, and Gradle itself using `search_dependency_sources` and `read_dependency_sources`.

## Searching Dependency Sources

### Symbol Search (DECLARATION)

Find class, method, or interface definitions:

```json
{
  "projectPath": ":app",
  "query": "CoroutineScope",
  "searchType": "DECLARATION"
}
```

- **Unqualified queries** search both `name` and `fqn` fields.
- **Prefix syntax**: `name:X` for simple names, `fqn:x.y.Z` for precision.
- **FQN matching** is literal (preserves dots and case): `fqn:*.MyClass` for partial matches.
- **Regex**: Wrap in `/` for full regex on `fqn`: `query: "/.*\\.internal\\..*/"`.

### Full-Text Search

Case-insensitive exhaustive text search:

```json
{
  "projectPath": ":app",
  "query": "TIMEOUT_MS"
}
```

Escape special characters like `:`, `=`, `+`.

### File Search (GLOB)

Locate files by name or extension:

```json
{
  "projectPath": ":app",
  "query": "**/AndroidManifest.xml",
  "searchType": "GLOB"
}
```

### Scoped to a Single Dependency

```json
{
  "projectPath": ":app",
  "dependency": "^org\\\\.jetbrains\\\\.kotlinx:kotlinx-coroutines-core(:.*)?$",
  "query": "launch",
  "searchType": "DECLARATION"
}
```

Use a Kotlin regex over `group:name:version[:variant]`.

### JDK Sources

```json
{
  "sourceSetPath": ":app:main",
  "dependency": "jdk",
  "query": "String",
  "searchType": "DECLARATION"
}
```

JDK sources appear under `jdk/sources/...` when local `src.zip` exists.

### Plugin Sources

```json
{
  "sourceSetPath": ":buildscript",
  "query": "MyPlugin",
  "searchType": "DECLARATION"
}
```

Search buildscript (plugin) dependencies.

## Reading Dependency Sources

After finding a path via search, read the file:

```json
{
  "projectPath": ":app",
  "path": "org.jetbrains.kotlin/kotlin-stdlib/kotlin/collections/List.kt"
}
```

Use the `{group}/{artifact}/...` syntax for paths. Browse directories by omitting the file name.

### Reading a Package

```json
{
  "projectPath": ":app",
  "path": "org.jetbrains.kotlin/kotlin-stdlib/kotlin.collections"
}
```

## Gradle Build Tool Source

```json
{
  "gradleOwnSource": true,
  "query": "DefaultProject",
  "searchType": "DECLARATION"
}
```

Search Gradle's own source code for internal API research.

## Important Notes

- **Dependency directories are junctions/symlinks**: Standard CLI tools like `rg` or `fd` will NOT follow them. Always pass `--follow` or use the MCP tools.
- **`fresh: true`**: Use after dependency changes to re-index.
- **`forceDownload: true`**: Only for corrupt/missing files — expensive operation.
- **ALWAYS scope** with a project, configuration, or source set — unscoped access is no longer supported.

## Internal Source Research Workflow

Combine `gradle_docs` with `gradleSource: true` for authoritative Gradle internal research:

1. Look up the public API via `gradle_docs(query="tag:dsl <API>")`.
2. Find the implementation via `search_dependency_sources(gradleOwnSource=true, query="<ClassName>")`.
3. Read the full source via `read_dependency_sources(gradleOwnSource=true, path="...")`.
4. Verify version-specific behavior via `gradle_docs(query="tag:release-notes", version="X.Y")`.

## Troubleshooting

- **Source Not Found**: Some modules may not be fully indexed. Try broader `FULL_TEXT` search or browse directories.
- **Plugin Not Found**: Ensure the plugin is resolved in the buildscript classpath. Use `inspect_dependencies(sourceSetPath=":buildscript")`.
- **Index Error**: Use `fresh: true` after dependency changes.
