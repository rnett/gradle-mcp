## Context

The `FullTextSearch` object in `gradle-mcp` uses Lucene for full-text searching across project dependency sources. It currently uses a standard analyzer with `WordDelimiterGraphFilter` that splits on case changes and lowercases all tokens.
This strategy is good for general "fuzzy" matches but fails on literal code fragments containing Lucene special characters (`:`, `=`) and doesn't prioritize exact, case-sensitive word matches correctly.

## Goals / Non-Goals

**Goals:**

- Provide clear, descriptive error messages for invalid Lucene syntax to the tool caller (the LLM).
- Improve the scoring logic to ensure that an exact, case-sensitive word match (e.g., `LANGUAGE`) is ranked higher than multiple occurrences of the word as a part of a larger identifier (e.g., `configureCommonLanguageFeatures`).
- Maintain the existing capability for "fuzzy" matches (splitting camelCase, etc.) while adding the exact match prioritization.

**Non-Goals:**

- Do not automatically fallback to an "escaped" query on syntax errors; instead, return an error message so the caller can choose the correct correction strategy.
- Do not change the underlying Lucene index version (v3) unless strictly necessary for the prioritization fix.
- Do not modify the `GLOB` or `SYMBOLS` search types, which are already robust for their specific use cases.

## Decisions

### 1. Catch QueryNodeException for Descriptive Error Messages

- **Rationale**: Currently, `StandardQueryParser.parse()` throws a `QueryNodeException` on invalid syntax, which might propagate as a generic failure in the tool response.
- **Implementation**: Wrap the `parse` call in a try-catch block and return a human-readable string explaining common Lucene special characters and how to escape them.
- **Alternatives**: Automatical escaping (rejected to avoid masking intentional Lucene syntax and potentially confusing the caller).

### 2. Multi-Field Indexing for Prioritization

- **Rationale**: A single tokenized field cannot easily distinguish between a "whole word" and a "word part" after tokenization. By indexing the same content into two fields with different analyzers, we can boost matches on the "more exact"
  field.
- **Implementation**:
    - `CONTENTS`: Existing field with `StandardTokenizer` + `WordDelimiterGraphFilter` (splits camelCase, lowercases).
    - `CONTENTS_EXACT`: New field with `StandardTokenizer` + `LowerCaseFilter` (no word delimiter splitting).
- **Rationale for Boosting**: During search, we will search both fields (using a `MultiFieldQueryParser` or manually combining queries) and give a significantly higher weight to matches in `CONTENTS_EXACT`.
- **Alternatives**: Changing the main analyzer to not split (rejected as it would break the "fuzzy" search capabilities that are useful for exploration).

### 3. Documentation Updates for LLM Self-Correction

- **Rationale**: The LLM needs to know that `searchType="FULL_TEXT"` uses Lucene syntax and requires escaping for literal matches with special characters.
- **Implementation**: Update `DependencySourceTools.kt` tool description and `SKILL.md` (researching_gradle_internals) with explicit instructions on Lucene syntax and common special characters.

### 4. Query Interpretation Feedback

- **Rationale**: To help LLMs verify their search intent for "syntactically valid but potentially mistaken queries," showing how Lucene interpreted the query is invaluable.
- **Implementation**: After parsing the query into a Lucene `Query` object, call `query.toString()` and include this string at the beginning of the search result output. This allows the LLM to see if, for example, a word was split or if a
  character was treated as a special operator.

## Risks / Trade-offs

- **[Risk] Index Size Increase** → [Mitigation] Adding `CONTENTS_EXACT` will double the size of the contents part of the index. This is acceptable given the relatively small size of source files in dependency graphs and the significant
  usability gain.
- **[Risk] Slower Indexing** → [Mitigation] Indexing twice the content with a simpler second analyzer will only marginally increase the indexing time, which is already a background/cached operation.
- **[Risk] Over-complicated Error Messages** → [Mitigation] Keep the error message concise and focused on the most common problematic characters (`:`, `=`, `+`, `-`, `*`, `/`).
