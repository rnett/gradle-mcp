## Context

Currently, we have skills for build execution and test execution, as well as dependency management and project introspection. However, we lack a skill specifically focused on the *creation* and *maintenance* of Gradle build logic itself.

## Goals / Non-Goals

**Goals:**

- Create a `gradle_expert` skill that provides expert guidance on Gradle build authoring and internal research.
- Include best practices for Kotlin DSL, performance, and multi-project structures.
- Integrate existing tools like `gradle_docs`, `read_dependency_sources`, and `search_dependency_sources` into the skill workflow.

**Non-Goals:**

- Implement new Gradle features.
- Provide general Kotlin programming advice (unless directly related to Gradle Kotlin DSL).

## Decisions

1. **Skill Location**: Create `skills/gradle_expert/`.
    - *Rationale*: Follows the project convention for skill organization.
2. **Instruction Set**: Focus on `SKILL.md` for declarative instructions and `references/` for deep-dive documentation.
    - *Rationale*: This structure is already used in other skills (e.g., `running_gradle_builds`).
3. **Integration with Gradle Search Tools**: The skill should provide clear workflows for:
    - Official documentation lookup (User Guide, DSL) using `gradle_docs`.
    - **Retrieving official samples and Javadocs** using `gradle_docs` with `tag:samples` and `tag:javadoc`.
    - Internal engine research using `search_dependency_sources` and `read_dependency_sources` with `gradleSource = true`.
    - Dependency source exploration for third-party plugins.
    - *Rationale*: This leverages the full power of the available MCP tools for deep technical understanding across all information sources.

## Risks / Trade-offs

- [Risk] → Skill instructions might become too verbose.
- [Mitigation] → Use progressive disclosure and move deep-dive details into `references/`.
- [Risk] → Recommendations might conflict with existing project custom logic.
- [Mitigation] → Instructions should emphasize observing existing project patterns first.
