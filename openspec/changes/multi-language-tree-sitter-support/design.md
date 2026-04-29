## Context

Currently, `TreeSitterDeclarationExtractor` and `DeclarationSearch` have hardcoded support for Java and Kotlin. The tree-sitter queries and indexing logic are tightly coupled to these two languages. While functional, this prevents the MCP server from providing symbol-level intelligence for the 60+ other languages supported by our `tree-sitter-language-pack`.

## Goals / Non-Goals

**Goals:**
- Generalize `TreeSitterDeclarationExtractor` to be language-agnostic.
- Use community-standard `tags.scm` files (e.g., from `nvim-treesitter`) for symbol extraction.
- Leverage the existing `ParserDownloader` to fetch any supported language parser from the language pack releases.
- Implement a generic "Tag-Aware" extractor that targets standard capture names (`@definition.*`, `@name`).
- Maintain existing performance and safety (FFM API arenas) while reducing code coupling.

**Non-Goals:**
- Using the `tree-sitter-language-pack` library directly (current releases are broken).
- Manually writing custom queries for every language.

## Decisions

### Decision 1: Version-Locked Manifest Discovery
We will use the [**`sources/language_definitions.json`**](https://github.com/kreuzberg-dev/tree-sitter-language-pack/blob/main/sources/language_definitions.json) file from the `tree-sitter-language-pack` repository.
- **Strict Versioning**: The manifest MUST be fetched using the same version tag as the native binaries (e.g., `https://raw.githubusercontent.com/kreuzberg-dev/tree-sitter-language-pack/v1.7.0/sources/language_definitions.json`).
- **Rationale**: This ensures that the metadata (extensions, repo revisions) perfectly matches the compiled parsers in use.

### Decision 2: Automated Query URL Construction
`tags.scm` files will be fetched directly from upstream repositories using metadata from the manifest.
- **URL Pattern**: `https://raw.githubusercontent.com/<repo_user>/<repo_name>/<rev>/[directory/]queries/tags.scm`
- **Path Logic**: If the manifest entry contains a `directory` field, it MUST be prepended to `/queries/tags.scm`.
- **Integrity**: By using the exact `rev` (git hash) from the manifest, we avoid breakage if the upstream repository's default branch evolves independently.
- **Caching**: Queries will be cached locally in `.mcps/rnett-gradle-mcp/cache/queries/<language>/<rev>/tags.scm`.

### Decision 3: Generic Tag-Based Extractor
The extractor will be refactored to be entirely data-driven, targeting standard tree-sitter captures.
- **Capture Mapping**: Map `@definition.*` (e.g., `.class`, `.function`, `.interface`) to symbol types and `@name` to the symbol identifier.
- **Verified Languages**: Patterns confirmed for Java, Kotlin, Python, Rust, Go, Scala, and TypeScript.
- **Fallback**: Languages missing `tags.scm` or using non-standard naming will require a local override or a contribution to the upstream pack metadata.

### Decision 4: Heuristic FQN Builder
A recursive AST visitor will build Fully Qualified Names (FQNs) by:
1. Identifying the definition node and its `@name` identifier.
2. Climbing the tree to the root.
3. For each ancestor node:
    - **Container Check**: If the ancestor node contains a child that was *also* captured as a `@definition.*` by the query, that ancestor is treated as a scope container.
    - **Name Extraction**: Extract the `@name` of that ancestor and prepend it to the current FQN parts.
4. Using a language-appropriate separator (defaulting to `.`, or `::` if configured).

### Decision 5: Hybrid Package Detection
Since `package.scm` is not standardized across all languages:
1. **Local Override**: Provide a small set of project-internal `package.scm` files for high-priority languages (Java, Kotlin, Python, Go).
2. **Upstream Attempt**: If no local override exists, attempt to fetch `package.scm` from the same upstream location as `tags.scm`.
3. **Graceful Fallback**: If both fail, use an empty string for the package/module name.

### Decision 6: Migrate to Official Java Bindings (`jtreesitter`)
Due to Tree-sitter ABI version mismatches (the pre-compiled language pack binaries use ABI 15, but `ktreesitter:0.24.1` only supports up to ABI 14), we will migrate from the community `ktreesitter-jvm` library to the official `io.github.tree-sitter:jtreesitter:0.25.0`.
- **JDK 25 Support**: Since the project is using JDK 25, we can leverage the Foreign Function & Memory (FFM) API used by `jtreesitter`. (Note: We use `0.25.0` instead of `0.26.0` to avoid a known `ClassCastException` related to Windows FFM memory layouts).
- **Core Library Loading**: `jtreesitter` does not bundle the core native C library (`tree-sitter.dll`/`libtree-sitter.so`). We will dynamically download the pre-compiled core library from the official `tree-sitter/tree-sitter` GitHub releases and inject it into the JVM `SymbolLookup` chain so `jtreesitter` can link against it.
- **Code Adaptation**: The existing Kotlin integration (`TreeSitterDeclarationExtractor.kt` and `TreeSitterLanguageProvider.kt`) must be rewritten to interface with the new FFM-based Java API instead of the old JNI-based `ktreesitter` API.

## Risks / Trade-offs

- **[Risk]** Query complexity across languages → **[Mitigation]** Start with proven queries from tree-sitter community (e.g., tags.scm patterns) and iterate.
- **[Risk]** Performance impact of many languages → **[Mitigation]** Lazy loading of native libraries and queries is already supported by `TreeSitterLanguageProvider`.
- **[Risk]** Breaking FQN logic for Java/Kotlin → **[Mitigation]** Rigorous regression testing using existing `DeclarationSearchTest`.
- **[Risk]** API differences between `ktreesitter` and `jtreesitter` → **[Mitigation]** Carefully map the `ktreesitter` JNI abstractions (especially Queries and Cursors) to the new FFM Java API in `jtreesitter`.
