## Context

The current `SymbolSearch` implementation scans source files using regex and stores results in a flat text file. Searching requires loading the entire index into memory and filtering with regex. For large projects like Gradle, this is
extremely slow and memory-intensive. Additionally, dependency extraction and indexing are currently sequential, further increasing the time required to prepare sources for search.

## Goals / Non-Goals

**Goals:**

- Migrate `SymbolSearch` to a Lucene-based index for high-performance, disk-backed searching.
- Implement symbol identification within a custom Lucene `TokenFilter` for single-pass indexing.
- Parallelize and stream extraction and indexing to minimize disk I/O.
- Support searching by Fully Qualified Name (FQN) and provide package-level navigation.
- Focus purely on identifying symbol declarations (simple name and FQN) without the overhead of tracking symbol "kinds."

**Non-Goals:**

- Full AST parsing of source files.
- Categorizing symbols by kind (e.g., distinguishing between a class and a method).
- **Parsing Groovy source files**: Groovy's highly dynamic syntax is difficult to parse accurately without the full compiler, and a robust pre-built Tree-sitter grammar is not available on Maven Central. We will intentionally drop symbol
  indexing support for `.groovy` files to maintain high performance and low dependency bloat.

## Decisions

### 1. Simplified Lucene-based Symbol Index

We will replace `symbols-v1.txt` with a compact Lucene index using **Payloads** for metadata.

- **Fields**:
    - `name`: Simple name of the symbol. Store: YES, Tokenized: YES (Keyword).
    - `fqn`: Fully qualified name. Store: YES, Tokenized: YES (Custom dot-splitting analyzer).
    - `package`: Package name. Store: YES, Tokenized: YES (Keyword).
    - `path`: Relative path. Store: YES, Tokenized: NO.
- **Metadata (Payloads)**: Store `line` (int) and `offset` (int) as byte arrays in the token payloads to allow surgical retrieval of the declaration site.

### 2. Tree-sitter based Symbol Extraction

We will use Tree-sitter (via the `io.github.bonede:tree-sitter` wrapper) to accurately parse and extract symbols and their FQNs from **Kotlin and Java** files.

- **Bundled Binaries**: The `bonede` library handles the native JNI binaries automatically, minimizing deployment complexity while delivering maximum performance.
- **Declarative Queries**: We will use declarative Tree-sitter queries (`.scm`) to identify declarations.
- **Error Tolerance**: Tree-sitter's error tolerance ensures that the indexer won't fail on new or unsupported syntax features.
- **FQN Assignment**: Queries will capture structural hierarchy to reliably build the FQN of each symbol.
- **Groovy Dropped**: As decided, we will not index `.groovy` files.

### 3. Streaming Parallel Indexer

Refactor `DefaultSourcesService.extractSources` to stream from `ZipInputStream` directly into `IndexWriter`.

- **Parallelism**: Use `coroutineScope` and `async` to process multiple ZIP entries concurrently.
- **I/O Efficiency**: Avoid extracting files to disk before indexing. Only symbol metadata is stored in Lucene.

### 4. Efficient Index Merging & Abstraction

Use `IndexWriter.addIndexes(Directory...)` (or equivalent `addDocument` loops for custom mappings) to merge individual dependency indices into a single project-level Lucene index.

- **`LuceneBaseSearchProvider`**: To keep the system DRY, we extract common Lucene index management (creating directories, configuring `IndexWriter`, managing `DirectoryReader` caches via Caffeine, and the index merging lifecycle) into an
  abstract base class. `FullTextSearch` and `SymbolSearch` extend this, significantly reducing boilerplate.

### 5. Streamlined Progress Reporting

Since extraction and indexing are highly parallelized, noisy per-file progress updates from the inner loops cause the UI to flicker erratically. We will suppress these inner updates during parallel execution and only report progress at the
dependency level (e.g., "Processing my:lib:1.0 (1/10)"), ensuring a clean user experience.

## Risks / Trade-offs

- **[Risk] Index Size** → Lucene indices are larger than flat files. **Mitigation**: Performance gains in search and indexing justify the storage.
- **[Risk] JAR Size** → Bundling Tree-sitter native binaries increases the distribution JAR size. **Mitigation**: The performance and accuracy gains (along with zero runtime configuration) outweigh the bump in JAR size.
- **[Trade-off] Dropping Groovy** → Users will not be able to search for symbols defined in Groovy source files (e.g., old Gradle plugins). **Mitigation**: This is an acceptable loss given the move towards Kotlin DSL and the massive
  complexity reduction in our parsing architecture.
