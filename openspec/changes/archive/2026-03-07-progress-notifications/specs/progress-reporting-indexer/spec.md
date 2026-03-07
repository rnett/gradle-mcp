## ADDED Requirements

### Requirement: Indexing progress reporting

The \`GradleDocsIndexService\` SHALL report progress while indexing converted documentation.

#### Scenario: Real-time indexing feedback

- **WHEN** the system is indexing Markdown documentation with Lucene
- **THEN** it SHALL emit progress notifications as each file or batch of files is processed

### Requirement: Extraction progress reporting

The \`ContentExtractorService\` SHALL report progress during documentation extraction and conversion.

#### Scenario: Extraction feedback

- **WHEN** documentation HTML is being extracted and converted to Markdown
- **THEN** progress updates SHALL be emitted based on the percentage of processed files in the ZIP archive
