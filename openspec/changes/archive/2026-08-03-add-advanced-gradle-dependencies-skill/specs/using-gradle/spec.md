# Capability Deltas: using-gradle

## ADDED Requirements

### Requirement: Advanced Dependency Depth Handoff
The skill MUST retain everyday dependency basics — graph audits, `dependencyInsight` winner analysis, the force/exclude/platform/constraint menu, cache TTL versus `--refresh-dependencies`, conditional verification cautions, and update discovery — and MUST route advanced dependency work to `advanced-gradle-dependencies` through a `## Cross-Skill Handoffs` row and a frontmatter negative trigger. Advanced dependency work comprises variant-aware resolution diagnostics, dependency verification metadata authoring, component metadata and selection rules, dependency substitution, composite builds, advanced version catalog topics, and dependency governance.

#### Scenario: Hand off a variant selection failure
- **WHEN** an agent hits a variant selection failure, an attribute mismatch, or a no-matching-variant resolution error
- **THEN** `using-gradle` routes it to the advanced dependency engineering handoff (`advanced-gradle-dependencies`) instead of attempting a fix within the everyday operational basics

#### Scenario: Keep everyday dependency basics
- **WHEN** an agent performs everyday dependency inspection (graph audits, winner analysis, version bumps, update discovery)
- **THEN** it stays in `using-gradle` and its existing dependency basics without activating the advanced skill
