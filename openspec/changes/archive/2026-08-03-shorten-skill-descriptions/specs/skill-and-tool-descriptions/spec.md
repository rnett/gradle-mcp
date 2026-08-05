## MODIFIED Requirements

### Requirement: Authoritative Skill Descriptions

Each shipped `SKILL.md` MUST provide a non-empty, authoritative, persuasive top-level frontmatter `description` that clearly states why the skill is the preferred way to interact with Gradle for its specific domain. Descriptions MUST start with a third-person gerund (e.g., "Manages...", "Retrieves...", "Analyzes...") as required by the `building-mcp-servers` and `creating-skills` expert guidelines. All skill names MUST be in gerund form (e.g., `managing_gradle_builds`). Descriptions MUST be one or two sentences and state: (1) what the skill does with semantic anchors, (2) when the skill should be activated, and (3) the primary negative routing boundary when the skill would otherwise be ambiguous with another shipped skill. Each parsed description MUST contain no more than 400 characters and MUST NOT repeat detailed workflows, directives, examples, or trigger inventories from the body. Detailed Positive Triggers and Negative Triggers sections MUST appear in the `SKILL.md` body, not in the frontmatter description. If both `description` and `when_to_use` exist, their parsed character counts combined MUST NOT exceed 1,536 characters.

#### Scenario: Agent reads SKILL.md

- **WHEN** an agent reads a `SKILL.md` file
- **THEN** it encounters a 1-2 sentence description that starts with a third-person gerund, uses strong authoritative language (e.g., "authoritatively manage," "STRONGLY PREFERRED"), and includes positive/negative trigger phrases

#### Scenario: Compact discovery metadata

- **WHEN** an agent discovers a shipped skill before loading its body
- **THEN** the parsed frontmatter description contains no more than 400 characters
- **AND** it states the primary negative routing boundary needed to choose among shipped skills

#### Scenario: Optional when-to-use metadata

- **WHEN** a shipped skill defines both `description` and `when_to_use`
- **THEN** their parsed character counts combined do not exceed 1,536 characters

#### Scenario: Detailed trigger guidance

- **WHEN** a shipped skill needs examples or a complete trigger inventory
- **THEN** Positive Triggers and Negative Triggers sections appear in the body
- **AND** frontmatter contains no trigger headings or detailed trigger bullets

## ADDED Requirements

### Requirement: Skill description verification

Skill documentation verification MUST reject shipped metadata whose parsed frontmatter description is missing, blank, longer than 400 characters, or longer than 1,536 characters when combined with optional parsed `when_to_use`. Verification failures MUST identify the skill, measured length, and applicable limit. Malformed or unparseable frontmatter MUST fail verification rather than being skipped.

#### Scenario: Description exceeds budget

- **WHEN** a shipped skill has a parsed description longer than 400 characters
- **THEN** `verifySkillsList` fails with the skill name, measured character count, and 400-character limit

#### Scenario: Combined metadata exceeds limit

- **WHEN** a shipped skill's parsed `description` and `when_to_use` exceed 1,536 characters combined
- **THEN** `verifySkillsList` fails with the skill name, measured combined character count, and 1,536-character limit
