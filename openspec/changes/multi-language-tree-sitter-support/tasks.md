### 1. Research & Analysis
- [x] Investigate `tree-sitter-language-pack` manifest structure and repository mappings.
- [x] Document standard capture names for Python, Go, Rust, TypeScript, Kotlin, and Scala.
- [x] Audit `tags.scm` availability for Groovy and determine fallback strategy.

### 2. Infrastructure Expansion
- [x] **Task 2.1**: Update `ParserDownloader.kt` to fetch `language_definitions.json` using the version-specific git tag (e.g., `v1.7.0`).
- [x] **Task 2.2**: Implement `UpstreamQueryFetcher` to download and cache `tags.scm` files.
    - [x] Handle URL construction with `directory` field support.
    - [x] Implement local disk caching with revision-based purging.
- [x] **Task 2.3**: Extend `TreeSitterLanguageProvider.kt` to expose extensions and FQN metadata from the versioned manifest.

### 3. Core Refactoring (jtreesitter Migration)
- [ ] **Task 3.1**: Fix ABI mismatch by migrating the JVM binding layer from `ktreesitter:0.24.1` to the official `io.github.tree-sitter:jtreesitter:0.25.0` (using JDK 25 FFM API).
    - [ ] Update `libs.versions.toml` to `jtreesitter:0.25.0`.
    - [ ] Update `ParserDownloader.kt` to dynamically download the native `tree-sitter` core library (`tree-sitter.dll` etc.) from official GitHub releases.
    - [ ] Rewrite `TreeSitterLanguageProvider.kt` to inject the downloaded core library into the FFM `SymbolLookup` chain and load native parsers.
    - [ ] Generalize `TreeSitterDeclarationExtractor.kt` to use the data-driven capture processor (`@definition.*`, `@name`) using the new `jtreesitter` Query API.
- [ ] **Task 3.2**: Implement the heuristic AST scope builder for FQN generation using `jtreesitter` nodes.
    - [ ] Implement upward traversal from definition nodes.
    - [ ] Implement "Container Check" based on definition captures in ancestors.
- [ ] **Task 3.3**: Update `DeclarationSearch.kt` to dynamically index files based on the manifest's extension mappings.
- [ ] **Task 3.4**: Implement hybrid package/module detection (Local overrides + Upstream fallback).

### 4. Validation & Testing
- [ ] **Task 4.1**: Create `MultiLanguageIndexingTest` covering symbol extraction for at least 4 languages (e.g., Python, Rust, Go, Scala).
- [ ] **Task 4.2**: Verify Java/Kotlin regression by running `DeclarationSearchTest`.
- [ ] **Task 4.3**: Benchmark start-up performance and memory usage with 10+ languages active.
