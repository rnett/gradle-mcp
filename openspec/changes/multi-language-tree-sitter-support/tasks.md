### 1. Research & Analysis

- [x] Investigate `tree-sitter-language-pack` C FFI library and platform support
- [x] Evaluate alternatives (jtreesitter fix, bundled natives, WASM, C FFI via FFM)
- [x] Document C FFI API surface (`ts_pack_process`, DownloadManager, error handling)
- [x] Analyze reference Java FFM binding implementation

### 2. FFM Bindings & Models

- [x] **Task 2.1**: Access native `MethodHandle`s via reflection in `dev.kreuzberg.treesitterlanguagepack.NativeLib`
  - [x] Bypass broken Java record serialization by passing raw JSON strings (Workaround for Issue #115)
  - [x] Implement error & memory handles: `ts_pack_last_error_code`, `_free_string`
  - [x] Implement ProcessConfig & Process handles: `_from_json`, `_process`, `_result_to_json`
- [x] **Task 2.2**: Define minimal models for pattern extractions in `TreeSitterDeclarationExtractor.kt`
  - [x] `TsPackExtraction`, `TsPackMatch`, `TsPackCapture` (for Kotlin name fallback)

### 3. Native Library & Language Management

- [x] **Task 3.1**: Implement manual native library extraction and loading in `TreeSitterLanguageProvider.kt`
  - [x] Locate DLL in Gradle cache if missing from JAR (Workaround for Issue #114)
  - [x] Load via `System.load()`
- [x] **Task 3.2**: Refactor `TreeSitterLanguageProvider.kt` to use C FFI `DownloadManager` logic via reflection
- [x] **Task 3.3**: Remove legacy `ParserDownloader.kt` JNI logic

### 4. Declaration Extractor Implementation

- [x] **Task 4.1**: Implement `TreeSitterDeclarationExtractor.kt` using `ts_pack_process()`
  - [x] Implement recursive tree walking (`collectSymbols`) for nested structure model
  - [x] Implement FQN construction by carrying context through recursion
  - [x] Implement Kotlin name fallback using pattern extractions matching byte offsets
  - [x] Implement package name detection with extractions fallback
- [x] **Task 4.2**: Clean up dependencies and old query infrastructure

### 5. Build Configuration

- [x] **Task 5.1**: Update `build.gradle.kts` with `tree-sitter-language-pack:1.8.0-rc.24`
- [x] **Task 5.2**: Add `--enable-native-access=ALL-UNNAMED` to JVM args for tests
- [x] **Task 5.3**: Remove `jtreesitter` and legacy native dependencies

### 6. Testing & Validation

- [x] **Task 6.1**: Implement comprehensive test suite in `TreeSitterDeclarationExtractorTest.kt`
  - [x] Verify Java symbol extraction and FQN construction
  - [x] Verify Kotlin symbol extraction with name fallback
  - [x] Verify package name detection for both languages
- [ ] **Task 6.2**: Confirm all tree-sitter tests pass (`./gradlew treeSitterTest` last reached 20 passed / 2 failed on 2026-04-30)

### 7. Pause / Resume Notes

- [x] **Task 7.1**: Document paused status after `tree-sitter-language-pack:1.8.0-rc.24` evaluation
- [ ] **Task 7.2**: Re-verify upstream Java artifact packaging before resuming; closed issues #114/#115 were not sufficient evidence that the Maven artifact is fixed
- [ ] **Task 7.3**: Confirm public Java API works on Windows without local `ts_pack_core_ffi.dll`, manual `cache_dir` JSON, or raw `NativeLib` MethodHandle calls
- [ ] **Task 7.4**: Resolve Kotlin member FQN attribution for declarations such as `com.example.MyKotlinClass.myVal`
- [ ] **Task 7.5**: Avoid JUnit cleanup of temp cache directories containing loaded Windows parser DLLs, or move parser caches out of auto-deleted temp roots
