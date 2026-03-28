## ADDED Requirements

### Requirement: Suite-Based Test Grouping in Summary

The build summary output SHALL group test results by their `suiteName` for improved readability when many tests fail or are in progress.

#### Scenario: Display grouped test failures in summary

- **WHEN** multiple tests have failed across different suites
- **THEN** the summary SHALL list the suite names as headings.
- **AND** it SHALL indent individual test names under their respective suites.
