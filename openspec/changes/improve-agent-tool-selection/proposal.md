## Why

AI agents frequently misuse Gradle MCP tools — they reach for `searchDependencySources`/`readDependencySources` with `gradleSource: true` when they should use the `gradle` tool for build execution, and they over-specify `projectRoot` when
auto-detection would suffice. The parameter name `gradleSource` is itself ambiguous, leading agents to interpret it as a general "use Gradle" flag. This wastes time, confuses the agent, and produces suboptimal results. The root cause is
weak or ambiguous tool descriptions and parameter naming that don't clearly discriminate between tools.

## What Changes

- **`gradleSource` parameter rename**: Rename `gradleSource` to `gradleOwnSource` on `searchDependencySources` and `readDependencySources` to clearly communicate that this flag targets Gradle's own source code, not project dependencies or
  build execution.
- **`gradleOwnSource` parameter descriptions**: Strengthen descriptions on `searchDependencySources` and `readDependencySources` to clearly signal that `gradleOwnSource: true` is for reading Gradle's own source code only — NOT for running
  Gradle builds or tasks. Add negative trigger guidance ("Do NOT use for...").
- **`gradle` tool description**: Elevate the `gradle` tool's description to make it the unambiguous default for running any Gradle task or build. Add stronger authoritative language and clearer "when to use" scenarios that contrast with
  `gradleOwnSource` and shell alternatives.
- **`projectRoot` parameter descriptions**: Clarify when `projectRoot` is auto-detected vs. required. Simplify the description to reduce agent confusion and unnecessary specification.
- **Cross-reference improvements**: Add explicit cross-references between tools (e.g., `gradle` tool description mentions when to use `query_build` instead, `gradleSource` descriptions reference the `gradle` tool for execution).

## Capabilities

### New Capabilities

- `tool-description-tuning`: Systematic review and improvement of MCP tool descriptions to guide AI agents toward correct tool selection through authoritative language, clear positive/negative triggers, and cross-references.

### Modified Capabilities

- `skill-and-tool-descriptions`: Extend the existing spec to cover tool description patterns that specifically address agent tool-selection confusion — adding requirements for discriminative language between similar tools and explicit "when
  NOT to use" guidance.

## Impact

- **Source files**: `DependencySourceTools.kt` (`gradleSource` → `gradleOwnSource` rename + param descriptions), `GradleExecutionTools.kt` (`gradle` tool description), `GradleInputs.kt` (`projectRoot` description),
  `GradleBuildLookupTools.kt` (`query_build` description), `GradleDependencyTools.kt` (`inspect_dependencies` description)
- **Spec files**: `openspec/specs/skill-and-tool-descriptions/spec.md` (modified), `openspec/specs/tool-description-tuning/spec.md` (new)
- **API change**: Rename `gradleSource` boolean parameter to `gradleOwnSource` on `searchDependencySources` and `readDependencySources`. No backwards compatibility needed — only consumers are AI agents.
