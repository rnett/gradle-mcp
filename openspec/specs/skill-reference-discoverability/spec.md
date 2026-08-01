# Capability: skill-reference-discoverability

## Purpose

Defines the cross-skill contract that every shipped skill reference is discoverable from its `SKILL.md` body, and retires generated per-skill root reference indexes.

## Requirements

### Requirement: Reference discoverability from the SKILL.md body

Every Markdown file under a skill's `references/` directory SHALL be reachable from that skill's `SKILL.md` by following relative Markdown links woven throughout the body, including inline workflow steps, Constitution directives, before-you-modify checklist items, troubleshooting notes, or, only for references with no natural prose home, an authored routing table. The build's reference-reachability verification (`checkReferenceReachability`, run by `verifySkillsMaterialized` and `check`) SHALL enforce this with no dead relative links and no orphaned references.

#### Scenario: Woven pointer is discoverable

- **WHEN** a reference is linked from a `SKILL.md` prose section or an authored in-body table
- **THEN** the reachability check reports it reachable and the agent can construct its path from the body

#### Scenario: Orphaned reference fails

- **WHEN** a reference under `references/` is not linked from `SKILL.md` or any reachable reference
- **THEN** verification fails with an "Orphaned reference" violation

### Requirement: Generated per-skill root reference index retired

Reference discovery SHALL be woven into the `SKILL.md` body and SHALL NOT be produced as a generated per-skill root `references/_index.md` with `generator: skill-index`. The generated best-practices corpus index (`references/best-practices/_index.md`, `generator: best-practices`) is explicitly out of scope of this retirement and remains generated and body-linked.

#### Scenario: Regenerated root index is a violation

- **WHEN** a root `references/_index.md` exists for any canonical skill
- **THEN** the no-generated-root-reference-index regression test fails
