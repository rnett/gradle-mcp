## Why

Currently, tree-sitter integration in `gradle-mcp` is blocked by a critical issue: the `jtreesitter` library requires a separate `libtree-sitter` core native library, and the current `ParserDownloader.ensureCoreTsLibrary()` fails to
download it. The `kreuzberg-dev/tree-sitter-language-pack` C FFI library (`ts_pack_core_ffi`) bundles the core tree-sitter runtime and provides a high-level `ts_pack_process()` API that handles parsing, extraction, and language detection
internally — solving the blocker while simplifying the architecture.

## What Changes

- **Replace `jtreesitter` with C FFI**: Direct FFM calls to `ts_pack_core_ffi` instead of jtreesitter JNI bindings
- **Replace `ParserDownloader` with C FFI `DownloadManager`**: The built-in DownloadManager handles language parser downloads, SHA-256 verification, and caching
- **Replace `tags.scm` query extraction with `ts_pack_process()`**: Single FFI call per file handles parsing and extraction internally, eliminating need for `UpstreamQueryFetcher` and query compilation
- **Simplify `TreeSitterDeclarationExtractor`**: Remove `ExtractionContext` pooling, query caching, and manual AST traversal
- **Simplify `TreeSitterLanguageProvider`**: Remove jtreesitter `Language` wrapping and core library loading
- **Update build configuration**: Remove jtreesitter dependency, add FFM module access flags

## Capabilities

### New Capabilities

- `cffi-symbol-extraction`: Symbol extraction via `ts_pack_process()` with 305+ language support
- `cffi-download-manager`: Language parser downloads via C FFI's built-in DownloadManager

### Modified Capabilities

- `dependency-source-search`: Now uses C FFI extraction instead of jtreesitter queries
- `performant-symbol-search`: Single FFI call per file instead of parse → query → traverse pipeline

## Impact

- **`TreeSitterDeclarationExtractor`**: Major refactoring to use `ts_pack_process()` via FFM reflection bypass
- **`TreeSitterLanguageProvider`**: Updated to handle manual native library extraction/loading and use C FFI `DownloadManager` logic
- **`ParserDownloader`**: Deleted — replaced by C FFI logic in `TreeSitterLanguageProvider`
- **`UpstreamQueryFetcher`**: No longer needed — C FFI handles queries internally
- **`TsPackModels`**: New file — shared data classes for extraction results
- **Build config**: Remove jtreesitter, add `--enable-native-access=ALL-UNNAMED`
- **Testing**: Updated `TreeSitterDeclarationExtractorTest` with comprehensive Java/Kotlin verification
