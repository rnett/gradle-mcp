## Why

The current symbol indexing and search implementation for project dependencies is not performant for large codebases like the Gradle build tool sources. It uses regex scanning into flat text files and reads the entire index into memory for
every search, leading to high latency and memory overhead. Improving this performance is critical for maintaining a responsive experience when exploring large dependency graphs.

## What Changes

- **Lucene-based Symbol Indexing**: Migrate `SymbolSearch` to use Lucene for indexing and searching symbols. This will replace the current line-by-line regex scanning and flat file storage with a more robust, disk-indexed solution.
- **Optimized Archive Extraction**: Improve the performance of extracting and processing dependency sources before they are indexed.
- **FQN and Package Search Support**: Introduce capabilities to search for symbols using their fully qualified names (FQN) and to browse/search packages as if they were directories.

## Capabilities

### New Capabilities

- `performant-symbol-search`: Provides high-performance, disk-indexed symbol searching using Lucene.
- `fqn-symbol-search`: Enables searching for symbols by their fully qualified names and provides package-level navigation.

### Modified Capabilities

(None)

## Impact

- **Services**: `SymbolSearch`, `SourcesService`, `GradleSourceService` will be modified to use the new indexing engine.
- **Storage**: Symbol indices will now be stored in Lucene format instead of flat `.txt` files.
- **Performance**: Search latency for symbols is expected to drop from seconds to milliseconds for large indices.
