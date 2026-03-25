# Capability: search-error-feedback

## Purpose

Provides descriptive, actionable error messages when users encounter Lucene syntax errors in full-text search queries.

## Requirements

### Requirement: Descriptive Lucene Syntax Errors

The full-text search engine SHALL catch Lucene syntax errors and return a descriptive error message to the user instead of a raw stack trace or generic failure.

#### Scenario: Syntax Error with Colon

- **WHEN** user provides a query like `LANGUAGE:` (which is interpreted as an invalid field search)
- **THEN** system SHALL return an error message explaining that `:` is a special Lucene character and suggesting escaping (`\:`), using phrase queries (`"..."`), or relying on `GLOB` search for literal fragments.

#### Scenario: Syntax Error with Equals

- **WHEN** user provides a query like `LANGUAGE =` (which is invalid Lucene syntax)
- **THEN** system SHALL return an error message explaining that `=` is a special Lucene character and suggesting escaping (`\=`), using phrase queries (`"..."`), or relying on `GLOB` search for literal fragments.
