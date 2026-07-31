# Tasks: Gradle Skill Portfolio Redesign

## 1. Implement Portfolio Consolidation (Sources)

- [x] **Create `using-gradle`**: 
    - Move an operation-focused subset of `gradle` sources to `src/main/skills/using-gradle`.
    - Merge `exploring_dependency_sources` and the inspection-side of `managing_gradle_dependencies` into progressive-disclosure references.
    - Consolidate build execution, diagnosis, and internal research into separated, documented references.
- [x] **Create `authoring-gradle-builds`**:
    - Rename `gradle-build-authoring` to `src/main/skills/authoring-gradle-builds`.
    - Move the modification-focused subset of `gradle` sources (including `common_build_patterns.md`) into it.
    - Merge the declaration/wiring side of `managing_gradle_dependencies` into an `authored-local` reference.
- [x] **Rename Capability Skills**:
    - Rename `interacting_with_project_runtime` $\rightarrow$ `interacting-with-project-runtime`.
    - Rename `verifying_compose_ui` $\rightarrow$ `verifying-compose-ui`.
    - Compact bodies; extract shared setup if necessary to `authored-shared` sources.
- [x] **Cleanup**: Delete exactly the six retired source directories after link verification.

## 2. Refine Information Architecture

- [x] **Draft Index Bodies**: Write `SKILL.md` for the four skills as workflow indexes ($\le$ 150 lines).
- [x] **Implement Progressive Disclosure**: 
    - Move all detailed procedures into root-local references ($\le$ 220 lines).
    - Create generated `references/_index.md` for each skill.
- [x] **Establish Provenance**: Add deterministic `class` + `skill` headers to every reference.
- [x] **Apply Metadata**: Set names, semantic versions (`1.0.0`), author, and precise `Use when`/`Do NOT use` descriptions.

## 3. Implement Infrastructure & Automation

- [x] **Build Materialization**:
    - Implement `materializeSkills` for shared and generated resources.
    - Add `verifySkillsMaterialized` as a build gate for `check`.
- [x] **Update Documentation Splicing**:
    - Added START/END markers to `docs/skills.md` for future splicing.
    - Updated skill inventory content.
- [x] **Update Packaging and Installation**:
    - Updated install tests (`SkillToolsTest.kt`) to assert exact four-name inventory equality.
    - Verified `replaceOld=true` behavior against repository author markers.

## 4. Safety and Quality Gates

- [x] **Enforce `afterEvaluate` Prohibition**: 
    - Add the default prohibition to `authoring-gradle-builds/SKILL.md`.
    - [x] Add automated tests scanning skill artifacts for illegal `afterEvaluate` usage without the required justification block.
- [x] **Verify Indexing**: Run a deterministic check for dead links and orphan references within each skill root.
- [x] **Run Full Suite**: Execute `:updateToolsList`, `test`, and `check` to ensure no regressions in tool metadata or build logic.

## 5. Validation and Handoff

- [x] **OpenSpec Validation**: Run `openspec validate redesign-gradle-skills-portfolio --strict`.
- [x] **Behavioral Rubric**: Manually verify the Investigative and Modification loops against the new taxonomy.
- [x] **Documentation Sync**: Ensure `docs/skills.md` accurately reflects the finalized portfolio.
