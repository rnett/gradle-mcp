## Context

Current Gradle MCP tools and skills have descriptions that are mostly functional and descriptive but lack persuasive power and clear guidance on *why* they should be preferred over raw shell commands. This leads to agents occasionally
defaulting to `./gradlew` which is less reliable and lacks the advanced features (backgrounding, output capturing, structured failure analysis) of the MCP tools. Descriptions should be much longer and more comprehensive to provide agents
with the full context they need to make optimal tool choices.

## Goals / Non-Goals

**Goals:**

- Rewrite frontmatter descriptions for all skills to be benefit-oriented, authoritative, and more detailed.
- Significantly expand tool descriptions in Kotlin code to highlight unique MCP features and usage patterns.
- Incorporate "When to Use" scenarios directly into the primary descriptions where appropriate, and expand the "When to Use" sections in `SKILL.md` files with specific, high-signal scenarios.
- Maintain a high level of functional utility, ensuring technical details are preserved (even if moved to later sections).
- Improve parameter descriptions in tool arguments to guide the agent toward efficient usage (e.g., pagination).

**Non-Goals:**

- Change the functional implementation of any tools.
- Add new tools or parameters.
- Reorganize the skill directory structure.

## Decisions

### 1. Adopt an "Expert Authority" Tone

We will use strong, persuasive adjectives like "Managed", "High-Performance", "Surgical", "Precision", and "Authoritative". This signals to the LLM that these tools are the professionally recommended way to interact with Gradle.

### 2. Prioritize "Why" and "Comprehensive Context" Without Sacrificing "How"

We will explain the benefit and provide a thorough background, but we will ensure that the functional instructions remain clear. For tool descriptions, we will use a structured approach: a benefit-oriented summary followed by detailed
functional documentation.

### 3. Integrate "When to Use" into Primary Descriptions

The "When to Use" logic will be woven into the high-level descriptions for skills, making it immediately clear to the agent under which conditions the skill is the superior choice.

### 4. Balanced "Why over What"

The "why" will be grounded in technical capabilities. We will avoid "fluff" and ensure that the persuasive elements directly relate to the tool's actual power and efficiency (e.g., "Surgical Diagnostics" instead of just "Better Debugging").

### 5. Improve Argument-Level Guidance

We will update the `@Description` annotations on data classes to include hints about efficient usage, such as reminding the agent to use `limit` and `offset` for large results.

## Risks / Trade-offs

- **[Risk]** Overly long descriptions might increase context usage.
- **[Mitigation]** While descriptions will be longer, we will prioritize high-signal information and structured formatting (bullets, bold text) to maintain clarity and efficiency. The extra detail is intended to *reduce* unnecessary tool
  calls by guiding the agent to the right tool the first time.
