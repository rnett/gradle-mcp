## Context

Long-running documentation tasks (downloading, extracting, indexing) currently provide no user feedback, which can be frustrating. The Model Context Protocol (MCP) supports progress notifications, which can be used to provide real-time
updates to the client.

## Goals / Non-Goals

**Goals:**

- Provide progress updates for downloading Gradle distributions/documentation.
- Provide progress updates for extracting and converting documentation.
- Provide progress updates for indexing documentation.
- Maintain a clean, reusable API for progress reporting in `McpContext`.
- Adopt the "multi-stage" progress pattern used in Gradle builds (phase-based 0-100% with prefixes).

**Non-Goals:**

- Tracking progress for very short operations (e.g., small local file reads).
- Implementing progress for every single background task (focus on the most impactful ones).

## Decisions

### 1. Decoupled Progress Reporting

To maintain strict separation of concerns, the core services (Downloader, Extractor, Indexer) MUST NOT have any dependency on the MCP layer or `McpContext`. They will instead accept an optional "slim update lambda".

```kotlin
typealias ProgressUpdate = (progress: Double, total: Double?, message: String?) -> Unit
```

The tool layer (`McpContext` or Tool implementations) will be responsible for mapping these generic updates to MCP `ProgressNotification`s and applying phase-based prefixes.

### 2. Multi-Stage Progress Pattern

Following the pattern established for Gradle builds:

- Each major operation (Download, Extract, Index) will be treated as a "Phase".
- Each phase will report progress on a 0-100% scale.
- The progress bar will reset to 0% at the start of each phase.
- The status message will be prefixed with the current phase: `[DOWNLOADING]`, `[EXTRACTING]`, or `[INDEXING]`.

### 3. Progress Tracking Implementation

- **Download Phase**: Use Ktor's `onDownload` feature to track bytes. Total is the content length.
- **Extraction Phase**: Total items = number of entries in the ZIP. Progress is `(processedEntries + currentEntryProgress) / totalEntries`.
- **Indexing Phase**: Total items = number of Markdown files to index. Progress is `(processedFiles + 1) / totalFiles`.

### 4. Use of progressToken

We will strictly follow the MCP spec, only emitting `ProgressNotification` if a `progressToken` was provided in the initial request metadata.

## Risks / Trade-offs

- **[Risk]** Excessive notifications could impact performance. → **Mitigation**: Throttling progress updates (e.g., only every 1% or every 500ms).
- **[Risk]** Resetting the progress bar might confuse some clients. → **Mitigation**: The phase-prefix in the status message clarifies the context of the reset.
