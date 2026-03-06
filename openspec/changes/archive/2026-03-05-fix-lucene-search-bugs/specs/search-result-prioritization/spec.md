## ADDED Requirements

### Requirement: Prioritize Exact Case-Sensitive Matches

The full-text search engine SHALL prioritize exact, case-sensitive word matches over matches where the word is part of a larger, mixed-case identifier.

#### Scenario: Search for val LANGUAGE

- **WHEN** user searches for `LANGUAGE`
- **THEN** a file containing `val LANGUAGE = "..."` SHALL be ranked higher (and appear earlier in the results) than a file containing `configureCommonLanguageFeatures`, even if the latter contains multiple occurrences of "language" as a
  word part.

### Requirement: Multi-field Search for Boosting

The search engine SHOULD use multiple Lucene fields with different tokenization strategies (e.g., standard vs. non-splitting) to achieve the desired prioritization of exact matches.

#### Scenario: Search for camelCase

- **WHEN** user searches for `camelCase`
- **THEN** it SHALL match files containing the exact word `camelCase` even if they are not split into `camel` and `case` tokens, and these exact matches SHALL score higher than partial matches on the individual word parts.
