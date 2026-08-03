# Capability Deltas: gradle-skill-best-practices-integration

## ADDED Requirements

### Requirement: Authored doctrine precedence over frozen corpus examples
The skill MUST ensure that authored guidance and procedural recipes within the skills take absolute precedence over examples or patterns found in the frozen generated best-practice corpus. When a conflict exists between authored guidance (representing the modern ground-truth doctrine) and the frozen corpus, the authored guidance SHALL be treated as the authoritative source, and the frozen corpus entry SHALL be treated as optional historical rationale only.

#### Scenario: Conflict between authored and frozen guidance
- **WHEN** an agent finds a pattern in the frozen `best-practices/` corpus that contradicts a rule in an authored reference or skill body
- **THEN** it applies the authored rule and ignores the frozen example
- **AND** it optionally cites the authored rule as the reason for deviating from the corpus example
