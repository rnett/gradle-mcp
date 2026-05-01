## ADDED Requirements

### Requirement: C FFI Download Manager

The system SHALL use the C FFI's built-in `DownloadManager` to download and cache language parser bundles. The DownloadManager handles SHA-256 verification, platform detection, and caching internally.

#### Scenario: Downloading parsers for Rust

- **WHEN** the system needs to extract symbols from a `.rs` file
- **THEN** it SHALL call `ts_pack_download_manager_ensure_languages()` with `["rust"]`
- **AND** the DownloadManager SHALL download and cache the parser bundle if not already present
- **AND** subsequent calls SHALL use the cached parser without re-downloading

### Requirement: Native Library Download

The system SHALL download the `ts_pack_core_ffi` native library for the current platform on first use. The library is downloaded from the `kreuzberg-dev/tree-sitter-language-pack` GitHub releases.

#### Scenario: First-time initialization

- **WHEN** the system initializes for the first time
- **THEN** it SHALL download the platform-specific `ts_pack_core_ffi` library (`.dll`, `.so`, or `.dylib`)
- **AND** cache it at `~/.gradle-mcp/cache/tree-sitter-language-pack/v1.7.0/native/`
- **AND** load it via `SymbolLookup.libraryLookup()` for FFM access

### Requirement: Centralized Language Registry

The system SHALL maintain a registry of supported languages that maps file extensions to tree-sitter language names, derived from the versioned `language_definitions.json` fetched from the upstream repository.

#### Scenario: Registering a new language

- **WHEN** a new language definition is added to the registry (e.g., mapping `.go` to `go`)
- **THEN** the extraction and search systems SHALL automatically recognize and process files with that extension

### Requirement: Graceful Error Handling

The system SHALL handle cases where language download fails or an unsupported language is requested gracefully, without crashing.

#### Scenario: Network failure during download

- **WHEN** a language parser download fails due to network error
- **THEN** the system SHALL log an error and throw a descriptive exception
- **AND** not corrupt the cache

#### Scenario: Unsupported language

- **WHEN** a file extension is not registered in the language definitions
- **THEN** the system SHALL return an empty result and continue processing other files
