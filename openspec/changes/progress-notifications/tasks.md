## 1. McpContext Enhancement

- [x] 1.1 Update \`McpContext\` to provide a method that returns a progress-reporting lambda that applies phase-based prefixes.
- [x] 1.2 Update \`McpContext.emitProgressNotification\` to handle progress tokens more reliably.

## 2. DistributionDownloaderService

- [x] 2.1 Modify \`DistributionDownloaderService\` to accept an optional progress lambda: \`(progress: Double, total: Double?, message: String?) -> Unit\`.
- [x] 2.2 Implement progress tracking using Ktor's \`onDownload\` in \`DefaultDistributionDownloaderService\`.

## 3. ContentExtractorService

- [x] 3.1 Modify \`ContentExtractorService\` to accept an optional progress lambda.
- [x] 3.2 Implement progress reporting based on the number of processed ZIP entries in \`DefaultContentExtractorService\`.

## 4. GradleDocsIndexService

- [x] 4.1 Modify \`GradleDocsIndexService\` to accept an optional progress lambda.
- [x] 4.2 Implement progress reporting for Lucene indexing in \`DefaultGradleDocsIndexService\`.

## 5. Tool Integration

- [x] 5.1 Update \`GradleDocsTools\` and other relevant tools to pass progress lambdas with appropriate phase prefixes (\`[DOWNLOADING]\`, \`[EXTRACTING]\`, \`[INDEXING]\`).
- [x] 5.2 Verify that progress notifications are correctly received by an MCP client with phase-based resets and prefixes.
