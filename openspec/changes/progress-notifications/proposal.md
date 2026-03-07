## Why

Currently, long-running operations like downloading Gradle distributions, extracting documentation, and indexing them provide no feedback to the user. This can lead to the perception that the tool has hung or is unresponsive. Implementing
progress notifications using the MCP protocol will improve the user experience by providing real-time feedback on these background tasks.

## What Changes

- **Progress Reporting in Downloads**: Add progress tracking to the `DistributionDownloaderService` using Ktor's `onDownload` feature.
- **Progress Reporting in Documentation Processing**: Track the progress of extracting and converting HTML documentation to Markdown.
- **Progress Reporting in Indexing**: Provide updates during the Lucene indexing process of the converted documentation.
- **McpContext Enhancement**: Ensure `McpContext` can easily propagate these progress updates to the MCP client via `ProgressNotification`.
- **Tool Level Integration**: Update relevant tools (e.g., `GradleDocsTools`) to bridge service-level progress updates to the `McpContext`.

## Capabilities

### New Capabilities

- `progress-reporting-downloader`: Adds progress notification support to the distribution downloader service.
- `progress-reporting-indexer`: Adds progress notification support to the documentation indexing and processing services.

### Modified Capabilities

- `mcp-context-progress`: Enhance McpContext to better handle and simplify progress reporting for nested service calls.

## Impact

- **Services**: `DistributionDownloaderService`, `ContentExtractorService`, `GradleDocsIndexService` will be modified to accept progress update lambdas.
- **Tools**: All tools utilizing these services will be updated to pass these lambdas from their `McpContext`.
- **API**: No breaking changes to the external MCP tool signatures are expected, as progress tokens are handled via MCP metadata.
