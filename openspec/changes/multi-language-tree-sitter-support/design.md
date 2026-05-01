## Context

Currently, `TreeSitterDeclarationExtractor` uses `jtreesitter` (JNI-based tree-sitter bindings) with custom `tags.scm` query files fetched via `UpstreamQueryFetcher`. This approach has a critical blocker: the `jtreesitter` library requires
a separate `libtree-sitter` core native library, and the current `ParserDownloader.ensureCoreTsLibrary()` fails to download it. The language-pack parsers statically link the core library, making a separate core library either unnecessary or
conflicting.

The project already uses JDK 25, which enables the Foreign Function & Memory (FFM) API. The `kreuzberg-dev/tree-sitter-language-pack` project provides pre-built C FFI libraries (`ts_pack_core_ffi`) for Windows x64, Linux x64, Linux aarch64,
and macOS arm64. These libraries bundle the core tree-sitter runtime and expose a high-level `ts_pack_process()` API that handles parsing, extraction, and language detection internally.

## Goals / Non-Goals

**Goals:**

- Replace `jtreesitter` with direct FFM calls to the `ts_pack_core_ffi` C FFI library
- Replace custom `ParserDownloader` with the C FFI's built-in `DownloadManager`
- Use `ts_pack_process()` for symbol extraction instead of manual `tags.scm` query compilation and cursor traversal
- Maintain the same `ExtractedSymbol` data class contract for downstream consumers
- Download native libraries at runtime (not bundled in JAR)

**Non-Goals:**

- Bundling native libraries in the JAR (user requirement: download at runtime)
- Supporting macOS x86_64 (not available in C FFI v1.7.0)
- Maintaining `jtreesitter` or `tags.scm` query infrastructure
- Changes to non-tree-sitter functionality

## Decisions

### Decision 1: Use `ts_pack_process()` for Symbol Extraction

The C FFI offers two extraction paths:

- **`ts_pack_process(source, config)`**: High-level function that parses + extracts in one call. Returns structured `ProcessResult` with `StructureItem[]` (kind, name, span, children), `SymbolInfo[]`, and `FileMetrics`. Structure items use
  a nested tree model (children are `Vec<StructureItem>`, not indices).
- **Lower-level node queries**: Returns a `Tree*` handle, then use node traversal functions.

**Chosen**: `ts_pack_process()` because:

- The C FFI does NOT expose raw tree-sitter `TSNode` to Java — all node traversal is Rust-side
- There is no FFI equivalent to jtreesitter's `Query`/`QueryCursor`
- `ts_pack_process()` returns structured data with nested children, enabling FQN construction
- Eliminates need for `tags.scm`/`package.scm` query files and `UpstreamQueryFetcher`
- Single FFI call per file instead of multiple (parse → query → traverse)

### Decision 2: Use C FFI `DownloadManager` for Parser Downloads

The C FFI's built-in `DownloadManager` handles:

- Language parser bundle downloads from GitHub releases
- SHA-256 verification (built into Rust implementation)
- Caching and platform detection
- The core `ts_pack_core_ffi` native library is downloaded separately via HTTP (chicken-and-egg: DownloadManager functions are IN the native library)

**Chosen**: Replace ~300 lines of custom `ParserDownloader` code with the C FFI DownloadManager.

### Decision 3: FFM Binding Pattern (Modified for v1.8.0-rc.22)

The `tree-sitter-language-pack` Java bindings are present but contain critical serialization bugs (Issue #115). Specifically, `ProcessConfig` and `ProcessResult` use `String` fields where the native side expects/returns JSON Maps.

**Actual Implementation:**

- Bypass high-level `TreeSitterLanguagePack` API.
- Use reflection to access `MethodHandle`s in `dev.kreuzberg.treesitterlanguagepack.NativeLib`.
- Manually construct JSON input strings for `ts_pack_process_config_from_json`, ensuring `extractions` is a JSON Map.
- Manually deserialize the JSON string from `ts_pack_process_result_to_json` using Jackson, bypassing the broken `ProcessResult` record.
- Load the native library manually from the Gradle cache via `System.load()` because the library's JAR is missing the `natives/` directory (Issue #114).

### Decision 4: JSON-Centric Processing

Due to the broken Java accessors, the implementation uses JSON as the primary exchange format:

- **Input**: Handcrafted JSON for config.
- **Output**: Full result JSON deserialized into a `Map<String, Any?>` for flexibility.

### Decision 5: FQN Construction from Nested Structure

The `StructureItem` from the C FFI uses a **nested tree model** (each item has a `children: List<StructureItem>` field), not a flat list with indices as previously expected.

**Actual Implementation:**

1. Recursive traversal (`collectSymbols`) of the nested structure tree.
2. FQN construction by passing the current parent FQN down the recursion.
3. Handle missing class names (primarily in Kotlin) by matching spans against custom `class_names` extractions.
4. Package name detection from structure with a fallback to `packages` extractions.

## Risks / Trade-offs

- **[Risk]** Extraction fidelity: `ts_pack_process` may extract different symbols than `tags.scm` queries.
- **[Risk]** Upstream Instability: The v1.8.0-rc.22 release required significant FFM/FFI workarounds.
- **[Trade-off]** Maintainability vs Upstream Sync: By bypassing the broken high-level API, we've gained stability at the cost of using internal MethodHandles via reflection. This should be reverted once Issue #115 is fixed.

## Paused Status (2026-04-30)

This change is paused and the implementation should be treated as experimental/stashed, not complete.

### Last Known State

- The dependency was tested with `dev.kreuzberg:tree-sitter-language-pack:1.8.0-rc.24`.
- `./gradlew treeSitterTestClasses` compiled successfully after the version bump.
- `./gradlew treeSitterTest` reached 20 passing tests and 2 failing tests before pausing.
- The two remaining failures were:
    - `DeclarationSearchTest.test integration`: Kotlin member FQN lookup failed for `com.example.MyKotlinClass.myVal`; the extractor emitted/searchable symbol data for `myVal`, but not under the class-qualified FQN.
    - `TreeSitterDeclarationExtractorTest.executionError`: JUnit failed to delete a temporary cache directory on Windows because loaded parser DLLs (`tree_sitter_java.dll`, `tree_sitter_kotlin.dll`) remained locked.

### Upstream Findings for `1.8.0-rc.24`

- Issue #114 appeared closed upstream, but the resolved Maven artifact still did not contain native resources. `jar tf tree-sitter-language-pack-1.8.0-rc.24.jar | Select-String "^natives/|ts_pack_core_ffi|tree_sitter_"` returned no entries.
- `NativeLib` in the `1.8.0-rc.24` sources has resource-extraction logic, but because the JAR lacks `natives/`, it falls back to `System.loadLibrary("ts_pack_core_ffi")`. Local successes may therefore depend on an untracked/local DLL or
  `java.library.path`, not the published artifact alone.
- Issue #115 also appeared closed upstream, but `javap` against the resolved `1.8.0-rc.24` JAR still showed `ProcessConfig.extractions(): Optional<String>` and `ProcessResult.extractions(): String`.
- The public `TreeSitterLanguagePack.process(...)` path failed on Windows with `IO error: The filename, directory name, or volume label syntax is incorrect. (os error 123)` when tested through the project suite.
- The raw JSON FFI path (`ts_pack_process_config_from_json` -> `ts_pack_process` -> `ts_pack_process_result_to_json`) remained the only path that processed files reliably in this environment.

### Recommended Restart Strategy

1. First verify a newer upstream artifact, not just issue status. Specifically check the resolved JAR for `natives/<rid>/ts_pack_core_ffi.*` entries and inspect the Java records for extraction-compatible types.
2. Avoid reintroducing broad custom downloader logic unless the published artifact still cannot load natively as a normal Maven dependency.
3. Prefer the public Java API only after a focused smoke test confirms `TreeSitterLanguagePack.configure(...)`, `download(...)`, and `process(...)` work on Windows without local DLLs or manual JSON marshalling.
4. If custom extraction fallback is still required, isolate it behind a small adapter and document exactly which languages/symbol kinds upstream `structure`/`symbols` omit.
5. Fix Kotlin member FQN attribution from containing structure spans before re-enabling the full declaration-search tests.
