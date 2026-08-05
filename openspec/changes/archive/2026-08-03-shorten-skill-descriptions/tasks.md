## 1. OpenSpec Artifacts

- [x] 1.1 Materialize the proposal, design, capability deltas, tasks, and change metadata.

## 2. Skill Metadata and Bodies

- [x] 2.1 Replace all five shipped frontmatter descriptions with the approved compact text and patch-bump metadata versions.
- [x] 2.2 Move Positive and Negative Triggers sections into each skill body after its overview, preserving routing meaning and removing exact duplicate wording.

## 3. Validation and Tests

- [x] 3.1 Parse shipped skill frontmatter and enforce non-empty, 400-character description and 1,536-character combined metadata budgets in `UpdateSkills.verify()`.
- [x] 3.2 Add unit coverage for exact boundaries, absent `when_to_use`, malformed or missing frontmatter, shipped inventory validation, manifest equality, and generated docs synchronization.

## 4. Documentation

- [x] 4.1 Regenerate `docs/skills.md` with `:updateSkillsList`.
- [x] 4.2 Add `advanced-gradle-dependencies` to `docs/index.md` in canonical order.

## 5. Verification

- [x] 5.1 Run `:test` and fix all change-related failures.
- [x] 5.2 Run `:verifySkillsList` and strict OpenSpec validation for this change.
